package at.marcelr.mrchunks;

import at.marcelr.mrchunks.lag.LagLock;
import at.marcelr.mrchunks.lag.Metric;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Owns all chunk loaders, the chunk tickets that keep their areas loaded, per-player limits and bans,
 * and their persistence in {@code data.yml}. Main thread only.
 * <p>
 * Areas of several loaders may overlap, so every chunk has a reference count: the ticket is added when the
 * first active loader covers it and removed when the last one stops covering it.
 */
public final class LoaderManager {

    private final MRChunks plugin;
    private final File dataFile;

    private final Map<Integer, Loader> loaders = new LinkedHashMap<>();
    /** "world-uuid:x:y:z" -> loader, so block lookups (explosions, clicks) don't scan all loaders */
    private final Map<String, Loader> byPosition = new HashMap<>();
    /** world -> chunk key -> loaders whose block is in that chunk (block checks on chunk load) */
    private final Map<UUID, Map<Long, List<Loader>>> byChunk = new HashMap<>();
    private int nextId = 1;

    /** world -> chunk key -> number of active loaders covering that chunk */
    private final Map<UUID, Map<Long, Integer>> coverage = new HashMap<>();
    /** world -> chunks MRchunks force-loaded itself (load-method: forceload), so it never unforces others' chunks */
    private final Map<UUID, Set<Long>> forcedByUs = new HashMap<>();
    /** method the current tickets were created with (they must be removed the same way) */
    private Settings.LoadMethod appliedMethod;
    /** Server pause (see {@link at.marcelr.mrchunks.lag.ServerPause}): no tickets at all, loaders keep their state. */
    private boolean serverPaused;
    /** All loaders paused by the team (MRadmin web), until lifted; saved, so it also holds after a restart. */
    private Switch adminPause;
    /** Staged shutdown and hard cap switched off by the team (MRadmin web); saved. */
    private Switch monitoringOff;
    /** Loaders whose chunks are in {@link #coverage} right now (so uncovering never takes chunks of others). */
    private final Set<Integer> covered = new HashSet<>();
    /**
     * Staged shutdown (see {@link at.marcelr.mrchunks.lag.LoadShedder}): loader id -> since when it is disabled by lag.
     * Such a loader stays switched on but holds no chunks; not saved, a restart brings everything back.
     */
    private final Map<Integer, Long> shed = new LinkedHashMap<>();

    private final Map<UUID, Integer> budgetOverrides = new HashMap<>();
    private final Map<UUID, Integer> dummyOverrides = new HashMap<>();
    /**
     * Limits from permissions, remembered from the last time the player was online: permissions are unknown while
     * they are offline, but a linked second account may place loaders on their budget meanwhile.
     */
    private final Map<LimitKind, Map<UUID, Integer>> knownLimits = new java.util.EnumMap<>(LimitKind.class);
    /** Linked accounts: second account -> main account. Loaders, limits and invites belong to the main account. */
    private final Map<UUID, UUID> links = new LinkedHashMap<>();
    private final Map<UUID, Ban> bans = new HashMap<>();
    /** loader id -> invited player -> valid until (ms, Long.MAX_VALUE = permanent) */
    private final Map<Integer, Map<UUID, Long>> loaderMembers = new HashMap<>();
    /** owner -> invited player -> valid until; such an invite covers all loaders of the owner, also future ones */
    private final Map<UUID, Map<UUID, Long>> ownerMembers = new HashMap<>();
    /** loader id -> lag lock (only admins can lift it) */
    private final Map<Integer, LagLock> lagLocks = new HashMap<>();

    /** A switch the team flipped in the web interface: who and since when (ms). */
    public record Switch(String by, long since) {
    }

    /** Exclusion from chunk loaders. {@code until == Long.MAX_VALUE} means permanent. */
    public record Ban(long until, String reason, String by) {

        public boolean permanent() {
            return until == Long.MAX_VALUE;
        }

        public long remaining() {
            return permanent() ? Long.MAX_VALUE : until - System.currentTimeMillis();
        }
    }

    public LoaderManager(MRChunks plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
    }

    // ------------------------------------------------------------- lookups

    public Collection<Loader> all() {
        return List.copyOf(loaders.values());
    }

    public Loader byId(int id) {
        return loaders.get(id);
    }

