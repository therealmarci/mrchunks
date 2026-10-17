package at.marcelr.mrchunks;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

/**
 * One placed chunk loader. Position and owner never change; only {@link #active()} does.
 * The loaded area is the square of chunks with {@code radius} around {@link #chunkX()}/{@link #chunkZ()}.
 */
public final class Loader {

    private final int id;
    private final UUID owner;
    private final UUID worldId;
    private final String worldName;
    private final int x;
    private final int y;
    private final int z;
    private final long created;
    private boolean active;

    public Loader(int id, UUID owner, UUID worldId, String worldName, int x, int y, int z, long created, boolean active) {
        this.id = id;
        this.owner = owner;
        this.worldId = worldId;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.created = created;
        this.active = active;
    }

    public int id() {
        return id;
    }

    public UUID owner() {
        return owner;
    }

    public UUID worldId() {
        return worldId;
    }

    /** Name at the time of placing, used when the world is not loaded. */
    public String worldName() {
        World world = world();
        return world == null ? worldName : world.getName();
    }

    /** @return the world, or null if it is not loaded */
    public World world() {
        return Bukkit.getWorld(worldId);
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    public int chunkX() {
        return x >> 4;
    }

    public int chunkZ() {
        return z >> 4;
    }

    public long created() {
        return created;
    }

    public boolean active() {
        return active;
    }

    void setActive(boolean active) {
        this.active = active;
    }

    /** @return the block location, or null if the world is not loaded */
    public Location location() {
        World world = world();
        return world == null ? null : new Location(world, x, y, z);
    }

    public boolean isAt(World world, int bx, int by, int bz) {
        return world.getUID().equals(worldId) && bx == x && by == y && bz == z;
    }

    /** Packed key of a chunk, as used for the per-world ticket reference counts. */
    public static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }
}
