package at.marcelr.mrchunks;

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

    public static final String PERM_LIMIT_PREFIX = "mrchunks.limit.";
    public static final String PERM_UNLIMITED = "mrchunks.limit.unlimited";

    private final MRChunks plugin;
    private final File dataFile;

    private final Map<Integer, Loader> loaders = new LinkedHashMap<>();
    /** "world-uuid:x:y:z" -> loader, so block lookups (explosions, clicks) don't scan all loaders */
    private final Map<String, Loader> byPosition = new HashMap<>();
    private int nextId = 1;

    /** world -> chunk key -> number of active loaders covering that chunk */
    private final Map<UUID, Map<Long, Integer>> coverage = new HashMap<>();
    /** world -> chunks MRchunks force-loaded itself (load-method: forceload), so it never unforces others' chunks */
    private final Map<UUID, Set<Long>> forcedByUs = new HashMap<>();
    /** radius and method the current tickets were created with (they must be removed the same way) */
    private int appliedRadius;
    private Settings.LoadMethod appliedMethod;

    private final Map<UUID, Integer> limitOverrides = new HashMap<>();
    private final Map<UUID, Ban> bans = new HashMap<>();

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
        return !plugin.settings().loaders().disabledWorlds().contains(world.getName().toLowerCase(Locale.ROOT));
    }

    // --------------------------------------------------------- create/delete

    public Loader create(UUID owner, Block block) {
        Loader loader = new Loader(nextId++, owner, block.getWorld().getUID(), block.getWorld().getName(),
                block.getX(), block.getY(), block.getZ(), System.currentTimeMillis(), true);
        loaders.put(loader.id(), loader);
        index(loader);
        cover(loader);
        save();
        return loader;
    }

    /** Removes the loader and (if its world is loaded) its block. */
    public void delete(Loader loader) {
        if (loader.active()) {
            uncover(loader);
        }
        loaders.remove(loader.id());
        byPosition.remove(positionKey(loader.worldId(), loader.x(), loader.y(), loader.z()));
        World world = loader.world();
        if (world != null) {
            Block block = world.getBlockAt(loader.x(), loader.y(), loader.z());
            if (block.getType() == plugin.settings().item().material()) {
                block.setType(Material.AIR, false);
            }
        }
        save();
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
    }

    // ------------------------------------------------------------- tickets

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
        if (world == null) {
            return; // applied later by applyWorld when the world loads
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
        Map<Loader, Boolean> result = new LinkedHashMap<>();
        Material material = plugin.settings().item().material();
        boolean restore = plugin.settings().loaders().restoreMissingBlocks();
        for (Loader loader : List.copyOf(loaders.values())) {
            World world = loader.world();
            if (world == null || !world.isChunkLoaded(loader.chunkX(), loader.chunkZ())) {
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

    /** @return the admin-set limit, or null if the player uses the default/permission limit */
    public Integer limitOverride(UUID player) {
        return limitOverrides.get(player);
    }

    public void setLimitOverride(UUID player, Integer limit) {
        if (limit == null) {
            limitOverrides.remove(player);
        } else {
            limitOverrides.put(player, Math.max(0, limit));
        }
        save();
    }

    /** Where the effective limit comes from (for display). */
    public enum LimitSource { DEFAULT, PERMISSION, CUSTOM, UNLIMITED }

    public record Limit(int value, LimitSource source) {
    }

    /**
     * Effective limit: an admin override wins; otherwise the highest {@code mrchunks.limit.<n>} permission
     * (only known while the player is online), but never less than the config default.
     */
    public Limit limit(OfflinePlayer player) {
        Integer override = limitOverrides.get(player.getUniqueId());
        if (override != null) {
            return new Limit(override, LimitSource.CUSTOM);
        }
        int base = plugin.settings().loaders().defaultLimit();
        Player online = player.getPlayer();
        if (online == null) {
            return new Limit(base, LimitSource.DEFAULT);
        }
        if (online.hasPermission(PERM_UNLIMITED)) {
            return new Limit(Integer.MAX_VALUE, LimitSource.UNLIMITED);
        }
        int best = -1;
        for (PermissionAttachmentInfo info : online.getEffectivePermissions()) {
            String perm = info.getPermission();
            if (info.getValue() && perm.startsWith(PERM_LIMIT_PREFIX)) {
                try {
                    best = Math.max(best, Integer.parseInt(perm.substring(PERM_LIMIT_PREFIX.length())));
                } catch (NumberFormatException ignored) {
                    // e.g. mrchunks.limit.unlimited or a typo
                }
            }
        }
        return best > base ? new Limit(best, LimitSource.PERMISSION) : new Limit(base, LimitSource.DEFAULT);
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

    // --------------------------------------------------------- persistence

    public void load() {
        loaders.clear();
        byPosition.clear();
        limitOverrides.clear();
        bans.clear();
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
                    plugin.getLogger().warning("Chunkloader #" + key + " in data.yml ist unvollständig und wird übersprungen.");
                    continue;
                }
                int id = Integer.parseInt(key);
                loaders.put(id, new Loader(id, owner, world, s.getString("world-name", "?"),
                        s.getInt("x"), s.getInt("y"), s.getInt("z"),
                        s.getLong("created"), s.getBoolean("active", true)));
                index(loaders.get(id));
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
            data.set(p + "active", loader.active());
        }
        limitOverrides.forEach((uuid, limit) -> data.set("limits." + uuid, limit));
        bans.forEach((uuid, ban) -> {
            data.set("bans." + uuid + ".until", ban.until());
            data.set("bans." + uuid + ".reason", ban.reason());
            data.set("bans." + uuid + ".by", ban.by());
        });
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Konnte data.yml nicht speichern", e);
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
