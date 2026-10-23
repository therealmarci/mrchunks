package at.marcelr.mrchunks;

import at.marcelr.mrchunks.api.MRpvpArenaWorld;
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
    private final MRpvpArenaWorld pvpArenaWorld;

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
    /** radius and method the current tickets were created with (they must be removed the same way) */
    private int appliedRadius;
    private Settings.LoadMethod appliedMethod;
    /** Server pause (see {@link at.marcelr.mrchunks.lag.ServerPause}): no tickets at all, loaders keep their state. */
    private boolean paused;

    private final Map<UUID, Integer> limitOverrides = new HashMap<>();
    private final Map<UUID, Integer> activeOverrides = new HashMap<>();
    private final Map<UUID, Ban> bans = new HashMap<>();
    /** loader id -> invited player -> valid until (ms, Long.MAX_VALUE = permanent) */
    private final Map<Integer, Map<UUID, Long>> loaderMembers = new HashMap<>();
    /** owner -> invited player -> valid until; such an invite covers all loaders of the owner, also future ones */
    private final Map<UUID, Map<UUID, Long>> ownerMembers = new HashMap<>();
    /** loader id -> lag lock (only admins can lift it) */
    private final Map<Integer, LagLock> lagLocks = new HashMap<>();

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
        this.pvpArenaWorld = new MRpvpArenaWorld(plugin.getLogger());
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

    /**
     * Loaders whose area overlaps the area a loader at chunk {@code cx}/{@code cz} would have (same world).
     * Two areas overlap if the centres are at most 2 * radius chunks apart on both axes.
     */
    public List<Loader> overlapping(World world, int cx, int cz, Loader except) {
        int reach = plugin.settings().loaders().radius() * 2;
        List<Loader> result = new ArrayList<>();
        for (Loader loader : loaders.values()) {
            if (loader != except && loader.worldId().equals(world.getUID())
                    && Math.abs(loader.chunkX() - cx) <= reach && Math.abs(loader.chunkZ() - cz) <= reach) {
                result.add(loader);
            }
        }
        return result;
    }

    /** How many chunks of the area around {@code cx}/{@code cz} are also covered by one of {@code others}. */
    public int sharedChunks(int cx, int cz, Collection<Loader> others) {
        int r = plugin.settings().loaders().radius();
        int shared = 0;
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                for (Loader other : others) {
                    if (Math.abs(other.chunkX() - x) <= r && Math.abs(other.chunkZ() - z) <= r) {
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

    /** All players that own at least one loader or have a ban or custom limit. */
    public Set<UUID> knownPlayers() {
        Set<UUID> players = new HashSet<>();
        loaders.values().forEach(l -> players.add(l.owner()));
        players.addAll(limitOverrides.keySet());
        players.addAll(activeOverrides.keySet());
        players.addAll(activeBans().keySet());
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
                // MRpvp pastes and clears arenas there on its own - no loaders.
                && !pvpArenaWorld.is(world);
    }

    // --------------------------------------------------------- create/delete

    /** @param active false if the owner already has as many active loaders as allowed */
    public Loader create(UUID owner, Block block, boolean active) {
        Loader loader = new Loader(nextId++, owner, block.getWorld().getUID(), block.getWorld().getName(),
                block.getX(), block.getY(), block.getZ(), System.currentTimeMillis(), nextNumber(owner), null, active);
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
        unindex(loader);
        loaderMembers.remove(loader.id());
        lagLocks.remove(loader.id());
        plugin.border().loaderRemoved(loader);
        World world = loader.world();
        if (world != null) {
            Block block = world.getBlockAt(loader.x(), loader.y(), loader.z());
            if (block.getType() == plugin.settings().item().material()) {
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
        plugin.maps().refreshSoon();
        plugin.border().loaderChanged(loader);
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

    // ------------------------------------------------------------- tickets

    public boolean paused() {
        return paused;
    }

    /**
     * Pausing releases every ticket, resuming adds them again for all active loaders. Loaders keep their on/off
     * state; loaders switched on during the pause start loading when it ends.
     */
    public void setPaused(boolean paused) {
        if (this.paused == paused) {
            return;
        }
        if (paused) {
            releaseAll();
            this.paused = true;
        } else {
            this.paused = false;
            applyAll();
        }
        plugin.maps().refreshSoon();
    }

    /** Adds tickets for all active loaders of all loaded worlds (on enable / after reload). */
    public void applyAll() {
        appliedRadius = plugin.settings().loaders().radius();
        appliedMethod = plugin.settings().loaders().loadMethod();
        for (World world : Bukkit.getWorlds()) {
            applyWorld(world);
        }
    }

    /** Adds tickets for the active loaders of one world (also used when a world loads later). */
    public void applyWorld(World world) {
        for (Loader loader : loaders.values()) {
            if (loader.active() && loader.worldId().equals(world.getUID())) {
                cover(loader);
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
    }

    private void cover(Loader loader) {
        World world = loader.world();
        if (world == null || paused) {
            return; // applied later by applyWorld when the world loads, or by applyAll when the pause ends
        }
        Map<Long, Integer> chunks = coverage.computeIfAbsent(world.getUID(), k -> new HashMap<>());
        int r = appliedRadius;
        for (int cx = loader.chunkX() - r; cx <= loader.chunkX() + r; cx++) {
            for (int cz = loader.chunkZ() - r; cz <= loader.chunkZ() + r; cz++) {
                if (chunks.merge(Loader.chunkKey(cx, cz), 1, Integer::sum) == 1) {
                    addTicket(world, cx, cz);
                }
            }
        }
    }

    private void uncover(Loader loader) {
        World world = loader.world();
        Map<Long, Integer> chunks = world == null ? null : coverage.get(world.getUID());
        if (chunks == null) {
            return;
        }
        int r = appliedRadius;
        for (int cx = loader.chunkX() - r; cx <= loader.chunkX() + r; cx++) {
            for (int cz = loader.chunkZ() - r; cz <= loader.chunkZ() + r; cz++) {
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
        Material material = plugin.settings().item().material();
        boolean restore = plugin.settings().loaders().restoreMissingBlocks();
        for (Loader loader : candidates) {
            World world = loader.world();
            if (!loaders.containsKey(loader.id()) || world == null
                    || !world.isChunkLoaded(loader.chunkX(), loader.chunkZ())) {
                continue;
            }
            Block block = world.getBlockAt(loader.x(), loader.y(), loader.z());
            if (block.getType() == material) {
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

    /** The two caps per player: how many loaders may be placed, and how many of them may be active at once. */
    public enum LimitKind {
        PLACE("mrchunks.limit.", "mrchunks.limit.unlimited"),
        ACTIVE("mrchunks.active.", "mrchunks.active.unlimited");

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
        return kind == LimitKind.PLACE ? limitOverrides : activeOverrides;
    }

    /** Where the effective limit comes from (for display). */
    public enum LimitSource { DEFAULT, PERMISSION, CUSTOM, UNLIMITED }

    public record Limit(int value, LimitSource source) {
    }

    /** How many loaders the player may place. */
    public Limit limit(OfflinePlayer player) {
        return limit(player, LimitKind.PLACE);
    }

    /** How many loaders of the player may be active at the same time. */
    public Limit activeLimit(OfflinePlayer player) {
        return limit(player, LimitKind.ACTIVE);
    }

    /**
     * Effective limit: an admin override wins; otherwise the highest {@code <prefix><n>} permission
     * (only known while the player is online), but never less than the config default.
     */
    public Limit limit(OfflinePlayer player, LimitKind kind) {
        Integer override = overrides(kind).get(player.getUniqueId());
        if (override != null) {
            return new Limit(override, LimitSource.CUSTOM);
        }
        Settings.LoaderSettings settings = plugin.settings().loaders();
        int base = kind == LimitKind.PLACE ? settings.defaultLimit() : settings.defaultActiveLimit();
        Player online = player.getPlayer();
        if (online == null) {
            return new Limit(base, LimitSource.DEFAULT);
        }
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

    /** True if one more of the player's loaders may be activated. */
    public boolean canActivate(OfflinePlayer player) {
        return activeCount(player.getUniqueId()) < activeLimit(player).value();
    }

    /**
     * Deactivates the newest active loaders until the player is within their active limit.
     * @return the deactivated loaders
     */
    public List<Loader> enforceActiveLimit(OfflinePlayer player) {
        int limit = activeLimit(player).value();
        List<Loader> active = new ArrayList<>();
        for (Loader loader : byOwner(player.getUniqueId())) {
            if (loader.active()) {
                active.add(loader);
            }
        }
        List<Loader> deactivated = new ArrayList<>();
        // byOwner keeps creation order: keep the oldest ones running
        for (int i = active.size() - 1; i >= 0 && active.size() - deactivated.size() > limit; i--) {
            setActive(active.get(i), false);
            deactivated.add(active.get(i));
        }
        return deactivated;
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

    /** Loaders of other players that {@code player} may switch on and off. */
    public List<Loader> sharedWith(UUID player) {
        List<Loader> result = new ArrayList<>();
        for (Loader loader : loaders.values()) {
            if (!loader.owner().equals(player) && isMember(player, loader)) {
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
        limitOverrides.clear();
        activeOverrides.clear();
        bans.clear();
        loaderMembers.clear();
        ownerMembers.clear();
        lagLocks.clear();
        nextId = 1;
        if (!dataFile.exists()) {
            return;
        }
        YamlConfiguration data = YamlConfiguration.loadConfiguration(dataFile);
        nextId = Math.max(1, data.getInt("next-id", 1));

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
                loaders.put(id, new Loader(id, owner, world, s.getString("world-name", "?"),
                        s.getInt("x"), s.getInt("y"), s.getInt("z"), s.getLong("created"),
                        s.getInt("number", 0), name == null || name.isBlank() ? null : name,
                        s.getBoolean("active", true)));
                nextId = Math.max(nextId, id + 1);
            }
        }
        ConfigurationSection limitSection = data.getConfigurationSection("limits");
        if (limitSection != null) {
            for (String key : limitSection.getKeys(false)) {
                UUID uuid = parseUuid(key);
                if (uuid != null) {
                    limitOverrides.put(uuid, limitSection.getInt(key));
                }
            }
        }
        ConfigurationSection activeSection = data.getConfigurationSection("active-limits");
        if (activeSection != null) {
            for (String key : activeSection.getKeys(false)) {
                UUID uuid = parseUuid(key);
                if (uuid != null) {
                    activeOverrides.put(uuid, activeSection.getInt(key));
                }
            }
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
                                s.getDouble("value"), s.getDouble("limit"), s.getBoolean("notified")));
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
            loaders.put(old.id(), new Loader(old.id(), old.owner(), old.worldId(), old.worldName(), old.x(), old.y(),
                    old.z(), old.created(), number, old.name(), old.active()));
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
        }
        limitOverrides.forEach((uuid, limit) -> data.set("limits." + uuid, limit));
        activeOverrides.forEach((uuid, limit) -> data.set("active-limits." + uuid, limit));
        lagLocks.keySet().removeIf(id -> !loaders.containsKey(id));
        lagLocks.forEach((id, lock) -> {
            String p = "lag-locks." + id + ".";
            data.set(p + "since", lock.since());
            data.set(p + "metric", lock.metric().key());
            data.set(p + "value", lock.value());
            data.set(p + "limit", lock.limit());
            data.set(p + "notified", lock.notified());
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
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save data.yml", e);
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
