package at.marcelr.mrchunks;

import io.papermc.paper.event.packet.PlayerChunkLoadEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;

/**
 * Redstone signal of chunk loaders before restarts (loaders.restart-signal, per loader in its settings menu): a loader
 * set to "on before restarts" gives a full signal (strength 15 to all sides) from the chosen number of minutes before
 * any restart or stop MRadmin knows of (planned or started by someone) until the server goes down; "off before
 * restarts" gives it all the time and switches it off then. Without MRadmin there is no signal.
 *
 * <p>A block only gives a signal if Minecraft knows it as a power source, so on the server the loader becomes a redstone
 * block while it signals. Every player keeps seeing the chosen block: it is sent to them in its place whenever the
 * chunk reaches them or the server would show the real block (a refused attempt to break it). Only the web map, which
 * draws the server's world, shows the redstone block. When MRchunks stops, every loader gets its own block back.
 */
public final class RestartSignal implements Listener {

    /** How a loader uses the signal. */
    public enum Mode { NONE, ON, OFF }

    private final MRChunks plugin;
    /** Loaders that stand as a redstone block right now. */
    private final Set<Integer> powered = new HashSet<>();
    private BukkitTask task;

    RestartSignal(MRChunks plugin) {
        this.plugin = plugin;
    }

    void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20, 20);
    }

    /** Stops and gives every loader its own block back (also when MRchunks is disabled). */
    void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Integer id : List.copyOf(powered)) {
            Loader loader = plugin.loaders().byId(id);
            if (loader != null) {
                apply(loader, false);
            }
        }
        powered.clear();
    }

    /** Whether loaders can use the signal right now: switched on in the config and MRadmin connected (API 14+). */
    public boolean available() {
        return plugin.settings().loaders().restartSignal() && plugin.mradmin().restartsKnown();
    }

    /** The loader stands as a redstone block right now. */
    public boolean powered(Loader loader) {
        return powered.contains(loader.id());
    }

    /** The block the loader should stand as on the server right now. */
    public Material blockFor(Loader loader) {
        return powered(loader) ? Material.REDSTONE_BLOCK : plugin.loaders().blockOf(loader);
    }

    /** The loader is gone. */
    void forget(Loader loader) {
        powered.remove(loader.id());
    }

    /** Milliseconds until the next restart or stop, -1 if none is known. */
    public long restartIn() {
        return plugin.mradmin().restartMillisLeft();
    }

    private void tick() {
        boolean on = available();
        long left = on ? restartIn() : -1;
        for (Loader loader : List.copyOf(plugin.loaders().all())) {
            boolean want = on && wants(loader, left);
            if (want != powered(loader)) {
                World world = loader.world();
                // only in loaded chunks - the check on chunk load and this tick fix the rest later
                if (world != null && world.isChunkLoaded(loader.chunkX(), loader.chunkZ())) {
                    apply(loader, want);
                }
            }
        }
    }

    /** Whether the loader should give a signal now, with a restart in {@code left} ms (-1 = none known). */
    static boolean wants(Loader loader, long left) {
        if (loader.signalMode() == Mode.NONE) {
            return false;
        }
        boolean soon = left >= 0 && left <= loader.signalMinutes() * 60_000L;
        return loader.signalMode() == Mode.ON ? soon : !soon;
    }

    /** Swaps the loader to a redstone block or back; neighbours get the redstone update. */
    private void apply(Loader loader, boolean on) {
        World world = loader.world();
        if (world == null) {
            return;
        }
        Block block = world.getBlockAt(loader.x(), loader.y(), loader.z());
        if (on) {
            powered.add(loader.id());
        } else {
            powered.remove(loader.id());
        }
        block.setType(blockFor(loader), true);
        if (on) {
            disguise(loader);
        }
    }

    /** Shows the chosen block to every player who has the loader's chunk. */
    public void disguise(Loader loader) {
        World world = loader.world();
        if (world == null || !powered(loader)) {
            return;
        }
        long key = Chunk.getChunkKey(loader.chunkX(), loader.chunkZ());
        for (Player player : world.getPlayers()) {
            if (player.isChunkSent(key)) {
                show(player, loader);
            }
        }
    }

    /** Shows the chosen block to one player (e.g. after the server sent the real one). */
    public void disguise(Player player, Loader loader) {
        if (powered(loader)) {
            Bukkit.getScheduler().runTask(plugin, () -> show(player, loader));
        }
    }

    private void show(Player player, Loader loader) {
        World world = loader.world();
        if (world == null || !player.isOnline() || !player.getWorld().equals(world) || !powered(loader)) {
            return;
        }
        BlockData data = plugin.loaders().blockOf(loader).createBlockData();
        player.sendBlockChange(new Location(world, loader.x(), loader.y(), loader.z()), data);
    }

    /** A chunk reached a player: loaders in it that signal look like their chosen block. */
    @EventHandler
    public void onChunkSent(PlayerChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        for (Loader loader : plugin.loaders().inChunk(chunk.getWorld(), chunk.getX(), chunk.getZ())) {
            disguise(event.getPlayer(), loader);
        }
    }
}
