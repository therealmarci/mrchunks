package at.marcelr.mrchunks.map;

import at.marcelr.mrchunks.MRChunks;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
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
            plugin.getLogger().info("squaremap gefunden - Chunkloader werden auf der Webkarte eingezeichnet.");
        } catch (LinkageError | RuntimeException e) {
            squaremap = null;
            status = "map-state-error";
            error = e.getClass().getSimpleName();
            plugin.getLogger().log(Level.WARNING, "squaremap-Anbindung fehlgeschlagen (passende squaremap-Version installiert?)", e);
            return;
        }
        if (setup.pending()) {
            runSetup(Bukkit.getConsoleSender(), false);
        }
        long interval = plugin.settings().squaremap().updateSeconds() * 20L;
        periodic = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshNow, interval, interval);
    }

    /**
     * Writes the recommended squaremap settings and reloads squaremap.
     * @param force also run if it already ran before (/mrchunks map setup)
     */
    public void runSetup(CommandSender sender, boolean force) {
        if (!force && !setup.pending()) {
            return;
        }
        try {
            List<String> changes = setup.apply();
            if (changes.isEmpty()) {
                plugin.messages().send(sender, "map-setup-nothing");
                return;
            }
            plugin.getLogger().info("squaremap eingerichtet: " + String.join(", ", changes) + ".");
            if (sender != Bukkit.getConsoleSender()) {
                plugin.messages().send(sender, "map-setup-done", tag("changes", String.join(", ", changes)));
            }
            // squaremap reads its config only on (re)load; our layers and the grid are restored right after.
            Bukkit.getScheduler().runTask(plugin, () -> {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "squaremap reload");
                Bukkit.getScheduler().runTaskLater(plugin, this::refreshNow, 40L);
            });
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "squaremap-Config konnte nicht angepasst werden", e);
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
            plugin.getLogger().log(Level.WARNING, "squaremap: Markierungen konnten nicht aktualisiert werden", e);
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
                return squaremap.publicUrl();
            } catch (LinkageError | RuntimeException e) {
                return "";
            }
        }
        return "";
    }

    public boolean squaremapActive() {
        return squaremap != null;
    }

    /** "active", "not installed", "disabled in the config" or an error - in the configured language. */
    public String squaremapStatus() {
        return plugin.messages().plain(status, "error", error);
    }
}