    public Loader at(Block block) {
        return byPosition.get(positionKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ()));
    }

    private static String positionKey(UUID world, int x, int y, int z) {
        return world + ":" + x + ":" + y + ":" + z;
    }

    private void index(Loader loader) {
        byPosition.put(positionKey(loader.worldId(), loader.x(), loader.y(), loader.z()), loader);
        byChunk.computeIfAbsent(loader.worldId(), k -> new HashMap<>())
                .computeIfAbsent(Loader.chunkKey(loader.chunkX(), loader.chunkZ()), k -> new ArrayList<>()).add(loader);
    }

    private void unindex(Loader loader) {
        byPosition.remove(positionKey(loader.worldId(), loader.x(), loader.y(), loader.z()));
        Map<Long, List<Loader>> chunks = byChunk.get(loader.worldId());
        if (chunks != null) {
            List<Loader> list = chunks.get(Loader.chunkKey(loader.chunkX(), loader.chunkZ()));
            if (list != null && list.remove(loader) && list.isEmpty()) {
                chunks.remove(Loader.chunkKey(loader.chunkX(), loader.chunkZ()));
            }
        }
    }

    /** Loaders whose block lies in the given chunk. */
    public List<Loader> inChunk(World world, int cx, int cz) {
        Map<Long, List<Loader>> chunks = byChunk.get(world.getUID());
        List<Loader> list = chunks == null ? null : chunks.get(Loader.chunkKey(cx, cz));
        return list == null ? List.of() : List.copyOf(list);
    }

    /** The owner's loader with that number, or null. */
    public Loader byNumber(UUID owner, int number) {
        for (Loader loader : loaders.values()) {
            if (loader.owner().equals(owner) && loader.number() == number) {
                return loader;
            }
        }
        return null;
    }

    /** Lowest number the owner does not use yet (1, 2, 3 ... - a deleted number is used again). */
    private int nextNumber(UUID owner) {
        Set<Integer> used = new HashSet<>();
        for (Loader loader : loaders.values()) {
            if (loader.owner().equals(owner)) {
                used.add(loader.number());
            }
        }
        int number = 1;
        while (used.contains(number)) {
            number++;
        }
        return number;
    }

    /** Loaders whose area shares a chunk with the given rectangle of chunks (same world, {@code except} left out). */
    public List<Loader> overlapping(UUID world, int minX, int minZ, int maxX, int maxZ, Loader except) {
        List<Loader> result = new ArrayList<>();
        for (Loader loader : loaders.values()) {
            if (loader != except && loader.worldId().equals(world) && loader.minChunkX() <= maxX
                    && minX <= loader.maxChunkX() && loader.minChunkZ() <= maxZ && minZ <= loader.maxChunkZ()) {
                result.add(loader);
            }
        }
        return result;
    }

    /** Loaders whose area overlaps the area of {@code loader}. */
    public List<Loader> overlapping(Loader loader) {
        return overlapping(loader.worldId(), loader.minChunkX(), loader.minChunkZ(), loader.maxChunkX(),
                loader.maxChunkZ(), loader);
    }

    /** How many chunks of the loader's area are also covered by one of {@code others}. */
    public int sharedChunks(Loader loader, Collection<Loader> others) {
        int shared = 0;
        for (int x = loader.minChunkX(); x <= loader.maxChunkX(); x++) {
            for (int z = loader.minChunkZ(); z <= loader.maxChunkZ(); z++) {
                for (Loader other : others) {
                    if (other.covers(x, z)) {
                        shared++;
                        break;
                    }
                }
            }
        }
        return shared;
    }

    public boolean isLoaderBlock(Block block) {
        return at(block) != null;
    }

    public List<Loader> byOwner(UUID owner) {
        List<Loader> result = new ArrayList<>();
        for (Loader loader : loaders.values()) {
            if (loader.owner().equals(owner)) {
                result.add(loader);
            }
        }
        return result;
    }

    public int count(UUID owner) {
        return byOwner(owner).size();
    }

    /** All players that own at least one loader, have a ban or custom limit, or are linked to another account. */
    public Set<UUID> knownPlayers() {
        Set<UUID> players = new HashSet<>();
        loaders.values().forEach(l -> players.add(l.owner()));
        players.addAll(budgetOverrides.keySet());
        players.addAll(dummyOverrides.keySet());
        players.addAll(activeBans().keySet());
        players.addAll(links.keySet());
        players.addAll(links.values());
        return players;
    }

    /** Number of distinct chunks currently held loaded by MRchunks. */
    public int loadedChunkCount() {
        int count = 0;
        for (Map<Long, Integer> chunks : coverage.values()) {
            count += chunks.size();
        }
        return count;
    }

    public boolean isWorldAllowed(World world) {
        return !plugin.settings().loaders().disabledWorlds().contains(world.getName().toLowerCase(Locale.ROOT))
                // A world of another plugin (MRpvp pastes and clears arenas there, through MRadmin) - no loaders.
                && (plugin.mradmin() == null || !plugin.mradmin().reservedWorld(world));
    }

    // --------------------------------------------------------- create/delete

    /**
     * @param reach  the same reach in every direction (a centred square)
     * @param active false if the owner's budget has no room for it
     */
    public Loader create(UUID owner, Block block, int reach, boolean active) {
        Loader loader = new Loader(nextId++, owner, block.getWorld().getUID(), block.getWorld().getName(),
                block.getX(), block.getY(), block.getZ(), System.currentTimeMillis(), nextNumber(owner), null, active);
        loader.setReach(reach, reach, reach, reach);
        loaders.put(loader.id(), loader);
        index(loader);
        if (active) {
            cover(loader);
        }
        save();
        plugin.maps().refreshSoon();
        return loader;
    }

    /** Removes the loader and (if its world is loaded) its block. */
    public void delete(Loader loader) {
        if (loader.active()) {
            uncover(loader);
        }
        loaders.remove(loader.id());
        syncDummy(loader);
        unindex(loader);
        loaderMembers.remove(loader.id());
        lagLocks.remove(loader.id());
        shed.remove(loader.id());
        plugin.border().loaderRemoved(loader);
        World world = loader.world();
        if (world != null) {
            Block block = world.getBlockAt(loader.x(), loader.y(), loader.z());
            if (block.getType() == blockOf(loader) || loaderBlock(block.getType())) {
                block.setType(Material.AIR, false);
            }
        }
        save();
        plugin.maps().refreshSoon();
    }

    /** @return number of deleted loaders */
    public int deleteAll(UUID owner) {
        List<Loader> owned = byOwner(owner);
        owned.forEach(this::delete);
        return owned.size();
    }

    public void setActive(Loader loader, boolean active) {
        if (loader.active() == active) {
            return;
        }
        loader.setActive(active);
        if (active) {
            cover(loader);
        } else {
            uncover(loader);
        }
        save();
        syncDummy(loader);
        plugin.maps().refreshSoon();
        plugin.border().loaderChanged(loader);
    }

    /**
     * Changes the area of the loader; an active loader moves its chunk tickets right away. Checks (budget, maximum,
     * overlap, dummy) are up to the caller ({@link AdminActions#resize}).
     */
    public void resize(Loader loader, int north, int south, int west, int east) {
        if (loader.active()) {
            uncover(loader);
        }
        loader.setReach(north, south, west, east);
        if (loader.active()) {
            cover(loader);
        }
        save();
        syncDummy(loader);
        plugin.maps().refreshSoon();
        plugin.border().loaderChanged(loader);
        plugin.lag().areaChanged();
    }

    // -------------------------------------------------------------- blocks

    /** The block the loader stands as in the world: the owner's choice, otherwise item.material. */
    public Material blockOf(Loader loader) {
        return loader.block() != null ? loader.block() : plugin.settings().item().material();
    }

    /** Changes the loader's block and swaps it in the world right away (if the world is loaded). */
    public void setBlock(Loader loader, Material material) {
        loader.setBlock(material == plugin.settings().item().material() ? null : material);
        World world = loader.world();
        if (world != null) {
            world.getBlockAt(loader.x(), loader.y(), loader.z()).setType(blockOf(loader), false);
        }
        save();
    }

    /** Whether a loader may stand as this block: item.material or one of item.block-choices. */
    private boolean loaderBlock(Material material) {
        Settings.ItemSettings item = plugin.settings().item();
        return material == item.material() || item.blockChoices().contains(material);
    }

    // ------------------------------------------------------------- dummies

    /** Switches the dummy player of a loader on or off (it sits there while the loader is active). */
    public void setDummy(Loader loader, boolean dummy) {
        if (loader.dummy() == dummy) {
            return;
        }
        loader.setDummy(dummy);
        save();
        syncDummy(loader);
    }

    /** How many of the owner's loaders have their dummy switched on. */
    public int dummyCount(UUID owner) {
        int count = 0;
        for (Loader loader : loaders.values()) {
            if (loader.dummy() && loader.owner().equals(owner)) {
                count++;
            }
        }
        return count;
    }

    /** True if the player (or the main account they are linked to) may switch on one more dummy. */
    public boolean canAddDummy(OfflinePlayer player) {
        return dummyCount(main(player.getUniqueId())) < limit(player, LimitKind.DUMMY).value();
    }

    /** Spawns or removes the loader's dummy so it matches the loader (active, not paused, world loaded ...). */
    private void syncDummy(Loader loader) {
        if (plugin.dummies() != null) {
            plugin.dummies().sync(loader);
        }
    }

    private void syncDummies() {
        if (plugin.dummies() != null) {
            plugin.dummies().syncAll();
        }
    }

    /** Whether the loader exists (was not deleted meanwhile). */
    public boolean exists(Loader loader) {
        return loaders.get(loader.id()) == loader;
    }

    // ----------------------------------------------------------- lag locks

    /** The lag lock of a loader, or null if it is not locked. */
    public LagLock lagLock(Loader loader) {
        return lagLocks.get(loader.id());
    }

    public boolean lagLocked(Loader loader) {
        return lagLocks.containsKey(loader.id());
    }

    /** All lag locks (loader id -> lock). */
    public Map<Integer, LagLock> lagLocks() {
        return Map.copyOf(lagLocks);
    }

    /** Locks the loader and switches it off; it stays off until {@link #lagUnlock}. */
    public void lagLock(Loader loader, LagLock lock) {
        lagLocks.put(loader.id(), lock);
        shed.remove(loader.id());
        if (loader.active()) {
            setActive(loader, false);
        } else {
            save();
        }
    }

    /** Replaces the stored lock (e.g. once the owner has been told). */
    public void updateLagLock(Loader loader, LagLock lock) {
        if (lagLocks.containsKey(loader.id())) {
            lagLocks.put(loader.id(), lock);
            save();
        }
    }

    /** Lifts the lag lock; the loader stays off until someone switches it on. @return whether it was locked */
    public boolean lagUnlock(Loader loader) {
        boolean removed = lagLocks.remove(loader.id()) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    /** Sets or (with null) removes the name the player gave the loader. */
    public void rename(Loader loader, String name) {
        loader.setName(name);
        save();
        plugin.maps().refreshSoon();
    }

    // ---------------------------------------------------- staged shutdown

    /** Whether the loader is disabled by lag right now (switched on, but holding no chunks). */
    public boolean shed(Loader loader) {
        return shed.containsKey(loader.id());
    }

    /** Since when the loader is disabled by lag (ms), or 0. */
    public long shedSince(Loader loader) {
        return shed.getOrDefault(loader.id(), 0L);
    }

    /** Whether the loader holds its chunks right now (active, not disabled by lag, no server pause, world loaded). */
    public boolean loading(Loader loader) {
        return covered.contains(loader.id());
    }

    /** Ids of the loaders disabled by lag, oldest first. */
    public List<Integer> shedIds() {
        return List.copyOf(shed.keySet());
    }

    /** Disables the loader by lag: it releases its chunks and its dummy, but stays switched on. */
    public void shed(Loader loader, long since) {
        if (shed.putIfAbsent(loader.id(), since) != null) {
            return;
        }
        uncover(loader);
        syncDummy(loader);
        plugin.maps().refreshSoon();
        plugin.border().loaderChanged(loader);
    }

    /** Ends the shutdown of the loader; if it is (still) switched on, it loads its chunks again. */
    public void unshed(Loader loader) {
        if (shed.remove(loader.id()) == null) {
            return;
        }
        if (loader.active()) {
            cover(loader);
            syncDummy(loader);
        }
        plugin.maps().refreshSoon();
        plugin.border().loaderChanged(loader);
    }

    /** Removes the shutdown of a loader that no longer exists or is switched off (no chunks to load). */
    public void forgetShed(int id) {
        shed.remove(id);
    }

    // ------------------------------------------------------------- tickets

    /** Whether no loader holds chunks right now: server pause or paused by the team. */
    public boolean paused() {
        return serverPaused || adminPause != null;
    }

    /** Only the automatic server pause (TPS). */
    public boolean serverPaused() {
        return serverPaused;
    }

    /** Who paused all loaders in the web interface, or null. */
    public Switch adminPause() {
        return adminPause;
    }

    /**
     * Pausing releases every ticket, resuming adds them again for all active loaders. Loaders keep their on/off
     * state; loaders switched on during the pause start loading when it ends. Server pause and the pause by the team
     * are separate: loaders load again only when neither holds.
     */
    public void setPaused(boolean paused) {
        boolean before = paused();
        serverPaused = paused;
        pauseChanged(before);
    }

    /** Pauses all loaders for the team ({@code by} = who) or, with null, lifts that pause. */
    public void setAdminPause(String by) {
        boolean before = paused();
        adminPause = by == null ? null : new Switch(by, System.currentTimeMillis());
        save();
        pauseChanged(before);
    }

    private void pauseChanged(boolean before) {
        boolean now = paused();
        if (before == now) {
            return;
        }
        if (now) {
            releaseAll();
            syncDummies();
        } else {
            applyAll();
        }
        plugin.maps().refreshSoon();
    }

    /** Who switched the staged shutdown off in the web interface, or null (on as configured). */
    public Switch monitoringOff() {
        return monitoringOff;
    }

    public void setMonitoringOff(String by) {
        monitoringOff = by == null ? null : new Switch(by, System.currentTimeMillis());
        save();
    }

    /** Adds tickets for all active loaders of all loaded worlds (on enable / after reload). */
    public void applyAll() {
        appliedMethod = plugin.settings().loaders().loadMethod();
        for (World world : Bukkit.getWorlds()) {
            applyWorld(world);
        }
        // Also removes dummies that should no longer be there (e.g. dummies switched off in the config).
        syncDummies();
    }

    /** Adds tickets (and dummies) for the active loaders of one world (also used when a world loads later). */
    public void applyWorld(World world) {
        for (Loader loader : List.copyOf(loaders.values())) {
            if (loader.active() && loader.worldId().equals(world.getUID())) {
                cover(loader);
                syncDummy(loader);
            }
        }
    }

    /** Removes every ticket MRchunks holds (on disable / before re-applying with new settings). */
    public void releaseAll() {
        for (Map.Entry<UUID, Map<Long, Integer>> entry : coverage.entrySet()) {
            World world = Bukkit.getWorld(entry.getKey());
            if (world == null) {
                continue;
            }
            for (long key : entry.getValue().keySet()) {
                removeTicket(world, (int) (key >> 32), (int) key);
            }
        }
        coverage.clear();
        forcedByUs.clear();
        covered.clear();
    }

    private void cover(Loader loader) {
        World world = loader.world();
        if (world == null || paused() || shed.containsKey(loader.id())) {
            return; // applied later by applyWorld when the world loads, by applyAll when the pause ends, or unshed
        }
        if (!covered.add(loader.id())) {
            return;
        }
        Map<Long, Integer> chunks = coverage.computeIfAbsent(world.getUID(), k -> new HashMap<>());
        for (int cx = loader.minChunkX(); cx <= loader.maxChunkX(); cx++) {
            for (int cz = loader.minChunkZ(); cz <= loader.maxChunkZ(); cz++) {
                if (chunks.merge(Loader.chunkKey(cx, cz), 1, Integer::sum) == 1) {
                    addTicket(world, cx, cz);
                }
            }
        }
    }

    private void uncover(Loader loader) {
        if (!covered.remove(loader.id())) {
            return;
        }
        World world = loader.world();
        Map<Long, Integer> chunks = world == null ? null : coverage.get(world.getUID());
        if (chunks == null) {
            return;
        }
        for (int cx = loader.minChunkX(); cx <= loader.maxChunkX(); cx++) {
            for (int cz = loader.minChunkZ(); cz <= loader.maxChunkZ(); cz++) {
                long key = Loader.chunkKey(cx, cz);
                Integer count = chunks.get(key);
                if (count == null) {
                    continue;
                }
                if (count <= 1) {
                    chunks.remove(key);
                    removeTicket(world, cx, cz);
                } else {
                    chunks.put(key, count - 1);
                }
            }
        }
    }

    private void addTicket(World world, int cx, int cz) {
        if (appliedMethod == Settings.LoadMethod.FORCELOAD) {
            if (!world.isChunkForceLoaded(cx, cz)) {
                world.setChunkForceLoaded(cx, cz, true);
                forcedByUs.computeIfAbsent(world.getUID(), k -> new HashSet<>()).add(Loader.chunkKey(cx, cz));
            }
        } else {
            world.addPluginChunkTicket(cx, cz, plugin);
        }
    }

    private void removeTicket(World world, int cx, int cz) {
        if (appliedMethod == Settings.LoadMethod.FORCELOAD) {
            Set<Long> ours = forcedByUs.get(world.getUID());
            if (ours != null && ours.remove(Loader.chunkKey(cx, cz))) {
                world.setChunkForceLoaded(cx, cz, false);
            }
        } else {
            world.removePluginChunkTicket(cx, cz, plugin);
        }
    }

    // ---------------------------------------------------------- validation

    /**
     * Checks that every loader in a loaded chunk still has its block (WorldEdit, other plugins ...).
     * @return loaders whose block was missing, with true = restored, false = deleted
     */
    public Map<Loader, Boolean> validate() {
        return validate(List.copyOf(loaders.values()));
    }

    /** Like {@link #validate()} for some loaders only (e.g. those in a chunk that just loaded). */
    public Map<Loader, Boolean> validate(Collection<Loader> candidates) {
        Map<Loader, Boolean> result = new LinkedHashMap<>();
        boolean restore = plugin.settings().loaders().restoreMissingBlocks();
        for (Loader loader : candidates) {
            World world = loader.world();
            if (!loaders.containsKey(loader.id()) || world == null
                    || !world.isChunkLoaded(loader.chunkX(), loader.chunkZ())) {
                continue;
            }
            Block block = world.getBlockAt(loader.x(), loader.y(), loader.z());
            Material material = blockOf(loader);
            if (block.getType() == material) {
                continue;
            }
            if (loaderBlock(block.getType())) {
                // Still a loader block, just not the chosen one (choice made while the world was not loaded,
                // item.material changed ...): swap it quietly.
                block.setType(material, false);
                continue;
            }
            if (restore) {
                block.setType(material, false);
                result.put(loader, true);
            } else {
                delete(loader);
                result.put(loader, false);
            }
        }
        return result;
    }

    // -------------------------------------------------------------- limits

    /**
     * The caps per player that admins and permissions can raise: the chunk budget (chunks all active loaders together
     * may keep loaded) and on how many loaders a dummy player may sit. Linked accounts share the caps of their main
     * account. How many loaders may be placed is the same for everyone (loaders.max-loaders).
     */
    public enum LimitKind {
        BUDGET("mrchunks.budget.", "mrchunks.budget.unlimited"),
        DUMMY("mrchunks.dummies.", "mrchunks.dummies.unlimited");

        final String permPrefix;
        final String permUnlimited;

        LimitKind(String permPrefix, String permUnlimited) {
            this.permPrefix = permPrefix;
            this.permUnlimited = permUnlimited;
        }
    }

    /** @return the admin-set limit, or null if the player uses the default/permission limit */
    public Integer limitOverride(UUID player, LimitKind kind) {
        return overrides(kind).get(player);
    }

    public void setLimitOverride(UUID player, LimitKind kind, Integer limit) {
        if (limit == null) {
            overrides(kind).remove(player);
        } else {
            overrides(kind).put(player, Math.max(0, limit));
        }
        save();
    }

    private Map<UUID, Integer> overrides(LimitKind kind) {
        return switch (kind) {
            case BUDGET -> budgetOverrides;
            case DUMMY -> dummyOverrides;
        };
    }

    private int defaultLimit(LimitKind kind) {
        return switch (kind) {
            case BUDGET -> plugin.settings().loaders().defaultBudget();
            case DUMMY -> plugin.settings().dummies().defaultLimit();
        };
    }

    /** Where the effective limit comes from (for display). */
    public enum LimitSource { DEFAULT, PERMISSION, CUSTOM, UNLIMITED }

    public record Limit(int value, LimitSource source) {
    }

    /** How many loaders a player may place - the same for everyone. */
    public int maxLoaders() {
        return plugin.settings().loaders().maxLoaders();
    }

    /** How many chunks the player's active loaders may keep loaded together. */
    public Limit budget(OfflinePlayer player) {
        return limit(player, LimitKind.BUDGET);
    }

    /**
     * Effective limit of the player's main account (the player themself if not linked): an admin override wins;
     * otherwise the highest {@code <prefix><n>} permission, but never less than the config default. Permissions are
     * only known while the account is online - offline, the value from its last time online is used.
     */
    public Limit limit(OfflinePlayer player, LimitKind kind) {
        UUID main = main(player.getUniqueId());
        Integer override = overrides(kind).get(main);
        if (override != null) {
            return new Limit(override, LimitSource.CUSTOM);
        }
        int base = defaultLimit(kind);
        Player online = main.equals(player.getUniqueId()) ? player.getPlayer() : Bukkit.getPlayer(main);
        Map<UUID, Integer> known = knownLimits.computeIfAbsent(kind, k -> new HashMap<>());
        if (online == null) {
            Integer remembered = known.get(main);
            if (remembered != null && remembered == Integer.MAX_VALUE) {
                return new Limit(Integer.MAX_VALUE, LimitSource.UNLIMITED);
            }
            return remembered != null && remembered > base ? new Limit(remembered, LimitSource.PERMISSION)
                    : new Limit(base, LimitSource.DEFAULT);
        }
        Limit limit = permissionLimit(online, kind, base);
        Integer remember = limit.source() == LimitSource.DEFAULT ? null : limit.value();
        if (!java.util.Objects.equals(known.get(main), remember)) {
            if (remember == null) {
                known.remove(main);
            } else {
                known.put(main, remember);
            }
            save();
        }
        return limit;
    }

    private static Limit permissionLimit(Player online, LimitKind kind, int base) {
        if (online.hasPermission(kind.permUnlimited)) {
            return new Limit(Integer.MAX_VALUE, LimitSource.UNLIMITED);
        }
        int best = -1;
        for (PermissionAttachmentInfo info : online.getEffectivePermissions()) {
            String perm = info.getPermission();
            if (info.getValue() && perm.startsWith(kind.permPrefix)) {
                try {
                    best = Math.max(best, Integer.parseInt(perm.substring(kind.permPrefix.length())));
                } catch (NumberFormatException ignored) {
                    // e.g. "...unlimited" or a typo
                }
            }
        }
        return best > base ? new Limit(best, LimitSource.PERMISSION) : new Limit(base, LimitSource.DEFAULT);
    }

    public int activeCount(UUID owner) {
        int count = 0;
        for (Loader loader : loaders.values()) {
            if (loader.active() && loader.owner().equals(owner)) {
                count++;
            }
        }
        return count;
    }

    /** Chunks the owner's active loaders cost together (overlapping chunks count for every loader). */
    public int usedBudget(UUID owner) {
        int used = 0;
        for (Loader loader : loaders.values()) {
            if (loader.active() && loader.owner().equals(owner)) {
                used += loader.area();
            }
        }
        return used;
    }

    /** Chunks of the player's budget (of their main account, if linked) not used by active loaders yet. */
    public long freeBudget(OfflinePlayer player) {
        int budget = budget(player).value();
        return budget == Integer.MAX_VALUE ? Long.MAX_VALUE : (long) budget - usedBudget(main(player.getUniqueId()));
    }

    /** True if the player's budget has room for {@code chunks} more chunks. */
    public boolean fitsBudget(OfflinePlayer player, long chunks) {
        return chunks <= freeBudget(player);
    }

    /**
     * Switches off the newest active loaders until the player is within their budget again.
     * @return the deactivated loaders
     */
    public List<Loader> enforceBudget(OfflinePlayer player) {
        int budget = budget(player).value();
        List<Loader> active = new ArrayList<>();
        int used = 0;
        for (Loader loader : byOwner(main(player.getUniqueId()))) {
            if (loader.active()) {
                active.add(loader);
                used += loader.area();
            }
        }
        List<Loader> deactivated = new ArrayList<>();
        // byOwner keeps creation order: keep the oldest ones running
        for (int i = active.size() - 1; i >= 0 && used > budget; i--) {
            setActive(active.get(i), false);
            deactivated.add(active.get(i));
            used -= active.get(i).area();
        }
        return deactivated;
    }

    /**
     * Switches off the newest dummies until the player is within their dummy limit.
     * @return the loaders whose dummy was switched off
     */
    public List<Loader> enforceDummyLimit(OfflinePlayer player) {
        int limit = limit(player, LimitKind.DUMMY).value();
        List<Loader> withDummy = new ArrayList<>();
        for (Loader loader : byOwner(main(player.getUniqueId()))) {
            if (loader.dummy()) {
                withDummy.add(loader);
            }
        }
        List<Loader> removed = new ArrayList<>();
        for (int i = withDummy.size() - 1; i >= 0 && withDummy.size() - removed.size() > limit; i--) {
            setDummy(withDummy.get(i), false);
            removed.add(withDummy.get(i));
        }
        return removed;
    }

    // ---------------------------------------------------------- linked accounts

    /** The main account a second account is linked to; for everyone else the player themself. */
    public UUID main(UUID player) {
        return links.getOrDefault(player, player);
    }

    /** Whether the player is a main or second account of a link. */
    public boolean isLinked(UUID player) {
        return links.containsKey(player) || links.containsValue(player);
    }

    /** Second accounts linked to this main account, in the order they were linked. */
    public List<UUID> secondaries(UUID main) {
        List<UUID> result = new ArrayList<>();
        links.forEach((secondary, owner) -> {
            if (owner.equals(main)) {
                result.add(secondary);
            }
        });
        return result;
    }

    /** The main account and all its second accounts (just the player if not linked). */
    public List<UUID> group(UUID player) {
        UUID main = main(player);
        List<UUID> group = new ArrayList<>();
        group.add(main);
        group.addAll(secondaries(main));
        return group;
    }

    public boolean sameGroup(UUID a, UUID b) {
        return main(a).equals(main(b));
    }

    /** Link pairs, second account -> main account. */
    public Map<UUID, UUID> links() {
        return Map.copyOf(links);
    }

    /**
     * Links {@code secondary} to {@code main}: both share the loaders, limits and invites of the main account from
     * now on. Loaders of the second account go over to the main account (with new numbers), so do its invites for all
     * loaders; invites between the two accounts are dropped.
     * @return number of loaders taken over
     */
    public int link(UUID main, UUID secondary) {
        links.put(secondary, main);
        int moved = 0;
        for (Loader loader : List.copyOf(loaders.values())) {
            if (loader.owner().equals(secondary)) {
                Loader taken = loader.withOwner(main, nextNumber(main));
                unindex(loader);
                loaders.put(taken.id(), taken);
                index(taken);
                moved++;
            }
        }
        Map<UUID, Long> invites = ownerMembers.remove(secondary);
        if (invites != null) {
            Map<UUID, Long> target = ownerMembers.computeIfAbsent(main, k -> new HashMap<>());
            invites.forEach((member, until) -> target.merge(member, until, Math::max));
        }
        Set<UUID> group = Set.copyOf(group(main));
        Map<UUID, Long> ownerInvites = ownerMembers.get(main);
        if (ownerInvites != null) {
            ownerInvites.keySet().removeIf(group::contains);
        }
        for (Loader loader : loaders.values()) {
            Map<UUID, Long> members = loaderMembers.get(loader.id());
            if (members != null && loader.owner().equals(main)) {
                members.keySet().removeIf(group::contains);
            }
        }
        save();
        if (moved > 0) {
            // The dummies of the taken loaders now belong to (and look like) the main account.
            syncDummies();
            plugin.maps().refreshSoon();
        }
        return moved;
    }

    /** Removes the link of a second account; the loaders stay with the main account. @return whether it was linked */
    public boolean unlink(UUID secondary) {
        boolean had = links.remove(secondary) != null;
        if (had) {
            save();
        }
        return had;
    }

    // ---------------------------------------------------------------- bans

    /** @param durationMillis duration, or {@link Settings#PERMANENT} */
    public void ban(UUID player, long durationMillis, String reason, String by) {
        long until = durationMillis == Settings.PERMANENT ? Long.MAX_VALUE : System.currentTimeMillis() + durationMillis;
        bans.put(player, new Ban(until, reason, by));
        save();
    }

    public boolean unban(UUID player) {
        boolean had = ban(player) != null;
        bans.remove(player);
        save();
        return had;
    }

    /** @return the active ban, or null */
    public Ban ban(UUID player) {
        Ban ban = bans.get(player);
        if (ban != null && ban.remaining() <= 0) {
            bans.remove(player);
            return null;
        }
        return ban;
    }

    /** The ban of the player or of an account linked with them (a ban applies to the whole group), or null. */
    public Ban groupBan(UUID player) {
        for (UUID account : group(player)) {
            Ban ban = ban(account);
            if (ban != null) {
                return ban;
            }
        }
        return null;
    }

    public Map<UUID, Ban> activeBans() {
        Map<UUID, Ban> result = new LinkedHashMap<>();
        for (UUID uuid : List.copyOf(bans.keySet())) {
            Ban ban = ban(uuid);
            if (ban != null) {
                result.put(uuid, ban);
            }
        }
        return result;
    }

    // ------------------------------------------------------------- invites

    /**
     * Invites {@code member} to switch loaders on and off: one loader, or with {@code loader == null} all loaders of
     * {@code owner}. Invites never count towards anyone's limits.
     * @param durationMillis duration, or {@link Settings#PERMANENT}
     */
    public void invite(UUID owner, Loader loader, UUID member, long durationMillis) {
        long until = durationMillis == Settings.PERMANENT ? Long.MAX_VALUE : System.currentTimeMillis() + durationMillis;
        membersOf(owner, loader, true).put(member, until);
        save();
    }

    /** @return true if the player was invited */
    public boolean uninvite(UUID owner, Loader loader, UUID member) {
        Map<UUID, Long> members = membersOf(owner, loader, false);
        boolean had = members != null && members.remove(member) != null;
        if (had) {
            save();
        }
        return had;
    }

    /** Current invites (not expired) of one loader, or with {@code loader == null} the owner-wide ones. */
    public Map<UUID, Long> members(UUID owner, Loader loader) {
        Map<UUID, Long> members = membersOf(owner, loader, false);
        if (members == null) {
            return Map.of();
        }
        long now = System.currentTimeMillis();
        members.values().removeIf(until -> until <= now);
        return Map.copyOf(members);
    }

    private Map<UUID, Long> membersOf(UUID owner, Loader loader, boolean create) {
        if (loader == null) {
            return create ? ownerMembers.computeIfAbsent(owner, k -> new HashMap<>()) : ownerMembers.get(owner);
        }
        return create ? loaderMembers.computeIfAbsent(loader.id(), k -> new HashMap<>()) : loaderMembers.get(loader.id());
    }

    /** Whether {@code player} is invited to this loader (directly or for all loaders of its owner). */
    public boolean isMember(UUID player, Loader loader) {
        long now = System.currentTimeMillis();
        Map<UUID, Long> direct = loaderMembers.get(loader.id());
        Map<UUID, Long> owner = ownerMembers.get(loader.owner());
        return (direct != null && direct.getOrDefault(player, 0L) > now) || (owner != null && owner.getOrDefault(player, 0L) > now);
    }

    /** Loaders of other players that {@code player} may switch on and off (not those of their own linked accounts). */
    public List<Loader> sharedWith(UUID player) {
        List<Loader> result = new ArrayList<>();
        UUID main = main(player);
        for (Loader loader : loaders.values()) {
            if (!loader.owner().equals(main) && isMember(player, loader)) {
                result.add(loader);
            }
        }
        return result;
    }

    // --------------------------------------------------------- persistence

    public void load() {
        loaders.clear();
        byPosition.clear();
        byChunk.clear();
        budgetOverrides.clear();
        dummyOverrides.clear();
        knownLimits.clear();
        links.clear();
        bans.clear();
        loaderMembers.clear();
        ownerMembers.clear();
        lagLocks.clear();
        adminPause = null;
        monitoringOff = null;
        nextId = 1;
        if (!dataFile.exists()) {
            return;
        }
        YamlConfiguration data = YamlConfiguration.loadConfiguration(dataFile);
        nextId = Math.max(1, data.getInt("next-id", 1));
        adminPause = readSwitch(data, "admin-pause");
        monitoringOff = readSwitch(data, "monitoring-off");
        // Loaders from before per-loader sizes keep the size they had (loaders.radius, carried over as default-reach).
        int defaultReach = plugin.settings().loaders().defaultReach();
        boolean migrated = false;

        ConfigurationSection loaderSection = data.getConfigurationSection("loaders");
        if (loaderSection != null) {
            for (String key : loaderSection.getKeys(false)) {
                ConfigurationSection s = loaderSection.getConfigurationSection(key);
                UUID owner = parseUuid(s == null ? null : s.getString("owner"));
                String rawWorld = s == null ? null : s.getString("world");
                UUID world = parseUuid(rawWorld);
                if (world == null && rawWorld != null && Bukkit.getWorld(rawWorld) != null) {
                    // Hand-edited data.yml may name the world; it is stored as UUID on the next save.
                    world = Bukkit.getWorld(rawWorld).getUID();
                }
                if (owner == null || world == null) {
                    plugin.getLogger().warning("Chunk loader #" + key + " in data.yml is incomplete and is skipped.");
                    continue;
                }
                int id = Integer.parseInt(key);
                String name = s.getString("name");
                Loader loader = new Loader(id, owner, world, s.getString("world-name", "?"),
                        s.getInt("x"), s.getInt("y"), s.getInt("z"), s.getLong("created"),
                        s.getInt("number", 0), name == null || name.isBlank() ? null : name,
                        s.getBoolean("active", true));
                loader.setDummy(s.getBoolean("dummy", false));
                String block = s.getString("block");
                loader.setBlock(block == null ? null : Material.matchMaterial(block));
                if (!s.contains("reach")) {
                    migrated = true;
                }
                loader.setReach(reach(s, "north", defaultReach), reach(s, "south", defaultReach),
                        reach(s, "west", defaultReach), reach(s, "east", defaultReach));
                loaders.put(id, loader);
                nextId = Math.max(nextId, id + 1);
            }
        }
        readLimits(data.getConfigurationSection("budgets"), budgetOverrides);
        if (!data.contains("budgets") && data.contains("active-limits")) {
            // Before the budget: individual active limits (loaders at once) become as many default-size loaders' worth.
            Map<UUID, Integer> active = new HashMap<>();
            readLimits(data.getConfigurationSection("active-limits"), active);
            int area = plugin.settings().loaders().defaultArea();
            active.forEach((uuid, limit) -> budgetOverrides.put(uuid, (int) Math.min(Integer.MAX_VALUE - 1L, (long) limit * area)));
            migrated = true;
        }
        readLimits(data.getConfigurationSection("dummy-limits"), dummyOverrides);
        for (LimitKind kind : LimitKind.values()) {
            Map<UUID, Integer> known = new HashMap<>();
            readLimits(data.getConfigurationSection("known-limits." + kind.name().toLowerCase(Locale.ROOT)), known);
            knownLimits.put(kind, known);
        }
        ConfigurationSection linkSection = data.getConfigurationSection("links");
        if (linkSection != null) {
            for (String key : linkSection.getKeys(false)) {
                UUID secondary = parseUuid(key);
                UUID main = parseUuid(linkSection.getString(key));
                // No chains: a main account is never a second account itself.
                if (secondary != null && main != null && !secondary.equals(main)) {
                    links.put(secondary, main);
                }
            }
            links.keySet().removeIf(links::containsValue);
        }
        lagLocks.clear();
        ConfigurationSection lockSection = data.getConfigurationSection("lag-locks");
        if (lockSection != null) {
            for (String key : lockSection.getKeys(false)) {
                ConfigurationSection s = lockSection.getConfigurationSection(key);
                Metric metric = s == null ? null : Metric.byKey(s.getString("metric", ""));
                try {
                    if (metric != null) {
                        lagLocks.put(Integer.parseInt(key), new LagLock(s.getLong("since"), metric,
                                s.getDouble("value"), s.getDouble("limit"), s.getBoolean("notified"), s.getInt("shutdowns")));
                    }
                } catch (NumberFormatException ignored) {
                    // broken entry
                }
            }
        }
        ConfigurationSection banSection = data.getConfigurationSection("bans");
        if (banSection != null) {
            for (String key : banSection.getKeys(false)) {
                UUID uuid = parseUuid(key);
                if (uuid != null) {
                    bans.put(uuid, new Ban(banSection.getLong(key + ".until"),
                            banSection.getString(key + ".reason", ""), banSection.getString(key + ".by", "?")));
                }
            }
        }
        readMembers(data.getConfigurationSection("members.loaders"), true);
        readMembers(data.getConfigurationSection("members.owners"), false);
        // Only now (limits and bans are read), because it may save.
        assignMissingNumbers();
        loaders.values().forEach(this::index);
        if (migrated) {
            // Store the sizes right away, so a later change of loaders.default-reach does not resize these loaders.
            save();
            plugin.getLogger().info("data.yml: chunk loaders got their own size and the chunk budget (from the old radius and active limits).");
        }
    }

    private static int reach(ConfigurationSection loader, String direction, int fallback) {
        return Math.clamp(loader.getInt("reach." + direction, fallback), 0, 32);
    }

    private static void readLimits(ConfigurationSection section, Map<UUID, Integer> into) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            UUID uuid = parseUuid(key);
            if (uuid != null) {
                into.put(uuid, section.getInt(key));
            }
        }
    }

    private void readMembers(ConfigurationSection section, boolean perLoader) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection entries = section.getConfigurationSection(key);
            if (entries == null) {
                continue;
            }
            Map<UUID, Long> members = new HashMap<>();
            for (String member : entries.getKeys(false)) {
                UUID uuid = parseUuid(member);
                if (uuid != null) {
                    members.put(uuid, entries.getLong(member));
                }
            }
            if (perLoader) {
                try {
                    loaderMembers.put(Integer.parseInt(key), members);
                } catch (NumberFormatException ignored) {
                    // hand-edited garbage
                }
            } else {
                UUID owner = parseUuid(key);
                if (owner != null) {
                    ownerMembers.put(owner, members);
                }
            }
        }
    }

    /**
     * Older data.yml files have no per-owner numbers (and a hand-edited one may contain duplicates):
     * number those loaders in creation order with the lowest free numbers.
     */
    private void assignMissingNumbers() {
        Map<UUID, Set<Integer>> used = new HashMap<>();
        List<Loader> missing = new ArrayList<>();
        for (Loader loader : loaders.values()) {
            if (loader.number() > 0 && used.computeIfAbsent(loader.owner(), k -> new HashSet<>()).add(loader.number())) {
                continue;
            }
            missing.add(loader);
        }
        if (missing.isEmpty()) {
            return;
        }
        missing.sort(java.util.Comparator.comparingLong(Loader::created).thenComparingInt(Loader::id));
        for (Loader old : missing) {
            Set<Integer> taken = used.computeIfAbsent(old.owner(), k -> new HashSet<>());
            int number = 1;
            while (taken.contains(number)) {
                number++;
            }
            taken.add(number);
            loaders.put(old.id(), old.withOwner(old.owner(), number));
        }
        save();
    }

    public void save() {
        bans.values().removeIf(ban -> ban.remaining() <= 0);
        YamlConfiguration data = new YamlConfiguration();
        data.set("next-id", nextId);
        for (Loader loader : loaders.values()) {
            String p = "loaders." + loader.id() + ".";
            data.set(p + "owner", loader.owner().toString());
            data.set(p + "world", loader.worldId().toString());
            data.set(p + "world-name", loader.worldName());
            data.set(p + "x", loader.x());
            data.set(p + "y", loader.y());
            data.set(p + "z", loader.z());
            data.set(p + "created", loader.created());
            data.set(p + "number", loader.number());
            data.set(p + "name", loader.name());
            data.set(p + "active", loader.active());
            if (loader.dummy()) {
                data.set(p + "dummy", true);
            }
            if (loader.block() != null) {
                data.set(p + "block", loader.block().name());
            }
            for (Loader.Direction direction : Loader.Direction.values()) {
                data.set(p + "reach." + direction.name().toLowerCase(Locale.ROOT), loader.reach(direction));
            }
        }
        // Always written (also empty), so the old active limits are only converted once.
        data.createSection("budgets");
        budgetOverrides.forEach((uuid, limit) -> data.set("budgets." + uuid, limit));
        dummyOverrides.forEach((uuid, limit) -> data.set("dummy-limits." + uuid, limit));
        knownLimits.forEach((kind, known) -> known.forEach((uuid, limit) ->
                data.set("known-limits." + kind.name().toLowerCase(Locale.ROOT) + "." + uuid, limit)));
        links.forEach((secondary, main) -> data.set("links." + secondary, main.toString()));
        lagLocks.keySet().removeIf(id -> !loaders.containsKey(id));
        lagLocks.forEach((id, lock) -> {
            String p = "lag-locks." + id + ".";
            data.set(p + "since", lock.since());
            data.set(p + "metric", lock.metric().key());
            data.set(p + "value", lock.value());
            data.set(p + "limit", lock.limit());
            data.set(p + "notified", lock.notified());
            if (lock.shutdowns() > 0) {
                data.set(p + "shutdowns", lock.shutdowns());
            }
        });
        bans.forEach((uuid, ban) -> {
            data.set("bans." + uuid + ".until", ban.until());
            data.set("bans." + uuid + ".reason", ban.reason());
            data.set("bans." + uuid + ".by", ban.by());
        });
        long now = System.currentTimeMillis();
        loaderMembers.keySet().removeIf(id -> !loaders.containsKey(id));
        loaderMembers.forEach((id, members) -> members.forEach((uuid, until) -> {
            if (until > now) {
                data.set("members.loaders." + id + "." + uuid, until);
            }
        }));
        ownerMembers.forEach((owner, members) -> members.forEach((uuid, until) -> {
            if (until > now) {
                data.set("members.owners." + owner + "." + uuid, until);
            }
        }));
        writeSwitch(data, "admin-pause", adminPause);
        writeSwitch(data, "monitoring-off", monitoringOff);
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save data.yml", e);
        }
    }

    private static Switch readSwitch(YamlConfiguration data, String path) {
        return data.isConfigurationSection(path) ? new Switch(data.getString(path + ".by", "?"), data.getLong(path + ".since")) : null;
    }

    private static void writeSwitch(YamlConfiguration data, String path, Switch value) {
        if (value != null) {
            data.set(path + ".by", value.by());
            data.set(path + ".since", value.since());
        }
    }

    private static UUID parseUuid(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static String name(UUID player) {
        String name = Bukkit.getOfflinePlayer(player).getName();
        return name == null ? player.toString().substring(0, 8) : name;
    }
}
