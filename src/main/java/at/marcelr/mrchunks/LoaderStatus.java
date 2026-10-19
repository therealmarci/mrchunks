package at.marcelr.mrchunks;

import at.marcelr.mrchunks.lag.Activity;
import org.bukkit.Material;

/**
 * What state a loader is in, as shown by its icon in all menus (also in MRadmin). The first matching state wins:
 * locked, switched off, paused by the server pause, over a warn limit, ok.
 */
public enum LoaderStatus {
    /** Locked because of lag - only admins can lift it. */
    LOCKED(Material.REDSTONE_BLOCK),
    /** Switched off by a player or admin. */
    INACTIVE(Material.GRAY_CONCRETE),
    /** Switched on, but holding no chunks while the server lags (server pause). */
    PAUSED(Material.COBWEB),
    /** Last measurement over a warn limit. */
    WARN(Material.MAGMA_BLOCK),
    /** Everything fine: shown as the loader block (item.material, emerald block by default). */
    OK(null);

    private final Material material;

    LoaderStatus(Material material) {
        this.material = material;
    }

    public static LoaderStatus of(MRChunks plugin, Loader loader) {
        if (plugin.loaders().lagLocked(loader)) {
            return LOCKED;
        }
        if (!loader.active()) {
            return INACTIVE;
        }
        if (plugin.loaders().paused()) {
            return PAUSED;
        }
        Activity activity = plugin.settings().lag().enabled() ? plugin.lag().activity(loader) : null;
        return activity != null && activity.conspicuous() ? WARN : OK;
    }

    public Material material(MRChunks plugin) {
        return material != null ? material : plugin.settings().item().material();
    }
}
