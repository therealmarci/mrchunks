package at.marcelr.mrchunks.map;

import at.marcelr.mrchunks.MRChunks;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.logging.Level;

/**
 * Connects MRchunks to optional web map plugins. This class must not reference any squaremap type itself:
 * {@link SquaremapHook} is only loaded when squaremap is installed, so the plugin runs fine without it.
 */
public final class MapHooks {

    private final MRChunks plugin;
    private SquaremapHook squaremap;
    private BukkitTask periodic;
    private boolean refreshQueued;
    private String status = "nicht installiert";

    public MapHooks(MRChunks plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        if (!plugin.settings().squaremap().enabled()) {
            status = "in der Config ausgeschaltet";
            return;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("squaremap")) {
            status = "nicht installiert";
            return;
        }
        try {
            squaremap = new SquaremapHook(plugin);
            squaremap.update();
            status = "aktiv";
            plugin.getLogger().info("squaremap gefunden - Chunkloader werden auf der Webkarte eingezeichnet.");
        } catch (LinkageError | RuntimeException e) {
            squaremap = null;
            status = "Fehler: " + e.getClass().getSimpleName();
            plugin.getLogger().log(Level.WARNING, "squaremap-Anbindung fehlgeschlagen (passende squaremap-Version installiert?)", e);
            return;
        }
        long interval = plugin.settings().squaremap().updateSeconds() * 20L;
        periodic = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshNow, interval, interval);
    }

    public void stop() {
        if (periodic != null) {
            periodic.cancel();
            periodic = null;
        }
        if (squaremap != null) {
            try {
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
        } catch (LinkageError | RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "squaremap: Markierungen konnten nicht aktualisiert werden", e);
        }
    }

    public boolean squaremapActive() {
        return squaremap != null;
    }

    /** "aktiv", "nicht installiert", "in der Config ausgeschaltet" or an error. */
    public String squaremapStatus() {
        return status;
    }
}
