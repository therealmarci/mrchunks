package at.marcelr.mrchunks.api;

import at.marcelr.mrchunks.Loader;
import at.marcelr.mrchunks.MRChunks;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;

/**
 * The optional connection to MRadmin. MRchunks works the same without it; while MRadmin runs, MRchunks is one of its
 * modules ({@link MRadminModule}). This class uses no MRadmin types, so it is safe to load without MRadmin - only
 * {@link MRadminModule} touches them, and only after MRadmin is known to be enabled.
 */
public final class MRadminLink implements Listener {

    private static final String MRADMIN = "MRadmin";

    private final MRChunks plugin;
    private boolean connected;
    /** The saved measurements are read only once, also if MRadmin connects again. */
    private boolean historyRestored;

    public MRadminLink(MRChunks plugin) {
        this.plugin = plugin;
    }

    /** Connects if MRadmin runs (on enable; MRadmin enables first, MRchunks lists it under softdepend). */
    public void connect() {
        connected = false;
        if (!Bukkit.getPluginManager().isPluginEnabled(MRADMIN)) {
            return;
        }
        try {
            connected = MRadminModule.register(plugin);
            if (!connected) {
                plugin.getLogger().info("MRadmin found, but it is too old for the module API - update MRadmin to connect.");
            } else if (!historyRestored) {
                historyRestored = true;
                MRadminModule.restoreHistory(plugin);
            }
        } catch (LinkageError | RuntimeException e) {
            plugin.getLogger().warning("Could not connect to MRadmin (versions do not fit?): " + e);
        }
    }

    /** One measuring round for the charts - saved with MRadmin's data, so they survive a restart. */
    public void saveHistory(long time, String data) {
        if (connected) {
            try {
                MRadminModule.saveHistory(time, data);
            } catch (LinkageError | RuntimeException e) {
                plugin.getLogger().warning("Could not save the measurements with MRadmin: " + e);
            }
        }
    }

    /** A loader was locked because of lag - MRadmin opens a ticket for its owner. */
    public void lagLocked(Loader loader, String details) {
        if (connected) {
            try {
                MRadminModule.reportLock(plugin, loader, details);
            } catch (LinkageError | RuntimeException e) {
                plugin.getLogger().warning("Could not report the lock to MRadmin: " + e);
            }
        }
    }

    /** The lag lock was lifted - noted in the owner's ticket. */
    public void lagUnlocked(Loader loader, String admin) {
        if (connected) {
            try {
                MRadminModule.resolveLock(plugin, loader, admin);
            } catch (LinkageError | RuntimeException e) {
                plugin.getLogger().warning("Could not report the unlock to MRadmin: " + e);
            }
        }
    }

    /** Link to the map of the player page in MRadmin, or "" if there is none (no MRadmin, squaremap or address). */
    public String mapUrl() {
        try {
            return connected ? MRadminModule.mapUrl() : "";
        } catch (LinkageError | RuntimeException e) {
            return "";
        }
    }

    /** Link to the map of the player page centred on the loader, or "" if there is none. */
    public String mapUrl(Loader loader) {
        World world = loader.world();
        try {
            return connected && world != null ? MRadminModule.mapUrl(world, loader.x(), loader.z()) : "";
        } catch (LinkageError | RuntimeException e) {
            return "";
        }
    }

    /** The world belongs to another plugin, e.g. MRpvp's arena world (false without MRadmin). */
    public boolean reservedWorld(World world) {
        try {
            return connected && MRadminModule.reservedWorld(world);
        } catch (LinkageError | RuntimeException e) {
            plugin.getLogger().warning("MRadmin world check failed, ignoring it from now on: " + e);
            connected = false;
            return false;
        }
    }

    /** An event of the Discord log also into MRadmin's log (web interface, API 13). */
    public void log(String event, String text, int color) {
        if (connected) {
            try {
                MRadminModule.log(event, text, color);
            } catch (LinkageError | RuntimeException e) {
                plugin.getLogger().warning("Could not write into MRadmin's log: " + e);
            }
        }
    }

    /** Kind of the team notice "loader switched off by lag" and of the lock notice (MRadmin's choices). */
    public static final String NOTICE_SHED = "chunks-shed";
    public static final String NOTICE_LOCKED = "chunks-locked";

    /**
     * A team notice through MRadmin (inbox, push, game line; API 15): {@code texts} gives {title, body} for a language
     * code. Nothing without MRadmin.
     */
    public void teamNotice(String kind, String ref, String link, java.util.function.Function<String, String[]> texts) {
        if (connected) {
            try {
                MRadminModule.teamNotice(kind, ref, link, texts);
            } catch (LinkageError | RuntimeException e) {
                plugin.getLogger().warning("Could not send a team notice to MRadmin: " + e);
            }
        }
    }

    /** The team notices about {@code ref} are settled (e.g. the loader is back). */
    public void teamNoticeDone(String ref) {
        if (connected) {
            try {
                MRadminModule.teamNoticeDone(ref);
            } catch (LinkageError | RuntimeException e) {
                plugin.getLogger().warning("Could not update a team notice in MRadmin: " + e);
            }
        }
    }

    /**
     * Whether this team member wants messages of this kind in the game - their choice in MRadmin and not muted with
     * /mradmin mute. Always true without MRadmin.
     */
    public boolean teamInGame(org.bukkit.entity.Player player, String kind) {
        if (connected) {
            try {
                return MRadminModule.teamInGame(player.getUniqueId(), kind);
            } catch (LinkageError | RuntimeException e) {
                return true;
            }
        }
        return true;
    }

    /** Into MRadmin's Discord admin log. @return false without MRadmin */
    public boolean adminLog(String text) {
        if (connected) {
            try {
                MRadminModule.adminLog(text);
                return true;
            } catch (LinkageError | RuntimeException e) {
                plugin.getLogger().warning("Could not write into MRadmin's Discord log: " + e);
            }
        }
        return false;
    }

    /** MRadmin enabled later (e.g. with a plugin manager): connect now. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEnable(PluginEnableEvent event) {
        if (event.getPlugin().getName().equals(MRADMIN)) {
            connect();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDisable(PluginDisableEvent event) {
        if (event.getPlugin().getName().equals(MRADMIN)) {
            connected = false;
        }
    }
}
