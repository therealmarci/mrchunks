package at.marcelr.mrchunks.lag;

import at.marcelr.mrchunks.AdminActions;
import at.marcelr.mrchunks.Loader;
import at.marcelr.mrchunks.LoaderManager;
import at.marcelr.mrchunks.MRChunks;
import at.marcelr.mrchunks.Messages;
import at.marcelr.mrchunks.Settings;
import at.marcelr.mrchunks.Texts;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static at.marcelr.mrchunks.Messages.tag;

/**
 * Measures what happens in the area of every active loader and locks loaders that cause lag.
 * <p>
 * Events (redstone, hopper transfers, pistons, spawns) are counted per loader through a chunk index that is rebuilt
 * at every sample; entities and dropped items are counted at the sample itself. A loader is locked when its area is
 * over a lock limit {@code lock-after-samples} times in a row - by default only while the server is actually lagging.
 * Listeners are only registered for metrics that have a limit, so switching e.g. hoppers off costs nothing.
 */
public final class LagMonitor implements Listener {

    /** Discord warnings for the same loader at most this often. */
    private static final long WARN_INTERVAL = 60 * 60_000L;

    private final MRChunks plugin;
    private final List<Listener> registered = new ArrayList<>();
    private BukkitTask task;

    /** world -> chunk key -> ids of active loaders covering that chunk */
    private Map<UUID, Map<Long, int[]>> index = Map.of();
    /** loader id -> event counts since the last sample, indexed by Metric.ordinal() */
    private final Map<Integer, long[]> counts = new HashMap<>();
    private long windowStart = System.currentTimeMillis();

    private final Map<Integer, Activity> activity = new HashMap<>();
    private final Map<Integer, Integer> strikes = new HashMap<>();
    private final Map<Integer, Long> graceUntil = new HashMap<>();
    private final Map<Integer, Long> lastWarn = new HashMap<>();

    public LagMonitor(MRChunks plugin) {
        this.plugin = plugin;
    }

    private Settings.LagSettings settings() {
        return plugin.settings().lag();
    }

    private LoaderManager loaders() {
        return plugin.loaders();
    }

    // ------------------------------------------------------------------- life

