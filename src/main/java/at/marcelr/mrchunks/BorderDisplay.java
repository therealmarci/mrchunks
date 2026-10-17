package at.marcelr.mrchunks;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Shows the outer edge of a loader's area as a particle wall around the viewer's eye height.
 * Only the viewer sees the particles; each player sees at most one border at a time.
 */
public final class BorderDisplay {

    private static final long INTERVAL_TICKS = 10L;

    private final MRChunks plugin;
    private final Map<UUID, BukkitTask> running = new HashMap<>();

    public BorderDisplay(MRChunks plugin) {
        this.plugin = plugin;
    }

    public void show(Player player, Loader loader) {
        stop(player);
        Settings.BorderSettings border = plugin.settings().border();
        int radius = plugin.settings().loaders().radius();
        // Block coordinates of the outer edge (the area spans whole chunks).
        int minX = (loader.chunkX() - radius) << 4;
        int maxX = (loader.chunkX() + radius + 1) << 4;
        int minZ = (loader.chunkZ() - radius) << 4;
        int maxZ = (loader.chunkZ() + radius + 1) << 4;
        long until = System.currentTimeMillis() + border.durationSeconds() * 1000L;

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            World world = loader.world();
            if (!player.isOnline() || world == null || !player.getWorld().equals(world)
                    || System.currentTimeMillis() > until) {
                stop(player);
                return;
            }
            double eye = player.getEyeLocation().getY();
            int step = border.spacing();
            for (double y = eye - border.below(); y <= eye + border.above(); y += 2) {
                for (int x = minX; x <= maxX; x += step) {
                    spawn(player, x, y, minZ);
                    spawn(player, x, y, maxZ);
                }
                for (int z = minZ; z <= maxZ; z += step) {
                    spawn(player, minX, y, z);
                    spawn(player, maxX, y, z);
                }
            }
        }, 0L, INTERVAL_TICKS);
        running.put(player.getUniqueId(), task);
    }

    private void spawn(Player player, double x, double y, double z) {
        player.spawnParticle(plugin.settings().border().particle(), x, y, z, 1, 0, 0, 0, 0);
    }

    public void stop(Player player) {
        BukkitTask task = running.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    public void stopAll() {
        running.values().forEach(BukkitTask::cancel);
        running.clear();
    }
}
