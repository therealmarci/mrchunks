package at.marcelr.mrchunks.lag;

import at.marcelr.mrchunks.Loader;
import at.marcelr.mrchunks.MRChunks;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;

/**
 * Items in running chunk loaders always at full speed (loaders.items-always-active): outside the activation range of
 * every player and dummy (spigot.yml entity-activation-range.misc, usually 16 blocks) Paper ticks an item only every
 * 4th tick until it notices the movement - item streams on water or ice in a big loader then stutter and crawl. Every
 * {@link #INTERVAL} ticks this marks the items in the areas of all running loaders as active until the next round, like
 * a player standing next to them. The activation range of the rest of the world stays as it is.
 */
public final class ItemActivation {

    /** What needs the server's internals (lag/nms/NmsItemActivation, loaded by name). */
    public interface Backend {
        /** Keeps the entity fully ticked for at least the next {@code ticks} ticks. */
        void keepActive(Entity entity, int ticks);
    }

    private static final String BACKEND = "at.marcelr.mrchunks.lag.nms.NmsItemActivation";
    /** Ticks between two rounds; every round keeps the items active a little longer than that. */
    private static final int INTERVAL = 10;

    private final MRChunks plugin;
    private Backend backend;
    private String unavailableReason = "";
    private BukkitTask task;

    public ItemActivation(MRChunks plugin) {
        this.plugin = plugin;
        try {
            backend = (Backend) Class.forName(BACKEND).getConstructor().newInstance();
        } catch (Throwable t) {
            // NoSuchFieldException, NoClassDefFoundError ... - the server version does not fit this build.
            unavailableReason = t.toString();
        }
    }

    /** Starts or stops the rounds after the config (on enable and reload). */
    public void apply() {
        boolean wanted = plugin.settings().loaders().itemsAlwaysActive();
        if (wanted && backend == null) {
            plugin.getLogger().warning("loaders.items-always-active is enabled in config.yml but does not work with this server version ("
                    + Bukkit.getVersion() + "): " + unavailableReason + ". Items in chunk loaders follow the normal activation range.");
        }
        boolean run = wanted && backend != null;
        if (run && task == null) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::round, INTERVAL, INTERVAL);
        } else if (!run) {
            stop();
        }
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void round() {
        for (Loader loader : plugin.loaders().all()) {
            // only loaders that really keep their chunks loaded (not off, locked, shed or paused)
            if (!plugin.loaders().loading(loader)) {
                continue;
            }
            World world = loader.world();
            if (world == null) {
                continue;
            }
            BoundingBox area = new BoundingBox(loader.minChunkX() << 4, world.getMinHeight(), loader.minChunkZ() << 4,
                    (loader.maxChunkX() + 1) << 4, world.getMaxHeight(), (loader.maxChunkZ() + 1) << 4);
            for (Entity item : world.getNearbyEntities(area, e -> e instanceof Item)) {
                backend.keepActive(item, INTERVAL + 2);
            }
        }
    }
}
