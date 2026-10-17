package at.marcelr.mrchunks;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import java.util.logging.Level;

/**
 * Sends log events to a Discord webhook. Requests run one after another on a background thread,
 * so the main thread never waits and Discord's rate limit (HTTP 429) can be respected.
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
    private volatile String disabledReason = "discord.enabled ist false";
    private volatile String lastResult;
    private final Set<String> missingEventsLogged = ConcurrentHashMap.newKeySet();

    private record Event(boolean enabled, String message, int color) {
    }

    private record Config(boolean enabled, URI url, String username, String avatarUrl,
                          boolean embeds, String footer, String testMessage, Map<String, Event> events) {
        static final Config DISABLED = new Config(false, null, "", "", false, "", "", Map.of());
    }

    public DiscordWebhook(MRChunks plugin) {
        this.plugin = plugin;
    }

    public void load(ConfigurationSection section) {
        if (section == null || !section.getBoolean("enabled", false)) {
            disable("discord.enabled ist false", false);
            return;
        }
        String rawUrl = section.getString("webhook-url", "").trim();
        if (rawUrl.isEmpty()) {
            disable("discord.webhook-url ist leer", true);
            return;
        }
        if (!rawUrl.startsWith("https://discord.com/api/webhooks/")
                && !rawUrl.startsWith("https://discordapp.com/api/webhooks/")
                && !rawUrl.startsWith("https://canary.discord.com/api/webhooks/")
                && !rawUrl.startsWith("https://ptb.discord.com/api/webhooks/")) {
            disable("discord.webhook-url ist keine Discord-Webhook-URL (muss mit https://discord.com/api/webhooks/ beginnen)", true);
            return;
        }
        URI url;
        try {
            url = URI.create(rawUrl);
        } catch (IllegalArgumentException e) {
            disable("discord.webhook-url enthält ungültige Zeichen (Leerzeichen? Anführungszeichen?)", true);
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
                events.put(key, new Event(
                        e.getBoolean("enabled", true),
                        e.getString("message", ""),
                        parseColor(e.getString("color", "#5865F2"))));
            }
        }
        config = new Config(true, url,
                section.getString("username", "MRchunks"),
                section.getString("avatar-url", ""),
                section.getBoolean("use-embeds", true),
                section.getString("footer", ""),
                section.getString("test-message", "MRchunks: Testnachricht"),
                Map.copyOf(events));
        disabledReason = null;
        long active = events.values().stream().filter(e -> e.enabled() && !e.message().isEmpty()).count();
        plugin.getLogger().info("Discord-Webhook aktiv (" + active + "/" + events.size() + " Events eingeschaltet).");
    }

    private void disable(String reason, boolean warn) {
        config = Config.DISABLED;
        disabledReason = reason;
        if (warn) {
            plugin.getLogger().warning("Discord-Webhook deaktiviert: " + reason);
        }
    }

    // ------------------------------------------------------------ diagnosis

    public boolean enabled() {
        return config.enabled();
    }

    /** Why the webhook is off, or null if it is on. */
    public String disabledReason() {
        return disabledReason;
    }

    /** @return [enabled events, all events] */
    public int[] eventCounts() {
        Map<String, Event> events = config.events();
        int active = (int) events.values().stream().filter(e -> e.enabled() && !e.message().isEmpty()).count();
        return new int[]{active, events.size()};
    }

    /** Result of the last delivery attempt, e.g. "OK (HTTP 204) um 14:31:02", or null if nothing was sent yet. */
    public String lastResult() {
        return lastResult;
    }

    /**
     * Sends the configured test message and waits for Discord's answer ({@code ?wait=true}).
     * @return future with null on success, or the error description
     */
    public CompletableFuture<String> test() {
        Config current = config;
        if (!current.enabled()) {
            return CompletableFuture.completedFuture(disabledReason == null ? "deaktiviert" : disabledReason);
        }
        Event event = new Event(true, current.testMessage(), 0x2ECC71);
        String payload = buildPayload(current, event, current.testMessage());
        URI waitUrl = URI.create(current.url() + (current.url().getQuery() == null ? "?" : "&") + "wait=true");
        return CompletableFuture.supplyAsync(() -> send(waitUrl, payload), executor);
    }

    /**
     * Sends the configured message of an event. {@code placeholders} alternates key and value,
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
                plugin.getLogger().warning("Discord-Event '" + eventKey + "' fehlt in discord.events - wird nicht gesendet.");
            }
            return;
        }
        if (!event.enabled() || event.message().isEmpty()) {
            return;
        }
        String text = event.message();
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            text = text.replace("{" + placeholders[i] + "}", escapeMarkdown(String.valueOf(placeholders[i + 1])));
        }
        String payload = buildPayload(current, event, text);
        executor.execute(() -> send(current.url(), payload));
    }

    private static String buildPayload(Config config, Event event, String text) {
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
            embed.addProperty("color", event.color());
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
        String error;
        try {
            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status / 100 == 2) {
                    lastResult = "OK (HTTP " + status + ") um " + now();
                    return null;
                }
                if (status != 429) {
                    return fail("HTTP " + status + " - " + explain(status) + " " + abbreviate(response.body()));
                }
                Thread.sleep(retryAfterMillis(response));
            }
            error = "Rate-Limit von Discord, Nachricht verworfen";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            error = "unterbrochen";
        } catch (Exception e) {
            error = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
        }
        return fail(error);
    }

    private String fail(String error) {
        lastResult = "FEHLER um " + now() + ": " + error;
        plugin.getLogger().log(Level.WARNING, "Discord-Webhook: " + error);
        return error;
    }

    private static String explain(int status) {
        return switch (status) {
            case 400 -> "Discord lehnt die Nachricht ab (z.B. ungültiger Name/Avatar oder zu langer Text).";
            case 401, 403 -> "Webhook-Token ungültig.";
            case 404 -> "Webhook existiert nicht (gelöscht? URL falsch kopiert?).";
            default -> "";
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
