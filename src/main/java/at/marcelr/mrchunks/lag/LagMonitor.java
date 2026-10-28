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
 * at every sample; entities and dropped items are counted at the sample itself.
 * <p>
 * The limits grow with the area (see {@link Settings.LagSettings#limitsFor}), and all active loaders of one owner
 * share one pool: what counts is the sum of their values against the limits of their areas together. So splitting the
 * same budget into many small loaders never allows more load, and a busy small loader is fine as long as the owner's
 * other loaders are quiet. When the pool is over a lock limit {@code lock-after-samples} times in a row - by default
 * only while the server is actually lagging, or without lag once it reaches {@code force-lock-multiplier} times the
 * limit - the owner's loader that is furthest over its own share is locked.
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

    /** loader id -> last measurement, judged against the loader's own share of the limits */
    private final Map<Integer, Activity> activity = new HashMap<>();
    /** owner -> all their active loaders together, against the limits of their areas together */
    private final Map<UUID, Activity> pools = new HashMap<>();
    /** owner -> samples in a row with their pool over a lock limit */
    private final Map<UUID, Integer> strikes = new HashMap<>();
    /** loader id -> samples judged since an admin lifted its lock (cooldown, removed after lock-after-samples) */
    private final Map<Integer, Integer> sinceUnlock = new HashMap<>();
    private final Map<Integer, Long> lastWarn = new HashMap<>();
    /** Loader ids that were conspicuous at the last sample (the web map is redrawn when they change). */
    private java.util.Set<Integer> lastConspicuous = java.util.Set.of();

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
        Map<UUID, Map<Long, List<Integer>>> build = new HashMap<>();
        for (Loader loader : loaders().all()) {
            // Locked loaders too: admins want to see whether things calmed down before lifting the lock.
            if (!loader.active() && !loaders().lagLocked(loader)) {
                continue;
            }
            Map<Long, List<Integer>> chunks = build.computeIfAbsent(loader.worldId(), k -> new HashMap<>());
            for (int cx = loader.minChunkX(); cx <= loader.maxChunkX(); cx++) {
                for (int cz = loader.minChunkZ(); cz <= loader.maxChunkZ(); cz++) {
                    chunks.computeIfAbsent(Loader.chunkKey(cx, cz), k -> new ArrayList<>(1)).add(loader.id());
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

    /** A loader's area changed: events are counted for its new chunks from now on. */
    public void areaChanged() {
        if (task != null) {
            rebuildIndex();
        }
    }

    // --------------------------------------------------------------- sampling

    private void sample() {
        Settings.LagSettings s = settings();
        long now = System.currentTimeMillis();
        if (loaders().paused()) {
            // Server pause: the loaders hold nothing, so whatever happens in their areas is not their doing.
            strikes.clear();
            counts.clear();
            windowStart = now;
            return;
        }
        double seconds = Math.max(1, (now - windowStart) / 1000.0);
        boolean serverLags = Bukkit.getAverageTickTime() >= s.serverLagMspt();

        Map<UUID, List<Loader>> byOwner = new HashMap<>();
        for (Loader loader : loaders().all()) {
            boolean locked = loaders().lagLocked(loader);
            if (!loader.active() && !locked) {
                activity.remove(loader.id());
                continue;
            }
            World world = loader.world();
            if (world == null) {
                continue;
            }
            Activity a = measure(loader, world, seconds, now);
            if (loader.active()) {
                activity.put(loader.id(), a);
                byOwner.computeIfAbsent(loader.owner(), k -> new ArrayList<>()).add(loader);
            } else if (a.loadedChunks() > 0) {
                // Locked: measured while someone keeps (part of) the area loaded, never judged. Otherwise the last
                // measurement stays, so admins still see why it was locked.
                activity.put(loader.id(), a);
            }
        }
        pools.keySet().retainAll(byOwner.keySet());
        strikes.keySet().retainAll(byOwner.keySet());
        byOwner.forEach((owner, owned) -> {
            Activity pool = pool(owned, now);
            pools.put(owner, pool);
            judge(owner, owned, pool, serverLags, now);
        });
        activity.keySet().removeIf(id -> loaders().byId(id) == null);
        // The web map colours conspicuous loaders: redraw it when that changed.
        java.util.Set<Integer> conspicuousNow = new java.util.HashSet<>();
        for (Loader loader : loaders().all()) {
            if (conspicuous(loader)) {
                conspicuousNow.add(loader.id());
            }
        }
        if (!conspicuousNow.equals(lastConspicuous)) {
            lastConspicuous = conspicuousNow;
            plugin.maps().refreshSoon();
        }
        counts.clear();
        windowStart = now;
        rebuildIndex();
    }

    /** All loaders of one owner together: the sum of their values against the limits of their areas together. */
    private Activity pool(List<Loader> owned, long now) {
        Map<Metric, Double> sum = new EnumMap<>(Metric.class);
        int area = 0;
        int loaded = 0;
        for (Loader loader : owned) {
            Activity a = activity.get(loader.id());
            if (a == null) {
                continue;
            }
            for (Metric metric : Metric.values()) {
                sum.merge(metric, a.value(metric), Double::sum);
            }
            area += a.totalChunks();
            loaded += a.loadedChunks();
        }
        return Activity.of(sum, settings().limitsFor(Math.max(1, area)), now, loaded, area);
    }

    /** Counts entities and items in the loaded chunks of the area and the events since the last sample. */
    private Activity measure(Loader loader, World world, double seconds, long now) {
        Map<Metric, Double> values = new EnumMap<>(Metric.class);
        int entities = 0;
        int items = 0;
        int loaded = 0;
        for (int cx = loader.minChunkX(); cx <= loader.maxChunkX(); cx++) {
            for (int cz = loader.minChunkZ(); cz <= loader.maxChunkZ(); cz++) {
                if (!world.isChunkLoaded(cx, cz)) {
                    continue;
                }
                loaded++;
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
        return Activity.of(values, settings().limitsFor(loader.area()), now, loaded, loader.area());
    }

    /** The pool of one owner over a lock limit often enough: lock their loader that is furthest over its share. */
    private void judge(UUID owner, List<Loader> owned, Activity pool, boolean serverLags, long now) {
        Settings.LagSettings s = settings();
        // Cooldown after an unlock: exactly lock-after-samples measurements, which the strikes below need anyway.
        for (Loader loader : owned) {
            sinceUnlock.computeIfPresent(loader.id(), (k, n) -> n + 1 >= s.lockAfterSamples() ? null : n + 1);
        }
        // Far enough over a lock limit (force-lock-multiplier), the loader is locked even while the server copes.
        boolean forced = s.forceLockMultiplier() > 0 && pool.lockRatio() >= s.forceLockMultiplier();
        if (pool.lockRatio() >= 1 && (!s.onlyWhenServerLags() || serverLags || forced)) {
            int count = strikes.merge(owner, 1, Integer::sum);
            if (count >= s.lockAfterSamples()) {
                Loader culprit = culprit(owned, pool, false);
                if (culprit != null) {
                    lock(culprit, activity.get(culprit.id()), now);
                    strikes.remove(owner);
                    return;
                }
            }
        } else {
            strikes.remove(owner);
        }
        if (!pool.conspicuous()) {
            return;
        }
        Loader worst = null;
        for (Loader loader : owned) {
            Activity a = activity.get(loader.id());
            if (a != null && a.conspicuous() && (worst == null || a.warnRatio() > activity.get(worst.id()).warnRatio())) {
                worst = loader;
            }
        }
        if (worst != null && now - lastWarn.getOrDefault(worst.id(), 0L) >= WARN_INTERVAL) {
            lastWarn.put(worst.id(), now);
            Activity a = activity.get(worst.id());
            Metric metric = a.worst();
            plugin.discord().log("loader-lag-warning", plugin.adminActions().discordTags(worst,
                    "metric", plugin.messages().plain("lag-metric-" + metric.key()),
                    "value", format(a.value(metric)), "limit", format(s.limitsFor(worst.area()).warn(metric))));
        }
    }

    /**
     * The owner's loader to lock: for the metric the pool is furthest over, the loader furthest over its own share of
     * that limit (always at least one is over it when the pool is). Null if none is over its share.
     * @param skipCooldown leave out loaders whose lock was lifted only lately
     */
    private Loader culprit(List<Loader> owned, Activity pool, boolean skipCooldown) {
        Settings.LagSettings s = settings();
        LagLimits poolLimits = s.limitsFor(Math.max(1, pool.totalChunks()));
        Metric metric = null;
        double best = 0;
        for (Metric m : Metric.values()) {
            double limit = poolLimits.lock(m);
            double ratio = limit <= 0 ? 0 : pool.value(m) / limit;
            if (ratio > best) {
                best = ratio;
                metric = m;
            }
        }
        if (metric == null) {
            return null;
        }
        Loader culprit = null;
        double worst = 0;
        for (Loader loader : owned) {
            Activity a = activity.get(loader.id());
            double limit = s.limitsFor(loader.area()).lock(metric);
            if (a == null || limit <= 0 || loaders().lagLocked(loader) || (skipCooldown && sinceUnlock.containsKey(loader.id()))) {
                continue;
            }
            double ratio = a.value(metric) / limit;
            if (ratio >= 1 && ratio > worst) {
                worst = ratio;
                culprit = loader;
            }
        }
        return culprit;
    }

    // ------------------------------------------------------------------ locks

    private void lock(Loader loader, Activity a, long now) {
        LagLimits limits = settings().limitsFor(loader.area());
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
        strikes.remove(loader.owner());

        Messages messages = plugin.messages();
        var tags = tags(loader, lock);
        if (owner != null) {
            messages.send(owner, "lag-locked-owner", tags);
        }
        var teleport = Messages.lazy("teleport", () -> messages.get("lag-teleport-label").clickEvent(
                ClickEvent.runCommand("/mrchunks tp " + LoaderManager.name(loader.owner()) + " " + loader.number())));
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission(AdminActions.PERM_MANAGE) && !staff.equals(owner)) {
                messages.send(staff, "lag-locked-staff", tags, teleport);
            }
        }
        plugin.getLogger().warning("Chunk loader " + Texts.consoleName(plugin, loader) + " was locked because of lag ("
                + metric.key() + " " + format(lock.value()) + ", limit " + format(lock.limit()) + ").");
        plugin.discord().log("loader-lag-locked", plugin.adminActions().discordTags(loader,
                "metric", messages.plain("lag-metric-" + metric.key()),
                "value", format(lock.value()), "limit", format(lock.limit())));

        // MRadmin (if installed) opens a ticket for the owner - after our own messages, so its notice comes last.
        plugin.mradmin().lagLocked(loader, details(loader, lock, a));
    }

    /** The lock as readable plain text (several lines): loader, time, reason, all measured values, server state. */
    private String details(Loader loader, LagLock lock, Activity a) {
        Messages messages = plugin.messages();
        LagLimits limits = settings().limitsFor(loader.area());
        List<String> lines = new ArrayList<>();
        for (Component line : messages.lore("lock-details", tags(loader, lock),
                tag("tps", String.format(java.util.Locale.ROOT, "%.1f", Math.min(20, Bukkit.getTPS()[0]))),
                tag("mspt", String.format(java.util.Locale.ROOT, "%.1f", Bukkit.getAverageTickTime())))) {
            lines.add(PLAIN.serialize(line));
        }
        Activity pool = pools.get(loader.owner());
        if (pool != null) {
            lines.add(PLAIN.serialize(messages.get("lock-details-pool", Texts.loader(plugin, loader),
                    tag("percent", Math.round(pool.lockRatio() * 100)), tag("chunks", pool.totalChunks()))));
        }
        for (Metric metric : Metric.values()) {
            if ((limits.warn(metric) > 0 || limits.lock(metric) > 0) && a.value(metric) > 0) {
                lines.add(PLAIN.serialize(messages.get("lock-details-metric",
                        tag("metric", messages.plain("lag-metric-" + metric.key())),
                        tag("value", format(a.value(metric))),
                        tag("warn", format(limits.warn(metric))),
                        tag("lock", format(limits.lock(metric))))));
            }
        }
        return String.join("\n", lines);
    }

    private static final net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer PLAIN =
            net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();

    /** After an admin lifted a lock: the next lock needs lock-after-samples new measurements over the limit. */
    public void unlocked(Loader loader) {
        sinceUnlock.put(loader.id(), 0);
        strikes.remove(loader.owner());
    }

    /**
     * Called right before a server pause releases all chunks: owners whose loaders are over a lock limit now get the
     * worst of them locked at once, until their pool is below the limit again (the server lags for sure). Otherwise the
     * pause would hide them - their chunks unload, the lag is gone, the pause ends and it starts again, without the
     * cause ever being locked. Loaders still in their cooldown after an unlock are left alone.
     */
    public void lockOffendersBeforePause() {
        if (!settings().enabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        double seconds = Math.max(1, (now - windowStart) / 1000.0);
        Map<UUID, List<Loader>> byOwner = new HashMap<>();
        for (Loader loader : new ArrayList<>(loaders().all())) {
            World world = loader.world();
            if (!loader.active() || world == null) {
                continue;
            }
            activity.put(loader.id(), measure(loader, world, seconds, now));
            byOwner.computeIfAbsent(loader.owner(), k -> new ArrayList<>()).add(loader);
        }
        byOwner.forEach((owner, owned) -> {
            List<Loader> left = new ArrayList<>(owned);
            Activity pool = pool(left, now);
            while (pool.lockRatio() >= 1) {
                Loader culprit = culprit(left, pool, true);
                if (culprit == null) {
                    break;
                }
                lock(culprit, activity.get(culprit.id()), now);
                left.remove(culprit);
                pool = pool(left, now);
            }
            pools.put(owner, pool);
        });
    }

    /** Owners whose loader was locked while they were offline hear about it when they join. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        for (Loader loader : loaders().byOwner(loaders().main(player.getUniqueId()))) {
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

    /** All active loaders of the owner together (last measurement), or null. */
    public Activity pool(UUID owner) {
        return pools.get(owner);
    }

    /**
     * Over a warn limit: the loader over its own share and its owner's loaders together over theirs. A busy loader
     * whose owner's other loaders are quiet is fine.
     */
    public boolean conspicuous(Loader loader) {
        Activity a = activity.get(loader.id());
        Activity pool = pools.get(loader.owner());
        return a != null && a.conspicuous() && (pool == null || pool.conspicuous());
    }

    /** Like {@link #conspicuous(Loader)}, but over a lock limit. */
    public boolean overLock(Loader loader) {
        Activity a = activity.get(loader.id());
        Activity pool = pools.get(loader.owner());
        return a != null && a.lockRatio() >= 1 && (pool == null || pool.lockRatio() >= 1);
    }

    /** Conspicuous and locked loaders, the worst first. */
    public List<Loader> conspicuous() {
        List<Loader> result = new ArrayList<>();
        for (Loader loader : loaders().all()) {
            if (loaders().lagLocked(loader) || conspicuous(loader)) {
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
                Messages.lazy("metric", () -> plugin.messages().plain("lag-metric-" + lock.metric().key())),
                tag("value", format(lock.value())),
                tag("limit", format(lock.limit())),
                Messages.lazy("time", () -> Texts.date(plugin.messages(), lock.since())));
    }

    /** Whole numbers, rates with at most one decimal. */
    public static String format(double value) {
        return value >= 100 || value == Math.rint(value) ? String.valueOf(Math.round(value))
                : String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
