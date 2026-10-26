package at.marcelr.mrchunks;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

/**
 * One placed chunk loader. Position, owner and number never change; {@link #active()} and {@link #name()} do.
 * <p>
 * {@link #id()} is internal (data.yml, map markers). Players see the {@link #number()} - the lowest free number among
 * the owner's loaders - or the name they gave it.
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
    private final int number;
    private boolean active;
    private String name;
    /** The owner wants a dummy player on this loader (it only sits there while the loader is active). */
    private boolean dummy;

    public Loader(int id, UUID owner, UUID worldId, String worldName, int x, int y, int z, long created,
                  int number, String name, boolean active) {
        this.id = id;
        this.owner = owner;
        this.worldId = worldId;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.created = created;
        this.number = number;
        this.name = name;
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

    /** Per-owner number, e.g. 3 for the owner's third loader (re-used after deleting). */
    public int number() {
        return number;
    }

    /** Name given by the player, or null for the default name ("Chunkloader 3"). */
    public String name() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    public boolean active() {
        return active;
    }

    void setActive(boolean active) {
        this.active = active;
    }

    /** Whether a dummy player is switched on for this loader (see {@link at.marcelr.mrchunks.dummy.Dummies}). */
    public boolean dummy() {
        return dummy;
    }

    void setDummy(boolean dummy) {
        this.dummy = dummy;
    }

    /** The same loader with another owner and number (accounts being linked, see {@link LoaderManager#link}). */
    Loader withOwner(UUID newOwner, int newNumber) {
        Loader copy = new Loader(id, newOwner, worldId, worldName, x, y, z, created, newNumber, name, active);
        copy.dummy = dummy;
        return copy;
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
