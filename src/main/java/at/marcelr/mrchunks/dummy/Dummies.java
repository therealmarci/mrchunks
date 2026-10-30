package at.marcelr.mrchunks.dummy;

import at.marcelr.mrchunks.Loader;
import at.marcelr.mrchunks.LoaderManager;
import at.marcelr.mrchunks.MRChunks;
import at.marcelr.mrchunks.Texts;
import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.event.player.PlayerServerFullCheckEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Dummy players: a fake player sits on an active loader so the server treats the area as if a player were there
 * (mob AI and portals, spawning, random ticks). Main thread only.
 * <p>
 * A dummy is a real server player (see {@link DummyBackend}) in creative mode, so it is invulnerable, ignored by
 * mobs and does not attract phantoms. It is never ticked like a client player: it does not move, eat, pick up items
 * or gain statistics. MRchunks hides it from the tab list, the player count and the server-full check; other plugins
 * recognise it by the metadata {@value #NPC_METADATA} (the common NPC convention, also used by Citizens) or
 * {@value #METADATA}. Its data is never saved.
 */
public final class Dummies implements Listener {

    /** Metadata on the player that marks an NPC; MRsleep, MRadmin and MRpvp skip such players. */
    public static final String NPC_METADATA = "NPC";
    /** Metadata on the player with the loader id - only MRchunks' dummies have it. */
    public static final String METADATA = "mrchunks-dummy";
    /** The same as a non-deprecated marker for other plugins: {@code mrchunks:dummy} in the player's data container. */
    public static final NamespacedKey KEY = new NamespacedKey("mrchunks", "dummy");
    private static final String BACKEND = "at.marcelr.mrchunks.dummy.nms.NmsDummyBackend";
    /** Profile names must not be longer (the client rejects them). */
    private static final int MAX_NAME = 16;
    /** Smallest simulation distance the server allows a player - a dummy always simulates at least 5x5 chunks. */
    public static final int MIN_REACH = 2;

    private final MRChunks plugin;
    private final File skinFile;
    private DummyBackend backend;
    private String unavailableReason;

    /** loader id -> its dummy */
    private final Map<Integer, Spawned> spawned = new HashMap<>();
    /** loader ids whose dummy is spawned on the next tick */
    private final Set<Integer> pending = new HashSet<>();
    /** UUIDs of all dummies, also while one is joining */
    private final Set<UUID> ids = new HashSet<>();
    /** owner -> last known skin ("textures" property) */
    private final Map<UUID, Skin> skins = new HashMap<>();
    private final Set<UUID> skinRequested = new HashSet<>();
    /** True while MRchunks itself moves, seats or removes a dummy (events it would block otherwise). */
    private boolean internal;
    private boolean shuttingDown;
    private BukkitTask watchdog;

    private record Spawned(Player player, UUID owner, Entity seat) {
    }

    private record Skin(String value, String signature) {
    }

    public Dummies(MRChunks plugin) {
        this.plugin = plugin;
        this.skinFile = new File(plugin.getDataFolder(), "skins.yml");
        try {
            backend = (DummyBackend) Class.forName(BACKEND).getConstructor().newInstance();
        } catch (Throwable t) {
            // NoSuchMethodError, NoClassDefFoundError ... - the server version does not fit this build.
            unavailableReason = t.toString();
        }
        loadSkins();
    }

    /** Whether this server version supports dummies at all. */
    public boolean available() {
        return backend != null;
    }

    /** Dummies are switched on in the config and work on this server. */
    public boolean enabled() {
        return available() && plugin.settings().dummies().enabled();
    }

    /** Warns once per (re)load if dummies are switched on but cannot work here. */
    public void checkAvailability() {
        if (plugin.settings().dummies().enabled() && !available()) {
            plugin.getLogger().warning("Dummy players are enabled in config.yml but do not work with this server version ("
                    + Bukkit.getVersion() + "): " + unavailableReason + ". Everything else works normally.");
        }
    }

    /** Starts the periodic check that respawns dummies another plugin removed or moved. */
    public void start() {
        if (watchdog != null) {
            watchdog.cancel();
        }
        watchdog = Bukkit.getScheduler().runTaskTimer(plugin, this::checkSpawned, 100L, 100L);
    }

    /** Removes all dummies (plugin disable). */
    public void shutdown() {
        shuttingDown = true;
        if (watchdog != null) {
            watchdog.cancel();
        }
        pending.clear();
        for (Integer id : List.copyOf(spawned.keySet())) {
            despawn(id);
        }
    }

    // ----------------------------------------------------------------- state

    public static boolean isDummy(Player player) {
        return player.hasMetadata(METADATA);
    }

    private boolean isOurs(Entity entity) {
        return entity instanceof Player && ids.contains(entity.getUniqueId());
    }

    /** Number of dummies currently in the world. */
    public int count() {
        return spawned.size();
    }

    /** The dummy sitting on this loader, or null. */
    public Player dummyOf(Loader loader) {
        Spawned s = spawned.get(loader.id());
        return s == null ? null : s.player();
    }

    /**
     * Whether a dummy can sit on the loader: a dummy simulates a square around itself, so the area must be a square
     * with the loader block in the middle, and at least {@link #MIN_REACH} chunks in every direction - otherwise it
     * would simulate chunks outside the area (beyond what the owner's budget pays for).
     */
    public static boolean fits(Loader loader) {
        return loader.centred() && loader.reach(Loader.Direction.NORTH) >= MIN_REACH;
    }

    /** Whether the loader's dummy should be in the world right now. */
    private boolean wanted(Loader loader) {
        World world = loader.world();
        return !shuttingDown && enabled() && loader.dummy() && loader.active() && fits(loader) && world != null
                && plugin.loaders().exists(loader) && !plugin.loaders().paused() && !plugin.loaders().shed(loader)
                && plugin.loaders().isWorldAllowed(world);
    }

    /** Spawns or removes the loader's dummy so it matches the loader. Spawning happens on the next tick. */
    public void sync(Loader loader) {
        Spawned current = spawned.get(loader.id());
        boolean wanted = wanted(loader);
        if (current != null && (!wanted || !current.owner().equals(loader.owner()))) {
            despawn(loader.id());
            current = null;
        }
        if (current != null) {
            distances(current.player(), loader); // the loader may have been resized
        }
        if (wanted && current == null && pending.add(loader.id())) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                pending.remove(loader.id());
                Loader now = plugin.loaders().byId(loader.id());
                if (now != null && wanted(now) && !spawned.containsKey(now.id())) {
                    spawn(now);
                }
            });
        }
    }

    /** {@link #sync} for every loader; also removes dummies of loaders that no longer exist. */
    public void syncAll() {
        for (Integer id : List.copyOf(spawned.keySet())) {
            if (plugin.loaders().byId(id) == null) {
                despawn(id);
            }
        }
        for (Loader loader : plugin.loaders().all()) {
            sync(loader);
        }
    }

    /** Dummies that were removed, killed or moved by something else come back. */
    private void checkSpawned() {
        for (Map.Entry<Integer, Spawned> entry : List.copyOf(spawned.entrySet())) {
            Spawned s = entry.getValue();
            Loader loader = plugin.loaders().byId(entry.getKey());
            boolean broken = !s.player().isOnline() || s.player().isDead()
                    || (s.seat() != null && (!s.seat().isValid() || !s.seat().getPassengers().contains(s.player())));
            if (broken || loader == null) {
                despawn(entry.getKey());
            }
            if (loader != null) {
                sync(loader);
            }
        }
    }

    // ----------------------------------------------------------------- spawn

    @SuppressWarnings("deprecation") // metadata: the NPC convention other plugins check
    private void spawn(Loader loader) {
        World world = loader.world();
        if (world == null || backend == null) {
            return;
        }
        UUID owner = loader.owner();
        Skin skin = null;
        if (plugin.settings().dummies().ownerSkin()) {
            skin = skins.get(owner);
            if (skin == null) {
                requestSkin(owner);
            }
        }
        UUID id = UUID.nameUUIDFromBytes(("MRchunks-Dummy:" + loader.id()).getBytes(StandardCharsets.UTF_8));
        String name = name(loader);
        double x = loader.x() + 0.5;
        double y = loader.y() + 1;
        double z = loader.z() + 0.5;
        ids.add(id);
        Player player;
        internal = true;
        try {
            player = backend.spawn(world, x, y, z, 0f, id, name, skin == null ? null : skin.value(),
                    skin == null ? null : skin.signature(), p -> {
                        p.setMetadata(NPC_METADATA, new FixedMetadataValue(plugin, true));
                        p.setMetadata(METADATA, new FixedMetadataValue(plugin, loader.id()));
                        p.getPersistentDataContainer().set(KEY, PersistentDataType.BOOLEAN, true);
                        p.setPersistent(false);
                    });
            setUp(player);
            distances(player, loader);
        } catch (Throwable t) {
            ids.remove(id);
            backend = null;
            unavailableReason = t.toString();
            plugin.getLogger().log(Level.SEVERE, "Could not spawn the dummy of " + Texts.consoleName(plugin, loader)
                    + " - dummy players are switched off until the next restart.", t);
            return;
        } finally {
            internal = false;
        }
        Entity seat = plugin.settings().dummies().seat() ? seat(world, x, y, z, player) : null;
        spawned.put(loader.id(), new Spawned(player, owner, seat));
        plugin.maps().refreshSoon(); // dummy icon on the web map
        plugin.getLogger().info("Dummy " + name + " sits on " + Texts.consoleName(plugin, loader) + ".");
    }

    /** Creative (invulnerable, ignored by mobs), no sleeping, nothing to pick up, small send distance. */
    private void setUp(Player player) {
        player.setGameMode(GameMode.CREATIVE);
        player.setInvulnerable(true);
        player.setSleepingIgnored(true);
        player.setCollidable(false);
        player.setCanPickupItems(false);
        player.setAffectsSpawning(true);
        player.setSendViewDistance(2);
    }

    /** Simulates exactly the loader's (square) area, view distance one more. */
    private static void distances(Player player, Loader loader) {
        int reach = loader.reach(Loader.Direction.NORTH);
        if (player.getSimulationDistance() != Math.clamp(reach, MIN_REACH, 32)) {
            player.setSimulationDistance(Math.clamp(reach, MIN_REACH, 32));
            player.setViewDistance(Math.clamp(reach + 1, MIN_REACH, 32));
        }
    }

    /** An invisible seat on the block; the dummy rides it, so it sits and cannot be pushed away. */
    @SuppressWarnings("deprecation")
    private Entity seat(World world, double x, double y, double z, Player player) {
        internal = true;
        try {
            ItemDisplay seat = world.spawn(new Location(world, x, y, z), ItemDisplay.class, display -> {
                display.setPersistent(false);
                display.setMetadata(METADATA, new FixedMetadataValue(plugin, true));
            });
            if (!seat.addPassenger(player)) {
                seat.remove();
                return null;
            }
            return seat;
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Could not seat dummy " + player.getName() + ", it stands instead.", e);
            return null;
        } finally {
            internal = false;
        }
    }

    /**
     * Profile name: "[D3]Marci" for the dummy on Marci's loader 3. Brackets cannot be in a real Minecraft name, so a
     * dummy never takes the name of a real player. At most 16 characters.
     */
    private static String name(Loader loader) {
        String tag = "[D" + loader.number() + "]";
        String owner = LoaderManager.name(loader.owner());
        int room = Math.max(0, MAX_NAME - tag.length());
        return tag + (owner.length() > room ? owner.substring(0, room) : owner);
    }

    private void despawn(int loaderId) {
        Spawned s = spawned.remove(loaderId);
        if (s == null) {
            return;
        }
        internal = true;
        try {
            if (s.seat() != null && s.seat().isValid()) {
                s.seat().eject();
                s.seat().remove();
            }
            if (s.player().isOnline() && backend != null) {
                backend.remove(s.player());
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Could not remove dummy " + s.player().getName(), t);
        } finally {
            internal = false;
            ids.remove(s.player().getUniqueId());
            if (!shuttingDown) {
                plugin.maps().refreshSoon();
            }
        }
    }

    // ----------------------------------------------------------------- skins

    /** Looks up the owner's skin once (Mojang, async); dummies of the owner are respawned with it. */
    private void requestSkin(UUID owner) {
        if (!skinRequested.add(owner)) {
            return;
        }
        Bukkit.createProfile(owner).update().whenComplete((profile, error) -> {
            if (error == null && profile != null) {
                Bukkit.getScheduler().runTask(plugin, () -> rememberSkin(owner, profile));
            }
        });
    }

    /** Stores the skin of the profile; dummies of this owner with an outdated skin are respawned. */
    private void rememberSkin(UUID owner, PlayerProfile profile) {
        for (ProfileProperty property : profile.getProperties()) {
            if (property.getName().equals("textures")) {
                Skin skin = new Skin(property.getValue(), property.getSignature());
                if (skin.equals(skins.put(owner, skin))) {
                    return;
                }
                saveSkins();
                for (Map.Entry<Integer, Spawned> entry : List.copyOf(spawned.entrySet())) {
                    if (entry.getValue().owner().equals(owner)) {
                        despawn(entry.getKey());
                        Loader loader = plugin.loaders().byId(entry.getKey());
                        if (loader != null) {
                            sync(loader);
                        }
                    }
                }
                return;
            }
        }
    }

    private void loadSkins() {
        if (!skinFile.exists()) {
            return;
        }
        YamlConfiguration data = YamlConfiguration.loadConfiguration(skinFile);
        for (String key : data.getKeys(false)) {
            ConfigurationSection s = data.getConfigurationSection(key);
            try {
                if (s != null && s.getString("value") != null) {
                    skins.put(UUID.fromString(key), new Skin(s.getString("value"), s.getString("signature")));
                }
            } catch (IllegalArgumentException ignored) {
                // hand-edited garbage
            }
        }
    }

    private void saveSkins() {
        YamlConfiguration data = new YamlConfiguration();
        skins.forEach((uuid, skin) -> {
            data.set(uuid + ".value", skin.value());
            data.set(uuid + ".signature", skin.signature());
        });
        try {
            data.save(skinFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save skins.yml", e);
        }
    }

    // ---------------------------------------------------------------- events

    /** Dummies join silently and stay out of everyone's tab list; joining players don't list existing dummies. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (isOurs(player)) {
            event.joinMessage(null);
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (other != player) {
                    other.unlistPlayer(player);
                }
            }
            return;
        }
        for (Spawned s : spawned.values()) {
            player.unlistPlayer(s.player());
        }
        // Owners keep the skin of their dummies up to date.
        if (plugin.settings().dummies().ownerSkin()) {
            UUID main = plugin.loaders().main(player.getUniqueId());
            if (main.equals(player.getUniqueId()) && plugin.loaders().dummyCount(main) > 0) {
                rememberSkin(main, player.getPlayerProfile());
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        if (isOurs(event.getPlayer())) {
            event.quitMessage(null);
        }
    }

    /** Other plugins cannot kick, move or unseat a dummy, nor change its game mode. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onKick(PlayerKickEvent event) {
        if (!internal && isOurs(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!internal && isOurs(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDismount(EntityDismountEvent event) {
        if (!internal && isOurs(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent event) {
        if (!internal && isOurs(event.getPlayer()) && event.getNewGameMode() != GameMode.CREATIVE) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (isOurs(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (event.getTarget() != null && isOurs(event.getTarget())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (isOurs(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        if (isOurs(event.getPlayer())) {
            event.message(null);
        }
    }

    /** /kill and the like: no message, no drops; the watchdog brings it back. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent event) {
        if (isOurs(event.getPlayer())) {
            event.deathMessage(null);
            event.getDrops().clear();
            event.setKeepInventory(true);
            event.setShouldDropExperience(false);
        }
    }

    /** A world can only unload without players - take its dummies out first. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        for (Map.Entry<Integer, Spawned> entry : List.copyOf(spawned.entrySet())) {
            if (entry.getValue().player().getWorld().equals(event.getWorld())) {
                despawn(entry.getKey());
            }
        }
    }

    /** The server list shows only real players. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPing(PaperServerListPingEvent event) {
        if (ids.isEmpty()) {
            return;
        }
        int online = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isOurs(player)) {
                online++;
            }
        }
        event.setNumPlayers(Math.max(0, event.getNumPlayers() - online));
        event.getListedPlayers().removeIf(info -> ids.contains(info.id()));
    }

    /** Dummies never make the server full for real players. */
    @EventHandler(priority = EventPriority.LOW)
    public void onFullCheck(PlayerServerFullCheckEvent event) {
        if (event.isAllowed() || ids.isEmpty()) {
            return;
        }
        List<Player> real = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isOurs(player)) {
                real.add(player);
            }
        }
        if (real.size() < Bukkit.getMaxPlayers()) {
            event.allow(true);
        }
    }
}
