package at.marcelr.mrchunks;

import at.marcelr.mrchunks.util.Versions;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import static at.marcelr.mrchunks.Messages.tag;

/**
 * Looks up the newest published version on Modrinth or GitHub Releases and reports outdated installs
 * in the console and to admins. All HTTP requests run off the main thread.
 */
public final class UpdateChecker {

    public static final String PERM_NOTIFY = "mrchunks.admin.update";

    private static final long NOTICE_INTERVAL_MILLIS = 60_000;

    public enum State { DISABLED, CHECKING, UP_TO_DATE, OUTDATED, FAILED }

    private final MRChunks plugin;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final String currentVersion;
    private final String modrinthProject;
    private final String githubRepository;
    /** Pages on the website (empty in builds without website info). */
    private final String downloadPage;
    private final String docsPage;

    private volatile State state = State.DISABLED;
    private volatile String latestVersion;
    private volatile String downloadUrl;
    private volatile String error;
    private volatile String errorKey;
    private final String minecraft;
    private String announcedVersion;
    private BukkitTask task;
    /** When each player last got the update notice. */
    private final Map<UUID, Long> noticed = new ConcurrentHashMap<>();

    public UpdateChecker(MRChunks plugin) {
        this.plugin = plugin;
        Properties info = new Properties();
        try (InputStream in = plugin.getResource("build-info.properties")) {
            if (in != null) {
                info.load(in);
            }
        } catch (IOException ignored) {
            // fall back to defaults below
        }
        this.currentVersion = plugin.getPluginMeta().getVersion();
        String mc = info.getProperty("mcVersion", "");
        this.minecraft = mc.isBlank() || mc.contains("$") ? Versions.minecraft(currentVersion) : mc;
        this.modrinthProject = info.getProperty("modrinthProjectId", "mrchunks");
        this.githubRepository = info.getProperty("githubRepository", "");
        String website = info.getProperty("website", "");
        String slug = info.getProperty("websiteSlug", modrinthProject);
        boolean hasWebsite = !website.isBlank() && !website.contains("$");
        this.downloadPage = hasWebsite ? website + "/download/" + slug + "/" : "";
        this.docsPage = hasWebsite ? website + "/docs/" + slug + "/" : "";
    }

