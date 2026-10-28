package at.marcelr.mrchunks;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.UUID;

/**
 * One placed chunk loader. Position, owner and number never change; {@link #active()} and {@link #name()} do.
 * <p>
 * {@link #id()} is internal (data.yml, map markers). Players see the {@link #number()} - the lowest free number among
 * the owner's loaders - or the name they gave it.
 * The loaded area is a rectangle of chunks: the loader's own chunk plus {@link #reach(Direction)} chunks in each of the
 * four directions. Its cost against the owner's chunk budget is its {@link #area()}.
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
    /** Block in the world chosen by the owner, null = item.material (see {@link LoaderManager#blockOf}). */
    private Material block;
    /** Chunks loaded beyond the loader's own chunk, indexed by {@link Direction#ordinal()}. */
    private final int[] reach = new int[4];

    /** The four directions of the area; north is -z, west is -x (as in Minecraft). */
    public enum Direction { NORTH, SOUTH, WEST, EAST }

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

    /** The block chosen for the world, null = item.material. */
    public Material block() {
        return block;
    }

    void setBlock(Material block) {
        this.block = block;
    }

    // ---------------------------------------------------------------- area

    public int reach(Direction direction) {
        return reach[direction.ordinal()];
    }

    /** Sets the reach in all four directions (only through {@link LoaderManager}, which moves the chunk tickets). */
    void setReach(int north, int south, int west, int east) {
        reach[Direction.NORTH.ordinal()] = north;
        reach[Direction.SOUTH.ordinal()] = south;
        reach[Direction.WEST.ordinal()] = west;
        reach[Direction.EAST.ordinal()] = east;
    }

    public int minChunkX() {
        return chunkX() - reach(Direction.WEST);
    }

    public int maxChunkX() {
        return chunkX() + reach(Direction.EAST);
    }

    public int minChunkZ() {
        return chunkZ() - reach(Direction.NORTH);
    }

    public int maxChunkZ() {
        return chunkZ() + reach(Direction.SOUTH);
    }

    /** Chunks from west to east. */
    public int width() {
        return reach(Direction.WEST) + reach(Direction.EAST) + 1;
    }

    /** Chunks from north to south. */
    public int length() {
        return reach(Direction.NORTH) + reach(Direction.SOUTH) + 1;
    }

    /** Number of chunks in the area - what the loader costs from the owner's budget while it is active. */
    public int area() {
        return width() * length();
    }

    /** Same reach in all four directions: a square with the loader block in its middle chunk. */
    public boolean centred() {
        int r = reach[0];
        return reach[1] == r && reach[2] == r && reach[3] == r;
    }

    public boolean covers(int cx, int cz) {
        return cx >= minChunkX() && cx <= maxChunkX() && cz >= minChunkZ() && cz <= maxChunkZ();
    }

    /** Whether the areas of the two loaders share a chunk (same world). */
    public boolean overlaps(Loader other) {
        return worldId.equals(other.worldId) && minChunkX() <= other.maxChunkX() && other.minChunkX() <= maxChunkX()
                && minChunkZ() <= other.maxChunkZ() && other.minChunkZ() <= maxChunkZ();
    }

    /** The same loader with another owner and number (accounts being linked, see {@link LoaderManager#link}). */
    Loader withOwner(UUID newOwner, int newNumber) {
        Loader copy = new Loader(id, newOwner, worldId, worldName, x, y, z, created, newNumber, name, active);
        copy.dummy = dummy;
        copy.block = block;
        System.arraycopy(reach, 0, copy.reach, 0, 4);
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