    /** (Re)starts sampling and the event listeners with the current config. */
    public void start() {
        stop();
        // The join notice for locked loaders works even when measuring is switched off.
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Settings.LagSettings s = settings();
        if (!s.enabled()) {
            return;
        }
        if (checked(Metric.REDSTONE)) {
            register(new Listener() {
                @EventHandler(priority = EventPriority.MONITOR)
                public void onRedstone(BlockRedstoneEvent event) {
                    count(event.getBlock(), Metric.REDSTONE);
                }
            });
        }
        if (checked(Metric.HOPPERS)) {
            register(new Listener() {
                @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
                public void onMove(InventoryMoveItemEvent event) {
                    Location from = event.getSource().getLocation();
                    if (from != null && from.getWorld() != null) {
                        count(from.getWorld(), from.getBlockX() >> 4, from.getBlockZ() >> 4, Metric.HOPPERS);
                    }
                }
            });
        }
        if (checked(Metric.PISTONS)) {
            register(new Listener() {
                @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
                public void onExtend(BlockPistonExtendEvent event) {
                    count(event.getBlock(), Metric.PISTONS);
                }

                @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
                public void onRetract(BlockPistonRetractEvent event) {
                    count(event.getBlock(), Metric.PISTONS);
                }
            });
        }
        if (checked(Metric.SPAWNS)) {
            register(new Listener() {
                @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
                public void onCreature(CreatureSpawnEvent event) {
                    count(event.getLocation(), Metric.SPAWNS);
                }

                @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
                public void onItem(ItemSpawnEvent event) {
                    count(event.getLocation(), Metric.SPAWNS);
                }
            });
        }
        rebuildIndex();
        windowStart = System.currentTimeMillis();
        long period = s.sampleSeconds() * 20L;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::sample, period, period);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        HandlerList.unregisterAll(this);
        registered.forEach(HandlerList::unregisterAll);
        registered.clear();
        counts.clear();
    }

    private void register(Listener listener) {
        registered.add(listener);
        Bukkit.getPluginManager().registerEvents(listener, plugin);
    }

    private boolean checked(Metric metric) {
        LagLimits limits = settings().limits();
        return limits.warn(metric) > 0 || limits.lock(metric) > 0;
    }

    // --------------------------------------------------------------- counting

    private void count(Block block, Metric metric) {
        count(block.getWorld(), block.getX() >> 4, block.getZ() >> 4, metric);
    }

    private void count(Location location, Metric metric) {
        if (location.getWorld() != null) {
            count(location.getWorld(), location.getBlockX() >> 4, location.getBlockZ() >> 4, metric);
        }
    }

    private void count(World world, int cx, int cz, Metric metric) {
        Map<Long, int[]> chunks = index.get(world.getUID());
        if (chunks == null) {
            return;
        }
        int[] ids = chunks.get(Loader.chunkKey(cx, cz));
        if (ids == null) {
            return;
        }
        for (int id : ids) {
            counts.computeIfAbsent(id, k -> new long[Metric.values().length])[metric.ordinal()]++;
        }
    }

    /** Which active loader covers which chunk (overlapping areas count for every loader that keeps them loaded). */
    private void rebuildIndex() {
        int radius = plugin.settings().loaders().radius();
        Map<UUID, Map<Long, List<Integer>>> build = new HashMap<>();
        for (Loader loader : loaders().all()) {
            if (!loader.active()) {
                continue;
            }
            Map<Long, List<Integer>> chunks = build.computeIfAbsent(loader.worldId(), k -> new HashMap<>());
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    chunks.computeIfAbsent(Loader.chunkKey(loader.chunkX() + dx, loader.chunkZ() + dz), k -> new ArrayList<>(1))
                            .add(loader.id());
                }
            }
        }
        Map<UUID, Map<Long, int[]>> result = new HashMap<>();
        build.forEach((world, chunks) -> {
            Map<Long, int[]> packed = new HashMap<>(chunks.size() * 2);
            chunks.forEach((key, ids) -> packed.put(key, ids.stream().mapToInt(Integer::intValue).toArray()));
            result.put(world, packed);
        });
        index = result;
    }

    // --------------------------------------------------------------- sampling

    private void sample() {
        Settings.LagSettings s = settings();
        long now = System.currentTimeMillis();
        double seconds = Math.max(1, (now - windowStart) / 1000.0);
        int radius = plugin.settings().loaders().radius();
        boolean serverLags = Bukkit.getAverageTickTime() >= s.serverLagMspt();

        for (Loader loader : loaders().all()) {
            if (!loader.active()) {
                // Keep the last measurement of locked loaders (admins see why), forget the others.
                if (!loaders().lagLocked(loader)) {
                    activity.remove(loader.id());
                }
                strikes.remove(loader.id());
                continue;
            }
            World world = loader.world();
            if (world == null) {
                continue;
            }
            Map<Metric, Double> values = new EnumMap<>(Metric.class);
            int entities = 0;
            int items = 0;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int cx = loader.chunkX() + dx;
                    int cz = loader.chunkZ() + dz;
                    if (!world.isChunkLoaded(cx, cz)) {
                        continue;
                    }
                    Chunk chunk = world.getChunkAt(cx, cz);
                    for (Entity entity : chunk.getEntities()) {
                        if (entity instanceof Player) {
                            continue;
                        }
                        entities++;
                        if (entity instanceof Item) {
                            items++;
                        }
                    }
                }
            }
            values.put(Metric.ENTITIES, (double) entities);
            values.put(Metric.ITEMS, (double) items);
            long[] c = counts.get(loader.id());
            for (Metric metric : Metric.values()) {
                if (metric.rate()) {
                    values.put(metric, c == null ? 0 : c[metric.ordinal()] / seconds);
                }
            }
            Activity a = Activity.of(values, s.limits(), now);
            activity.put(loader.id(), a);
            judge(loader, a, serverLags, now);
        }
        activity.keySet().removeIf(id -> loaders().byId(id) == null);
        counts.clear();
        windowStart = now;
        rebuildIndex();
    }

    private void judge(Loader loader, Activity a, boolean serverLags, long now) {
        Settings.LagSettings s = settings();
        int id = loader.id();
        boolean grace = graceUntil.getOrDefault(id, 0L) > now;
        if (a.lockRatio() >= 1 && !grace && (!s.onlyWhenServerLags() || serverLags)) {
            int count = strikes.merge(id, 1, Integer::sum);
            if (count >= s.lockAfterSamples()) {
                lock(loader, a, now);
                return;
            }
        } else {
            strikes.remove(id);
        }
        if (a.conspicuous() && now - lastWarn.getOrDefault(id, 0L) >= WARN_INTERVAL) {
            lastWarn.put(id, now);
            Metric worst = a.worst();
            plugin.discord().log("loader-lag-warning", plugin.adminActions().discordTags(loader,
                    "metric", plugin.messages().plain("lag-metric-" + worst.key()),
                    "value", format(a.value(worst)), "limit", format(s.limits().warn(worst))));
        }
    }

    // ------------------------------------------------------------------ locks

    private void lock(Loader loader, Activity a, long now) {
        LagLimits limits = settings().limits();
        // The metric that is furthest over its lock limit.
        Metric metric = a.worst();
        double best = -1;
        for (Metric m : Metric.values()) {
            double limit = limits.lock(m);
            double ratio = limit <= 0 ? 0 : a.value(m) / limit;
            if (ratio > best) {
                best = ratio;
                metric = m;
            }
        }
        Player owner = Bukkit.getPlayer(loader.owner());
        LagLock lock = new LagLock(now, metric, a.value(metric), limits.lock(metric), owner != null);
        loaders().lagLock(loader, lock);
        strikes.remove(loader.id());

        Messages messages = plugin.messages();
        var tags = tags(loader, lock);
        if (owner != null) {
            messages.send(owner, "lag-locked-owner", tags);
        }
        Component teleport = messages.get("lag-teleport-label").clickEvent(ClickEvent.runCommand(
                "/mrchunks tp " + LoaderManager.name(loader.owner()) + " " + loader.number()));
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission(AdminActions.PERM_MANAGE) && !staff.equals(owner)) {
                messages.send(staff, "lag-locked-staff", tags, tag("teleport", teleport));
            }
        }
        plugin.getLogger().warning("Chunk loader " + Texts.consoleName(plugin, loader) + " was locked because of lag ("
                + metric.key() + " " + format(lock.value()) + ", limit " + format(lock.limit()) + ").");
        plugin.discord().log("loader-lag-locked", plugin.adminActions().discordTags(loader,
                "metric", messages.plain("lag-metric-" + metric.key()),
                "value", format(lock.value()), "limit", format(lock.limit())));
    }

    /** After an admin lifted a lock: no new lock for unlock-grace, so the owner can fix the machine. */
    public void unlocked(Loader loader) {
        graceUntil.put(loader.id(), System.currentTimeMillis() + settings().unlockGrace());
        strikes.remove(loader.id());
    }

    /** Owners whose loader was locked while they were offline hear about it when they join. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        for (Loader loader : loaders().byOwner(player.getUniqueId())) {
            LagLock lock = loaders().lagLock(loader);
            if (lock != null && !lock.notified()) {
                plugin.messages().send(player, "lag-locked-join", tags(loader, lock));
                loaders().updateLagLock(loader, lock.withNotified());
            }
        }
    }

    // ----------------------------------------------------------------- access

    /** The last measurement of a loader, or null (not measured yet, inactive, measuring off). */
    public Activity activity(Loader loader) {
        return activity.get(loader.id());
    }

    /** Conspicuous and locked loaders, the worst first. */
    public List<Loader> conspicuous() {
        List<Loader> result = new ArrayList<>();
        for (Loader loader : loaders().all()) {
            Activity a = activity.get(loader.id());
            if (loaders().lagLocked(loader) || (a != null && a.conspicuous())) {
                result.add(loader);
            }
        }
        result.sort(Comparator.comparing((Loader l) -> !loaders().lagLocked(l))
                .thenComparing(l -> -ratio(l)));
        return result;
    }

    private double ratio(Loader loader) {
        Activity a = activity.get(loader.id());
        return a == null ? 0 : a.warnRatio();
    }

    /** Placeholders of a lock: loader tags plus metric, value, limit, time. */
    public net.kyori.adventure.text.minimessage.tag.resolver.TagResolver tags(Loader loader, LagLock lock) {
        return net.kyori.adventure.text.minimessage.tag.resolver.TagResolver.resolver(
                Texts.loader(plugin, loader),
                tag("metric", plugin.messages().plain("lag-metric-" + lock.metric().key())),
                tag("value", format(lock.value())),
                tag("limit", format(lock.limit())),
                tag("time", Texts.date(plugin.messages(), lock.since())));
    }

    /** Whole numbers, rates with at most one decimal. */
    public static String format(double value) {
        return value >= 100 || value == Math.rint(value) ? String.valueOf(Math.round(value))
                : String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
