package at.marcelr.mrchunks;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shows the edge of a loader's area as glowing lines: a "fence" of horizontal bars along every chunk of the edge
 * plus a post at every chunk corner, around the viewer's height. The glow is visible through blocks.
 * <p>
 * The lines are client-visible block displays that only the viewer sees ({@code visibleByDefault = false}) and that
 * are never saved ({@code persistent = false}). They follow the viewer's height, disappear after the configured
 * time, when the loader is deleted, or when the viewer leaves or changes world. Each player sees at most one border.
 */
public final class BorderDisplay implements Listener {

    private static final long INTERVAL_TICKS = 10L;
    /** Display entities are culled beyond this multiple of 64 blocks; the area is up to ~150 blocks across. */
    private static final float VIEW_RANGE = 4f;

    private final MRChunks plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();

    /** One part of the border: where it is anchored (relative to the base height) and its shape. */
    private record Part(double x, double yOffset, double z, Transformation shape) {
    }

    private final class Session {
        final UUID viewer;
        final int loaderId;
        final long until;
        final List<Part> parts = new ArrayList<>();
        final List<BlockDisplay> entities = new ArrayList<>();
        BukkitTask task;
        double baseY;

        Session(UUID viewer, int loaderId, long until) {
            this.viewer = viewer;
            this.loaderId = loaderId;
            this.until = until;
        }
    }

    public BorderDisplay(MRChunks plugin) {
        this.plugin = plugin;
    }

    /** Shows the border of {@code loader} to {@code player} (replacing any border they see). */
    public void show(Player player, Loader loader) {
        show(player, loader, System.currentTimeMillis() + plugin.settings().border().durationSeconds() * 1000L);
    }

