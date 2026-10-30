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
 * Every loader is judged on its own against the limits of its area (see {@link Settings.LagSettings#limitsFor}): up to
 * 3x3 fixed limits, above that they grow with the area, never below the 3x3 ones. When a loader is over a lock limit
 * {@code lock-after-samples} times in a row - by default only while the server is actually lagging, or without lag
 * once it reaches {@code force-lock-multiplier} times the limit - only that loader is locked.
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

    /** loader id -> last measurement, judged against the limits of the loader's area */
    private final Map<Integer, Activity> activity = new HashMap<>();
    /** loader id -> samples in a row over a lock limit */
    private final Map<Integer, Integer> strikes = new HashMap<>();
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
        if (loaders().paused() || plugin.startupGrace()) {
            // Server pause or startup: the loaders hold nothing / every tick is slow, so nothing is judged.
            strikes.clear();
            counts.clear();
            windowStart = now;
            return;
        }
        double seconds = Math.max(1, (now - windowStart) / 1000.0);
        boolean serverLags = Bukkit.getAverageTickTime() >= s.serverLagMspt();

        java.util.Set<Integer> judged = new java.util.HashSet<>();
        for (Loader loader : new ArrayList<>(loaders().all())) {
            boolean locked = loaders().lagLocked(loader);
            if (!loader.active() && !locked) {
                activity.remove(loader.id());
                continue;
            }
            World world = loader.world();
            if (world == null) {
                continue;
            }
            if (loader.active() && loaders().shed(loader)) {
                // Disabled by lag: holds no chunks, so nothing measured there is its doing - the last measurement
                // stays (shows why it was chosen), it is not judged.
                continue;
            }
            Activity a = measure(loader, world, seconds, now);
            if (loader.active()) {
                activity.put(loader.id(), a);
                judged.add(loader.id());
                judge(loader, a, serverLags, now);
            } else if (a.loadedChunks() > 0) {
                // Locked: measured while someone keeps (part of) the area loaded, never judged. Otherwise the last
                // measurement stays, so admins still see why it was locked.
                activity.put(loader.id(), a);
            }
        }
        strikes.keySet().retainAll(judged);
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

    /** One loader over a lock limit often enough: lock it. Over a warn limit: a note in Discord (at most hourly). */
    private void judge(Loader loader, Activity a, boolean serverLags, long now) {
        Settings.LagSettings s = settings();
        // Cooldown after an unlock: exactly lock-after-samples measurements, which the strikes below need anyway.
        sinceUnlock.computeIfPresent(loader.id(), (k, n) -> n + 1 >= s.lockAfterSamples() ? null : n + 1);
        // Far enough over a lock limit (force-lock-multiplier), the loader is locked even while the server copes.
        boolean forced = s.forceLockMultiplier() > 0 && a.lockRatio() >= s.forceLockMultiplier();
        if (a.lockRatio() >= 1 && (!s.onlyWhenServerLags() || serverLags || forced)) {
            int count = strikes.merge(loader.id(), 1, Integer::sum);
            if (count >= s.lockAfterSamples()) {
                lock(loader, a, now, 0);
                return;
            }
        } else {
            strikes.remove(loader.id());
        }
        if (a.conspicuous() && now - lastWarn.getOrDefault(loader.id(), 0L) >= WARN_INTERVAL) {
            lastWarn.put(loader.id(), now);
            Metric metric = a.worst();
            plugin.discord().log("loader-lag-warning", plugin.adminActions().discordTags(loader,
                    "metric", plugin.messages().plain("lag-metric-" + metric.key()),
                    "value", format(a.value(metric)), "limit", format(s.limitsFor(loader.area()).warn(metric))));
        }
    }

    // ------------------------------------------------------------------ locks

    /**
     * Locks a loader the staged shutdown had to disable too often ({@code shutdowns} times within its window).
     * The lock shows the loader's worst metric of its last measurement.
     */
    public void lockRepeated(Loader loader, int shutdowns) {
        long now = System.currentTimeMillis();
        Activity a = activity.get(loader.id());
        if (a == null) {
            a = Activity.of(java.util.Map.of(), settings().limitsFor(loader.area()), now, 0, loader.area());
        }
        lock(loader, a, now, shutdowns);
    }

    private void lock(Loader loader, Activity a, long now, int shutdowns) {
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
        LagLock lock = new LagLock(now, metric, a.value(metric), limits.lock(metric), owner != null, shutdowns);
        loaders().lagLock(loader, lock);
        strikes.remove(loader.id());

        Messages messages = plugin.messages();
        var tags = tags(loader, lock);
        String kind = lock.repeated() ? "lag-locked-repeat-" : "lag-locked-";
        if (owner != null) {
            messages.send(owner, kind + "owner", tags);
        }
        var teleport = Messages.lazy("teleport", () -> messages.get("lag-teleport-label").clickEvent(
                ClickEvent.runCommand("/mrchunks tp " + LoaderManager.name(loader.owner()) + " " + loader.number())));
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission(AdminActions.PERM_MANAGE) && !staff.equals(owner)) {
                messages.send(staff, kind + "staff", tags, teleport);
            }
        }
        plugin.getLogger().warning("Chunk loader " + Texts.consoleName(plugin, loader) + " was locked because of lag ("
                + (lock.repeated() ? "disabled " + shutdowns + " times by the staged shutdown, " : "")
                + metric.key() + " " + format(lock.value()) + ", limit " + format(lock.limit()) + ").");
        plugin.discord().log(lock.repeated() ? "loader-lag-repeat-locked" : "loader-lag-locked",
                plugin.adminActions().discordTags(loader, "count", shutdowns,
                "minutes", plugin.settings().shed().windowMinutes(),
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
        if (lock.repeated()) {
            lines.add(PLAIN.serialize(messages.get("lock-details-repeat", tags(loader, lock))));
        }
        for (Component line : messages.lore("lock-details", tags(loader, lock),
                tag("tps", String.format(java.util.Locale.ROOT, "%.1f", Math.min(20, Bukkit.getTPS()[0]))),
                tag("mspt", String.format(java.util.Locale.ROOT, "%.1f", Bukkit.getAverageTickTime())))) {
            lines.add(PLAIN.serialize(line));
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
        strikes.remove(loader.id());
    }

    /**
     * Called right before a server pause releases all chunks: every loader over a lock limit now is locked at once
     * (the server lags for sure). Otherwise the pause would hide them - their chunks unload, the lag is gone, the pause
     * ends and it starts again, without the cause ever being locked. Loaders still in their cooldown after an unlock
     * are left alone.
     */
    public void lockOffendersBeforePause() {
        if (!settings().enabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        double seconds = Math.max(1, (now - windowStart) / 1000.0);
        for (Loader loader : new ArrayList<>(loaders().all())) {
            World world = loader.world();
            if (!loader.active() || world == null || loaders().shed(loader)) {
                continue;
            }
            Activity a = measure(loader, world, seconds, now);
            activity.put(loader.id(), a);
            if (a.lockRatio() >= 1 && !sinceUnlock.containsKey(loader.id())) {
                lock(loader, a, now, 0);
            }
        }
    }

    /** Owners whose loader was locked while they were offline hear about it when they join. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        for (Loader loader : loaders().byOwner(loaders().main(player.getUniqueId()))) {
            LagLock lock = loaders().lagLock(loader);
            if (lock != null && !lock.notified()) {
                plugin.messages().send(player, lock.repeated() ? "lag-locked-repeat-join" : "lag-locked-join",
                        tags(loader, lock));
                loaders().updateLagLock(loader, lock.withNotified());
            }
        }
    }

    // ----------------------------------------------------------------- access

    /** The last measurement of a loader, or null (not measured yet, inactive, measuring off). */
    public Activity activity(Loader loader) {
        return activity.get(loader.id());
    }

    /** Over a warn limit of its own area (last measurement). */
    public boolean conspicuous(Loader loader) {
        Activity a = activity.get(loader.id());
        return a != null && a.conspicuous();
    }

    /** Like {@link #conspicuous(Loader)}, but over a lock limit. */
    public boolean overLock(Loader loader) {
        Activity a = activity.get(loader.id());
        return a != null && a.lockRatio() >= 1;
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
                tag("count", lock.shutdowns()),
                tag("minutes", plugin.settings().shed().windowMinutes()),
                Messages.lazy("time", () -> Texts.date(plugin.messages(), lock.since())));
    }

    /** Whole numbers, rates with at most one decimal. */
    public static String format(double value) {
        return value >= 100 || value == Math.rint(value) ? String.valueOf(Math.round(value))
                : String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