    public void start() {
        stop();
        Settings.UpdateSettings settings = plugin.settings().update();
        if (!settings.enabled()) {
            state = State.DISABLED;
            return;
        }
        long period = settings.intervalHours() * 72_000L;
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::check, 100L, period);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /** Checks right away, reports the result to {@code sender} and then runs {@code after} (on the main thread). */
    public void checkNow(CommandSender sender, Runnable after) {
        plugin.messages().send(sender, "version-checking");
        CompletableFuture.runAsync(this::check).whenComplete((ignored, throwable) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sendStatus(sender);
                    after.run();
                }));
    }

    public void sendStatus(CommandSender sender) {
        // Runs after the command has finished (async result), so set the console language here again.
        plugin.messages().as(sender, () -> {
            sendStatusNow(sender);
            return null;
        });
    }

    private void sendStatusNow(CommandSender sender) {
        Messages messages = plugin.messages();
        switch (state) {
            case UP_TO_DATE -> messages.send(sender, "version-latest");
            case OUTDATED -> messages.send(sender, "version-outdated",
                    tag("latest", latestVersion()), tag("current", currentVersion()), tag("download", downloadLink()));
            case DISABLED -> messages.send(sender, "version-unknown", tag("reason", messages.plain("update-reason-disabled")));
            case CHECKING -> messages.send(sender, "version-checking");
            case FAILED -> messages.send(sender, "version-unknown", tag("reason", errorText()));
        }
    }

    /** Join notice for admins. */
    public void notifyIfOutdated(Player player) {
        if (state == State.OUTDATED && plugin.settings().update().notifyOnJoin() && player.hasPermission(PERM_NOTIFY)
                && firstNoticeFor(player)) {
            plugin.messages().run(player, () -> plugin.messages().send(player, "update-join-notify",
                    tag("latest", latestVersion()), tag("current", currentVersion()), tag("download", downloadLink())));
        }
    }

    /**
     * Whether the player did not get the notice within the last minute. Joining and the first check right after a
     * restart both send it - without this, players who join quickly see it twice.
     */
    private boolean firstNoticeFor(Player player) {
        long now = System.currentTimeMillis();
        Long last = noticed.put(player.getUniqueId(), now);
        return last == null || now - last > NOTICE_INTERVAL_MILLIS;
    }

    /** The last error in the configured language. */
    private String errorText() {
        if (errorKey != null) {
            return plugin.messages().plain(errorKey);
        }
        return error == null ? "?" : error;
    }

    public Component downloadLink() {
        String url = !downloadPage.isEmpty() ? downloadPage : downloadUrl == null ? modrinthPage() : downloadUrl;
        return plugin.messages().get("update-download-label").clickEvent(ClickEvent.openUrl(url));
    }

    public String modrinthPage() {
        return "https://modrinth.com/plugin/" + modrinthProject;
    }

    public String githubPage() {
        return "https://github.com/" + githubRepository;
    }

    /** Guide on the website (falls back to the GitHub page). */
    public String websitePage() {
        return docsPage.isEmpty() ? githubPage() : docsPage;
    }

    public State state() {
        return state;
    }

    /** Newest plugin version for this Minecraft version, without prefix (e.g. "1.2.0"), or null if unknown. */
    public String latestVersion() {
        return latestVersion == null ? null : Versions.plugin(latestVersion);
    }

    /** Installed plugin version without prefix, e.g. "1.2.0". */
    public String currentVersion() {
        return Versions.plugin(currentVersion);
    }

    /** Minecraft version this build is made for, e.g. "26.3". */
    public String minecraftVersion() {
        return minecraft;
    }

    // ------------------------------------------------------------------ check

    private synchronized void check() {
        Settings.UpdateSettings settings = plugin.settings().update();
        if (!settings.enabled()) {
            state = State.DISABLED;
            return;
        }
        if (state == State.DISABLED) {
            state = State.CHECKING;
        }
        // Whoever runs a pre-release (0.x, -rc...) must also be told about newer pre-releases,
        // otherwise 0.9.2 would never learn about 0.9.3.
        boolean prereleases = settings.includePrereleases() || Versions.isPrerelease(currentVersion);
        try {
            Release release = settings.source().equals("github")
                    ? fetchGithub(prereleases)
                    : fetchModrinth(prereleases);
            if (release == null) {
                fail("update-reason-no-release", null);
                return;
            }
            latestVersion = release.version();
            downloadUrl = release.url();
            error = null;
            errorKey = null;
            if (Versions.compare(release.version(), currentVersion) > 0) {
                state = State.OUTDATED;
                announce(release);
            } else {
                state = State.UP_TO_DATE;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ReasonException e) {
            fail(e.key, null);
        } catch (Exception e) {
            fail(null, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    /**
     * @param key    language key of the reason, or null
     * @param detail raw error text if there is no key
     */
    private void fail(String key, String detail) {
        // Log only when the reason changes, so a missing project does not spam the console every few hours.
        boolean newReason = state != State.FAILED || !java.util.Objects.equals(key, errorKey)
                || !java.util.Objects.equals(detail, error);
        state = State.FAILED;
        errorKey = key;
        error = detail;
        if (newReason) {
            plugin.getLogger().info("Update check not possible: " + (key != null ? plugin.messages().console(key) : detail));
        }
    }

    /** An expected failure with a translatable reason. */
    private static final class ReasonException extends IOException {
        final String key;

        ReasonException(String key) {
            super(key);
            this.key = key;
        }
    }

    private record Release(String version, String url) {
    }

    /** Releases for another Minecraft version are ignored; old tags without "mc..." prefix count as this one. */
    private boolean sameMinecraft(String version) {
        String mc = Versions.minecraft(version);
        return mc == null || minecraft == null || mc.equals(minecraft);
    }

    private Release fetchModrinth(boolean prereleases) throws IOException, InterruptedException {
        String loaders = URLEncoder.encode("[\"paper\"]", StandardCharsets.UTF_8);
        String body = get("https://api.modrinth.com/v2/project/" + modrinthProject
                + "/version?include_changelog=false&loaders=" + loaders);
        Release best = null;
        for (JsonElement element : JsonParser.parseString(body).getAsJsonArray()) {
            JsonObject version = element.getAsJsonObject();
            if (!prereleases && !"release".equals(version.get("version_type").getAsString())) {
                continue;
            }
            String number = version.get("version_number").getAsString();
            if (!sameMinecraft(number) || (minecraft != null && !supports(version.getAsJsonArray("game_versions")))) {
                continue;
            }
            if (best == null || Versions.compare(number, best.version()) > 0) {
                best = new Release(number, modrinthPage() + "/version/" + number);
            }
        }
        return best;
    }

    /** Whether a Modrinth version lists our Minecraft version (or a patch of it, e.g. 26.3.1 for 26.3). */
    private boolean supports(JsonArray gameVersions) {
        if (gameVersions == null) {
            return true;
        }
        for (JsonElement v : gameVersions) {
            String game = v.getAsString();
            if (game.equals(minecraft) || game.startsWith(minecraft + ".")) {
                return true;
            }
        }
        return false;
    }

    private Release fetchGithub(boolean prereleases) throws IOException, InterruptedException {
        if (githubRepository.isBlank()) {
            throw new ReasonException("update-reason-no-repository");
        }
        String body = get("https://api.github.com/repos/" + githubRepository + "/releases?per_page=30");
        JsonArray releases = JsonParser.parseString(body).getAsJsonArray();
        Release best = null;
        for (JsonElement element : releases) {
            JsonObject release = element.getAsJsonObject();
            if (release.get("draft").getAsBoolean() || (!prereleases && release.get("prerelease").getAsBoolean())) {
                continue;
            }
            String number = release.get("tag_name").getAsString();
            if (!sameMinecraft(number)) {
                continue;
            }
            if (best == null || Versions.compare(number, best.version()) > 0) {
                best = new Release(number, release.get("html_url").getAsString());
            }
        }
        return best;
    }

    private String get(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                // Modrinth asks for a unique, identifying User-Agent.
                .header("User-Agent", "MRchunks/" + currentVersion + " (" + githubPage() + ")")
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) {
            throw new ReasonException("update-reason-not-found");
        }
        if (response.statusCode() / 100 != 2) {
            throw new IOException("HTTP " + response.statusCode());
        }
        return response.body();
    }

    private void announce(Release release) {
        if (release.version().equals(announcedVersion)) {
            return;
        }
        announcedVersion = release.version();
        Logger log = plugin.getLogger();
        log.warning("==========================================================");
        log.warning(" A new version of MRchunks is available!");
        log.warning(" Installed: " + Versions.full(minecraft, currentVersion())
                + "   |   New: " + Versions.full(minecraft, Versions.plugin(release.version())));
        log.warning(" Download: " + (downloadPage.isEmpty() ? release.url() : downloadPage));
        log.warning("==========================================================");
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                notifyIfOutdated(player);
            }
        });
    }
}