    private void show(Player player, Loader loader, long until) {
        hide(player);
        World world = loader.world();
        if (world == null || !player.getWorld().equals(world)) {
            return;
        }
        Session session = new Session(player.getUniqueId(), loader.id(), until);
        buildParts(session, loader);
        session.baseY = Math.floor(player.getLocation().getY());
        spawn(session, player, loader);
        session.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(session), INTERVAL_TICKS, INTERVAL_TICKS);
        sessions.put(player.getUniqueId(), session);
    }

    /** Whether {@code player} currently sees the border of {@code loader}. */
    public boolean isShowing(Player player, Loader loader) {
        Session session = sessions.get(player.getUniqueId());
        return session != null && session.loaderId == loader.id();
    }

    public void hide(Player player) {
        Session session = sessions.remove(player.getUniqueId());
        if (session != null) {
            end(session);
        }
    }

    /** The loader was deleted: nobody may keep seeing its border. */
    public void loaderRemoved(Loader loader) {
        for (Session session : List.copyOf(sessions.values())) {
            if (session.loaderId == loader.id()) {
                sessions.remove(session.viewer);
                end(session);
            }
        }
    }

    /** The loader was switched on/off: redraw open borders in the matching colour. */
    public void loaderChanged(Loader loader) {
        for (Session session : List.copyOf(sessions.values())) {
            Player player = Bukkit.getPlayer(session.viewer);
            if (session.loaderId == loader.id() && player != null) {
                show(player, loader, session.until);
            }
        }
    }

    public void stopAll() {
        sessions.values().forEach(this::end);
        sessions.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        hide(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        hide(event.getPlayer());
    }

    // ------------------------------------------------------------- drawing

    /** Horizontal bars per chunk along all four edges at every line height, plus a post at each chunk corner. */
    private void buildParts(Session session, Loader loader) {
        Settings.BorderSettings border = plugin.settings().border();
        float t = (float) border.thickness();
        int minChunkX = loader.minChunkX();
        int maxChunkX = loader.maxChunkX();
        int minChunkZ = loader.minChunkZ();
        int maxChunkZ = loader.maxChunkZ();
        double minX = minChunkX << 4;
        double maxX = (maxChunkX + 1) << 4;
        double minZ = minChunkZ << 4;
        double maxZ = (maxChunkZ + 1) << 4;

        for (int offset = -border.below(); offset <= border.above(); offset += border.lineSpacing()) {
            for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                double mid = (cx << 4) + 8;
                session.parts.add(new Part(mid, offset, minZ, bar(16, t, t)));
                session.parts.add(new Part(mid, offset, maxZ, bar(16, t, t)));
            }
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                double mid = (cz << 4) + 8;
                session.parts.add(new Part(minX, offset, mid, bar(t, t, 16)));
                session.parts.add(new Part(maxX, offset, mid, bar(t, t, 16)));
            }
        }
        // Posts at every chunk boundary of the edge, from the lowest to the highest line.
        float height = border.below() + border.above();
        for (int cx = minChunkX; cx <= maxChunkX + 1; cx++) {
            session.parts.add(new Part(cx << 4, -border.below(), minZ, post(t, height)));
            session.parts.add(new Part(cx << 4, -border.below(), maxZ, post(t, height)));
        }
        for (int cz = minChunkZ + 1; cz <= maxChunkZ; cz++) {
            session.parts.add(new Part(minX, -border.below(), cz << 4, post(t, height)));
            session.parts.add(new Part(maxX, -border.below(), cz << 4, post(t, height)));
        }
    }

    /** A bar centred on its anchor (so the anchor - which decides when the client sees it - is in the middle). */
    private static Transformation bar(float sx, float sy, float sz) {
        return new Transformation(new Vector3f(-sx / 2, -sy / 2, -sz / 2), new AxisAngle4f(),
                new Vector3f(sx, sy, sz), new AxisAngle4f());
    }

    /** A post standing on its anchor. */
    private static Transformation post(float t, float height) {
        return new Transformation(new Vector3f(-t / 2, 0, -t / 2), new AxisAngle4f(),
                new Vector3f(t, height, t), new AxisAngle4f());
    }

    private void spawn(Session session, Player player, Loader loader) {
        Settings.BorderSettings border = plugin.settings().border();
        World world = player.getWorld();
        BlockData block = (loader.active() ? border.block() : border.inactiveBlock()).createBlockData();
        Color glow = loader.active() ? border.color() : border.inactiveColor();
        for (Part part : session.parts) {
            Location location = new Location(world, part.x(), session.baseY + part.yOffset(), part.z());
            if (!world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                session.entities.add(null); // spawned later, once the chunk is loaded
                continue;
            }
            session.entities.add(spawnPart(world, location, part, block, glow, player));
        }
    }

    private BlockDisplay spawnPart(World world, Location location, Part part, BlockData block, Color glow, Player viewer) {
        BlockDisplay display = world.spawn(location, BlockDisplay.class, entity -> {
            entity.setPersistent(false);
            entity.setVisibleByDefault(false);
            entity.setBlock(block);
            entity.setTransformation(part.shape());
            entity.setGlowing(true);
            entity.setGlowColorOverride(glow);
            entity.setBrightness(new Display.Brightness(15, 15));
            entity.setViewRange(VIEW_RANGE);
            entity.setTeleportDuration(5); // smooth movement when following the viewer
        });
        viewer.showEntity(plugin, display);
        return display;
    }

    private void tick(Session session) {
        Player player = Bukkit.getPlayer(session.viewer);
        Loader loader = plugin.loaders().byId(session.loaderId);
        if (player == null || loader == null || !player.getWorld().equals(loader.world())
                || System.currentTimeMillis() > session.until) {
            if (sessions.get(session.viewer) == session) {
                sessions.remove(session.viewer);
            }
            end(session);
            return;
        }
        double y = Math.floor(player.getLocation().getY());
        boolean moved = Math.abs(y - session.baseY) >= 1;
        session.baseY = y;
        Settings.BorderSettings border = plugin.settings().border();
        BlockData block = (loader.active() ? border.block() : border.inactiveBlock()).createBlockData();
        Color glow = loader.active() ? border.color() : border.inactiveColor();
        World world = player.getWorld();
        for (int i = 0; i < session.parts.size(); i++) {
            Part part = session.parts.get(i);
            Location location = new Location(world, part.x(), y + part.yOffset(), part.z());
            BlockDisplay display = session.entities.get(i);
            if (display == null || !display.isValid()) {
                // Not spawned yet or removed with its chunk: (re)spawn once the chunk is loaded.
                if (world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                    session.entities.set(i, spawnPart(world, location, part, block, glow, player));
                }
            } else if (moved) {
                display.teleport(location);
            }
        }
    }

    private void end(Session session) {
        if (session.task != null) {
            session.task.cancel();
        }
        for (BlockDisplay display : session.entities) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        session.entities.clear();
    }
}
