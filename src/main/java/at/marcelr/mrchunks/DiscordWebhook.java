package at.marcelr.mrchunks;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Sends log events to a Discord webhook. Requests run one after another on a background thread,
 * so the main thread never waits and Discord's rate limit (HTTP 429) can be respected.
 * <p>
 * On/off and colour of each event come from config.yml, the texts from the language file ({@code discord.<event>}).
 */
public final class DiscordWebhook {

    private static final int MAX_RETRIES = 3;

    private final MRChunks plugin;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "MRchunks-Discord");
        thread.setDaemon(true);
        return thread;
    });

    private volatile Config config = Config.DISABLED;
    /** Language key of the reason why the webhook is off, or null if it is on. */
    private volatile String disabledReason = "discord-reason-disabled";
    /** Last delivery result, rendered when shown so it appears in the reader's language. */
    private volatile Supplier<String> lastResult;
    private final Set<String> missingEventsLogged = ConcurrentHashMap.newKeySet();

    private record Event(boolean enabled, int color) {
    }

    private record Config(boolean enabled, URI url, String username, String avatarUrl,
                          boolean embeds, String footer, Map<String, Event> events) {
        static final Config DISABLED = new Config(false, null, "", "", false, "", Map.of());
    }

    public DiscordWebhook(MRChunks plugin) {
        this.plugin = plugin;
    }

    public void load(ConfigurationSection section) {
        if (section == null || !section.getBoolean("enabled", false)) {
            disable("discord-reason-disabled", false);
            return;
        }
        String rawUrl = section.getString("webhook-url", "").trim();
        if (rawUrl.isEmpty()) {
            disable("discord-reason-no-url", true);
            return;
        }
        if (!rawUrl.startsWith("https://discord.com/api/webhooks/")
                && !rawUrl.startsWith("https://discordapp.com/api/webhooks/")
                && !rawUrl.startsWith("https://canary.discord.com/api/webhooks/")
                && !rawUrl.startsWith("https://ptb.discord.com/api/webhooks/")) {
            disable("discord-reason-bad-url", true);
            return;
        }
        URI url;
        try {
            url = URI.create(rawUrl);
        } catch (IllegalArgumentException e) {
            disable("discord-reason-invalid-url", true);
            return;
        }

        // Events from the file, with the bundled defaults filling in entries that are missing there.
        Map<String, Event> events = new HashMap<>();
        ConfigurationSection fileEvents = section.contains("events", true) ? section.getConfigurationSection("events") : null;
        Configuration defaults = section.getRoot() == null ? null : section.getRoot().getDefaults();
        ConfigurationSection defaultEvents = defaults == null ? null : defaults.getConfigurationSection("discord.events");
        Set<String> keys = new LinkedHashSet<>();
        if (fileEvents != null) {
            keys.addAll(fileEvents.getKeys(false));
        }
        if (defaultEvents != null) {
            keys.addAll(defaultEvents.getKeys(false));
        }
        for (String key : keys) {
            ConfigurationSection e = fileEvents != null && fileEvents.contains(key, true)
                    ? fileEvents.getConfigurationSection(key)
                    : defaultEvents == null ? null : defaultEvents.getConfigurationSection(key);
            if (e != null) {
                events.put(key, new Event(e.getBoolean("enabled", true), parseColor(e.getString("color", "#5865F2"))));
            }
        }
        config = new Config(true, url,
                section.getString("username", "MRchunks"),
                section.getString("avatar-url", ""),
                section.getBoolean("use-embeds", true),
                section.getString("footer", ""),
                Map.copyOf(events));
        disabledReason = null;
        int[] counts = eventCounts();
        plugin.getLogger().info("Discord webhook active (" + counts[0] + "/" + counts[1] + " events enabled).");
    }

    private void disable(String reasonKey, boolean warn) {
        config = Config.DISABLED;
        disabledReason = reasonKey;
        if (warn) {
            plugin.getLogger().warning("Discord webhook disabled: " + plugin.messages().console(reasonKey));
        }
    }

    // ------------------------------------------------------------ diagnosis

    public boolean enabled() {
        return config.enabled();
    }

    /** Why the webhook is off (in the configured language), or null if it is on. */
    public String disabledReason() {
        String key = disabledReason;
        return key == null ? null : plugin.messages().plain(key);
    }

    /** @return [enabled events, all events] */
    public int[] eventCounts() {
        Map<String, Event> events = config.events();
        int active = (int) events.entrySet().stream().filter(e -> active(e.getKey(), e.getValue())).count();
        return new int[]{active, events.size()};
    }

    /** An event is sent if it is enabled in config.yml and has a text in the language file. */
    private boolean active(String key, Event event) {
        return event.enabled() && !plugin.messages().optional("discord." + key).isEmpty();
    }

    /** Result of the last delivery attempt, e.g. "OK (HTTP 204) at 14:31:02", or null if nothing was sent yet. */
    public String lastResult() {
        Supplier<String> result = lastResult;
        return result == null ? null : result.get();
    }

    /**
     * Sends the test message ({@code discord.test-message}) and waits for Discord's answer ({@code ?wait=true}).
     * @return future with null on success, or the error description
     */
    public CompletableFuture<String> test(CommandSender sender) {
        Config current = config;
        if (!current.enabled()) {
            return CompletableFuture.completedFuture(plugin.messages().as(sender, this::disabledReason));
        }
        String text = plugin.messages().server(() -> plugin.messages().optional("discord.test-message"));
        String payload = buildPayload(current, 0x2ECC71, text.isEmpty() ? "MRchunks" : text);
        URI waitUrl = URI.create(current.url() + (current.url().getQuery() == null ? "?" : "&") + "wait=true");
        // The error text goes back to the sender, so build it in their language (English for the console).
        return CompletableFuture.supplyAsync(() -> plugin.messages().as(sender, () -> send(waitUrl, payload)), executor);
    }

    /**
     * Sends the text of an event. {@code placeholders} alternates key and value,
     * e.g. {@code log("night-skipped", "world", "world", "sleeping", "2")} for {@code {world}} and {@code {sleeping}}.
     */
    public void log(String eventKey, Object... placeholders) {
        Config current = config;
        if (!current.enabled()) {
            return;
        }
        Event event = current.events().get(eventKey);
        if (event == null) {
            // Should not happen thanks to the defaults - but if it does, say so once instead of failing silently.
            if (missingEventsLogged.add(eventKey)) {
                plugin.getLogger().warning("Discord event '" + eventKey + "' is missing in discord.events - not sent.");
            }
            return;
        }
        String text = plugin.messages().server(() -> plugin.messages().optional("discord." + eventKey));
        if (!event.enabled() || text.isEmpty()) {
            return;
        }
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            text = text.replace("{" + placeholders[i] + "}", escapeMarkdown(String.valueOf(placeholders[i + 1])));
        }
        String payload = buildPayload(current, event.color(), text);
        executor.execute(() -> send(current.url(), payload));
    }

    private static String buildPayload(Config config, int color, String text) {
        JsonObject json = new JsonObject();
        if (!config.username().isEmpty()) {
            json.addProperty("username", config.username());
        }
        if (!config.avatarUrl().isEmpty()) {
            json.addProperty("avatar_url", config.avatarUrl());
        }
        // Never ping anyone, even if a message contains @everyone or a mention.
        JsonObject mentions = new JsonObject();
        mentions.add("parse", new JsonArray());
        json.add("allowed_mentions", mentions);

        if (config.embeds()) {
            JsonObject embed = new JsonObject();
            embed.addProperty("description", text);
            embed.addProperty("color", color);
            embed.addProperty("timestamp", Instant.now().toString());
            if (!config.footer().isEmpty()) {
                JsonObject footer = new JsonObject();
                footer.addProperty("text", config.footer());
                embed.add("footer", footer);
            }
            JsonArray embeds = new JsonArray();
            embeds.add(embed);
            json.add("embeds", embeds);
        } else {
            json.addProperty("content", text);
        }
        return json.toString();
    }

    /** @return null on success, otherwise a description of the error (also logged and kept as {@link #lastResult}) */
    private String send(URI url, String payload) {
        HttpRequest request = HttpRequest.newBuilder(url)
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("User-Agent", "MRchunks/" + plugin.getPluginMeta().getVersion())
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();
        try {
            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status / 100 == 2) {
                    String time = now();
                    lastResult = () -> plugin.messages().plain("discord-result-ok", "status", status, "time", time);
                    return null;
                }
                if (status != 429) {
                    return fail(explainKey(status), "HTTP " + status + " - ", " " + abbreviate(response.body()));
                }
                Thread.sleep(retryAfterMillis(response));
            }
            return fail("discord-error-rate-limit", "", "");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return fail("discord-error-interrupted", "", "");
        } catch (Exception e) {
            return fail(null, e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage()), "");
        }
    }

    /**
     * Records and logs a failed delivery: the admin sees the text in the configured language, the console in English.
     * @param key language key of the explanation, or null if there is none
     * @return the error in the configured language
     */
    private String fail(String key, String before, String after) {
        Messages messages = plugin.messages();
        String error = before + (key == null ? "" : messages.plain(key)) + after;
        String time = now();
        lastResult = () -> plugin.messages().plain("discord-result-error", "time", time,
                "error", before + (key == null ? "" : plugin.messages().plain(key)) + after);
        plugin.getLogger().log(Level.WARNING, "Discord webhook: " + before + (key == null ? "" : messages.console(key)) + after);
        return error;
    }

    private static String explainKey(int status) {
        return switch (status) {
            case 400 -> "discord-error-400";
            case 401, 403 -> "discord-error-401";
            case 404 -> "discord-error-404";
            default -> null;
        };
    }

    private static String abbreviate(String body) {
        String clean = body == null ? "" : body.replaceAll("\\s+", " ").trim();
        return clean.length() > 300 ? clean.substring(0, 300) + "…" : clean;
    }

    private static String now() {
        return LocalTime.now().truncatedTo(ChronoUnit.SECONDS).toString();
    }

    private static long retryAfterMillis(HttpResponse<String> response) {
        try {
            double seconds = JsonParser.parseString(response.body()).getAsJsonObject().get("retry_after").getAsDouble();
            return Math.max(250L, (long) (seconds * 1000));
        } catch (RuntimeException e) {
            return 2_000L;
        }
    }

    private static int parseColor(String raw) {
        try {
            return Integer.parseInt(raw.replace("#", "").trim(), 16);
        } catch (NumberFormatException e) {
            return 0x5865F2;
        }
    }

    private static String escapeMarkdown(String value) {
        return value.replaceAll("([_*~`|>\\\\])", "\\\\$1");
    }

    public void shutdown() {
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
