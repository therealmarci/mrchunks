package at.marcelr.mrchunks.map;

import at.marcelr.mrchunks.MRChunks;
import at.marcelr.mrchunks.Messages;
import at.marcelr.mrchunks.Settings;
import at.marcelr.mrchunks.util.MapAddress;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.logging.Level;

import static at.marcelr.mrchunks.Messages.tag;

/**
 * Connects MRchunks to squaremap. Without squaremap there is no map feature at all.
 * This class must not reference any squaremap type itself: {@link SquaremapHook} is only loaded when squaremap
 * is installed, so the plugin runs fine without it.
 */
public final class MapHooks implements Listener {

    private final MRChunks plugin;
    private final SquaremapSetup setup;
    private SquaremapHook squaremap;
    private BukkitTask periodic;
    private boolean refreshQueued;
    /** Public IP of the server, detected once (see squaremap.detect-public-ip); "" until known. */
    private volatile String publicIp = "";
    private boolean publicIpRequested;
    /** Language key of the squaremap status (map-state-*). */
    private String status = "map-state-missing";
    private String error = "";

    public MapHooks(MRChunks plugin) {
        this.plugin = plugin;
        this.setup = new SquaremapSetup(plugin);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public boolean squaremapInstalled() {
        return Bukkit.getPluginManager().isPluginEnabled("squaremap");
    }

    public void start() {
        stop();
        if (!squaremapInstalled()) {
            status = "map-state-missing";
            return;
        }
        if (!plugin.settings().squaremap().enabled()) {
            status = "map-state-disabled";
            return;
        }
        try {
            squaremap = new SquaremapHook(plugin);
            squaremap.update();
            squaremap.hidePlayers(plugin.settings().squaremap().hidePlayers());
            status = "map-state-active";
            plugin.getLogger().info("squaremap found - chunk loaders are shown on the web map.");
        } catch (LinkageError | RuntimeException e) {
            squaremap = null;
            status = "map-state-error";
            error = e.getClass().getSimpleName();
            plugin.getLogger().log(Level.WARNING, "squaremap integration failed (is a matching squaremap version installed?)", e);
            return;
        }
        // Every start: is squaremap's config still as MRchunks recommends it (values changed by hand stay)?
        if (plugin.settings().squaremap().autoConfigure()) {
            runSetup(Bukkit.getConsoleSender(), false);
        }
        detectPublicIp();
        long interval = plugin.settings().squaremap().updateSeconds() * 20L;
        periodic = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshNow, interval, interval);
    }

    /**
     * Brings the recommended squaremap settings up to date and reloads squaremap if something changed.
     * @param force also overwrite values changed by hand (/mrchunks map setup)
     */
    public void runSetup(CommandSender sender, boolean force) {
        try {
            List<SquaremapSetup.Change> changes = setup.apply(force);
            if (changes.isEmpty()) {
                if (force) {
                    plugin.messages().send(sender, "map-setup-nothing");
                }
                return;
            }
            Messages messages = plugin.messages();
            plugin.getLogger().info("squaremap configured: " + String.join(", ", changes.stream()
                    .map(c -> messages.console(c.key(), c.placeholders())).toList()) + ".");
            if (sender != Bukkit.getConsoleSender()) {
                messages.send(sender, "map-setup-done", tag("changes", String.join(", ", changes.stream()
                        .map(c -> messages.plain(c.key(), c.placeholders())).toList())));
            }
            // squaremap reads its config only on (re)load; our layers and the grid are restored right after.
            Bukkit.getScheduler().runTask(plugin, () -> {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "squaremap reload");
                Bukkit.getScheduler().runTaskLater(plugin, this::refreshNow, 40L);
            });
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Could not adjust the squaremap config", e);
            plugin.messages().send(sender, "map-setup-failed", tag("reason", String.valueOf(e.getMessage())));
        }
    }

    public void stop() {
        if (periodic != null) {
            periodic.cancel();
            periodic = null;
        }
        if (squaremap != null) {
            try {
                squaremap.hidePlayers(false);
                squaremap.remove();
            } catch (LinkageError | RuntimeException e) {
                // squaremap may already be disabled during shutdown
            }
            squaremap = null;
        }
    }

    /** Called after every change; several changes in one tick lead to a single update. */
    public void refreshSoon() {
        if (squaremap == null || refreshQueued) {
            return;
        }
        refreshQueued = true;
        Bukkit.getScheduler().runTask(plugin, this::refreshNow);
    }

    private void refreshNow() {
        refreshQueued = false;
        if (squaremap == null) {
            return;
        }
        try {
            squaremap.update();
            squaremap.hidePlayers(plugin.settings().squaremap().hidePlayers());
        } catch (LinkageError | RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "squaremap: could not update the markers", e);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (squaremap != null && plugin.settings().squaremap().hidePlayers()) {
            try {
                squaremap.hide(event.getPlayer().getUniqueId());
            } catch (LinkageError | RuntimeException e) {
                // ignore - squaremap not ready
            }
        }
    }

    /** Public link to the web map, or empty if there is none. */
    public String mapUrl() {
        if (squaremap != null) {
            try {
                return squaremap.publicUrl(publicIp);
            } catch (LinkageError | RuntimeException e) {
                return "";
            }
        }
        return "";
    }

    /**
     * Without squaremap.address and without a usable server-ip the link needs the public IP. It is asked for once,
     * in the background, at checkip.amazonaws.com (only the request, no data is sent). Off with detect-public-ip: false.
     */
    private void detectPublicIp() {
        Settings.SquaremapSettings settings = plugin.settings().squaremap();
        if (publicIpRequested || !settings.detectPublicIp() || !settings.address().isEmpty() || MapAddress.usable(Bukkit.getIp())) {
            return;
        }
        publicIpRequested = true;
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://checkip.amazonaws.com"))
                .timeout(Duration.ofSeconds(10)).GET().build();
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, error) -> {
            String body = error == null && response.statusCode() == 200 ? response.body().trim() : "";
            if (body.matches("[0-9a-fA-F.:]{3,45}")) {
                publicIp = body;
                plugin.getLogger().info("Public IP for the web map link: " + body + " (set squaremap.address to use a domain).");
            } else {
                publicIpRequested = false; // try again on the next reload
                plugin.getLogger().info("Could not detect the public IP for the web map link - set squaremap.address.");
            }
        });
    }

    public boolean squaremapActive() {
        return squaremap != null;
    }

    /** "active", "not installed", "disabled in the config" or an error - in the configured language. */
    public String squaremapStatus() {
        return plugin.messages().plain(status, "error", error);
    }
}
