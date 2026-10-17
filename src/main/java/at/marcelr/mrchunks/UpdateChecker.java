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
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static at.marcelr.mrchunks.Messages.tag;

/**
 * Looks up the newest published version on Modrinth or GitHub Releases and reports outdated installs
 * in the console and to admins. All HTTP requests run off the main thread.
 */
public final class UpdateChecker {

    public static final String PERM_NOTIFY = "mrchunks.admin.update";

    public enum State { DISABLED, CHECKING, UP_TO_DATE, OUTDATED, FAILED }

    private final MRChunks plugin;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final String currentVersion;
    private final String modrinthProject;
    private final String githubRepository;

    private volatile State state = State.DISABLED;
    private volatile String latestVersion;
    private volatile String downloadUrl;
    private volatile String error;
    private String announcedVersion;
    private BukkitTask task;

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
        this.modrinthProject = info.getProperty("modrinthProjectId", "mrchunks");
        this.githubRepository = info.getProperty("githubRepository", "");
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
        Messages messages = plugin.messages();
        switch (state) {
            case UP_TO_DATE -> messages.send(sender, "version-latest");
            case OUTDATED -> messages.send(sender, "version-outdated",
                    tag("latest", latestVersion), tag("current", currentVersion), tag("download", downloadLink()));
            case DISABLED -> messages.send(sender, "version-unknown", tag("reason", "Update-Check ist deaktiviert"));
            case CHECKING -> messages.send(sender, "version-checking");
            case FAILED -> messages.send(sender, "version-unknown", tag("reason", error == null ? "?" : error));
        }
    }

    /** Join notice for admins. */
    public void notifyIfOutdated(Player player) {
        if (state == State.OUTDATED && plugin.settings().update().notifyOnJoin() && player.hasPermission(PERM_NOTIFY)) {
            plugin.messages().send(player, "update-join-notify",
                    tag("latest", latestVersion), tag("current", currentVersion), tag("download", downloadLink()));
        }
    }

    public Component downloadLink() {
        String url = downloadUrl == null ? modrinthPage() : downloadUrl;
        return plugin.messages().get("update-download-label").clickEvent(ClickEvent.openUrl(url));
    }

    public String modrinthPage() {
        return "https://modrinth.com/plugin/" + modrinthProject;
    }

    public String githubPage() {
        return "https://github.com/" + githubRepository;
    }

    public State state() {
        return state;
    }

    public String latestVersion() {
        return latestVersion;
    }

    public String currentVersion() {
        return currentVersion;
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
                fail("Keine veröffentlichte Version gefunden");
                return;
            }
            latestVersion = release.version();
            downloadUrl = release.url();
            error = null;
            if (Versions.compare(release.version(), currentVersion) > 0) {
                state = State.OUTDATED;
                announce(release);
            } else {
                state = State.UP_TO_DATE;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            fail(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    private void fail(String reason) {
        // Log only when the reason changes, so a missing project does not spam the console every few hours.
        boolean newReason = state != State.FAILED || !reason.equals(error);
        state = State.FAILED;
        error = reason;
        if (newReason) {
            plugin.getLogger().info("Update-Check nicht möglich: " + reason);
        }
    }

    private record Release(String version, String url) {
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
            if (best == null || Versions.compare(number, best.version()) > 0) {
                best = new Release(number, modrinthPage() + "/version/" + number);
            }
        }
        return best;
    }

    private Release fetchGithub(boolean prereleases) throws IOException, InterruptedException {
        if (githubRepository.isBlank()) {
            throw new IOException("Kein GitHub-Repository hinterlegt");
        }
        String body = get("https://api.github.com/repos/" + githubRepository + "/releases?per_page=30");
        JsonArray releases = JsonParser.parseString(body).getAsJsonArray();
        Release best = null;
        for (JsonElement element : releases) {
            JsonObject release = element.getAsJsonObject();
            if (release.get("draft").getAsBoolean() || (!prereleases && release.get("prerelease").getAsBoolean())) {
                continue;
            }
            String number = release.get("tag_name").getAsString().replaceFirst("^[vV]", "");
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
            throw new IOException("Projekt nicht gefunden (HTTP 404) - noch nicht veröffentlicht?");
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
        log.warning(" Eine neue Version von MRchunks ist verfügbar!");
        log.warning(" Installiert: " + currentVersion + "   |   Neu: " + release.version());
        log.warning(" Download: " + release.url());
        log.warning("==========================================================");
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                notifyIfOutdated(player);
            }
        });
    }
}
