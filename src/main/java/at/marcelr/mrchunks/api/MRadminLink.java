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
            }
        } catch (LinkageError | RuntimeException e) {
            plugin.getLogger().warning("Could not connect to MRadmin (versions do not fit?): " + e);
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
