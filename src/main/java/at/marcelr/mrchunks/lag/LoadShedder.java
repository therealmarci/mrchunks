package at.marcelr.mrchunks.lag;

import at.marcelr.mrchunks.Loader;
import at.marcelr.mrchunks.LoaderManager;
import at.marcelr.mrchunks.MRChunks;
import at.marcelr.mrchunks.Messages;
import at.marcelr.mrchunks.Settings;
import at.marcelr.mrchunks.Texts;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static at.marcelr.mrchunks.Messages.tag;

/**
 * Staged shutdown: protects the server's performance against the load of many loaders together, each of which may be
 * under its own lag limits. Once a second the average tick time is taken; over {@code average-seconds} it gives the
 * stage:
 * <ul>
 *   <li>up to {@code warn-mspt}: fine</li>
 *   <li>above: warning to the team (chat + Discord); it ends only after {@code min-minutes} without being above</li>
 *   <li>above {@code shutdown-mspt}: every {@code step-seconds} the active loader with the highest load (share of its
 *       lag limits) is disabled by lag - it releases its chunks and its dummy but stays switched on</li>
 * </ul>
 * Disabled loaders stay off at least {@code min-minutes}. Afterwards, while the server is at {@code warn-mspt} or
 * lower, they come back one per step, the most recently disabled first. A loader disabled more than
 * {@code max-shutdowns} times within {@code window-minutes} is locked (only admins can lift that).
 * Nothing is saved: a restart brings every loader back. During a server pause nothing happens.
 */
public final class LoadShedder implements Listener {

    private static final String PERM_TEAM = "mrchunks.admin.gui";

    private final MRChunks plugin;
    private BukkitTask task;
    /** Average tick time of the last seconds (ring), filled once a second. */
    private double[] samples = new double[0];
    private int filled;
    private int next;
    /** Start of the current warning (0 = none) and when the server was last above warn-mspt. */
    private long warnedSince;
    private long lastAbove;
    /** Last shutdown or return of a loader (one per step). */
    private long lastStep;
    /** loader id -> times it was disabled by lag (within the window). */
    private final Map<Integer, Deque<Long>> history = new HashMap<>();
    /** Loaders disabled while their owner was offline: told on the next join (if still disabled). */
    private final Set<Integer> untold = new HashSet<>();

    public LoadShedder(MRChunks plugin) {
        this.plugin = plugin;
    }

    private Settings.ShedSettings settings() {
        return plugin.settings().shed();
    }

    private LoaderManager loaders() {
        return plugin.loaders();
    }

    // ------------------------------------------------------------------- life

    /** (Re)starts with the current config; switching it off brings every disabled loader back. */
    public void start() {
        stop();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        samples = new double[settings().averageSeconds()];
        filled = 0;
        next = 0;
        if (!settings().enabled()) {
            restoreAll();
            warnedSince = 0;
            return;
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::check, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        HandlerList.unregisterAll(this);
    }

    // ------------------------------------------------------------------ check

    private void check() {
        Settings.ShedSettings s = settings();
        samples[next] = Bukkit.getAverageTickTime();
        next = (next + 1) % samples.length;
        filled = Math.min(filled + 1, samples.length);
        if (filled < samples.length) {
            return; // a full window first (after start and reload)
        }
        double mspt = average();
        long now = System.currentTimeMillis();
        long min = s.minMinutes() * 60_000L;

        if (mspt > s.warnMspt()) {
            lastAbove = now;
            if (warnedSince == 0) {
                warnedSince = now;
                warn(mspt);
            }
        } else if (warnedSince != 0 && now - lastAbove >= min && loaders().shedIds().isEmpty()) {
            long duration = now - warnedSince;
            warnedSince = 0;
            normal(mspt, duration);
        }

        if (loaders().paused() || now - lastStep < s.stepSeconds() * 1000L) {
            return;
        }
        if (mspt > s.shutdownMspt()) {
            Loader loader = heaviest();
            if (loader != null) {
                lastStep = now;
                shutDown(loader, mspt, now);
            }
        } else if (mspt <= s.warnMspt() && restoreOne(now, min, mspt)) {
            lastStep = now;
        }
    }

    private double average() {
        double sum = 0;
        for (double sample : samples) {
            sum += sample;
        }
        return sum / samples.length;
    }

    /** The loader holding chunks with the highest load (share of its lock limits), the bigger one if equal. */
    private Loader heaviest() {
        Loader best = null;
        double bestRatio = -1;
        for (Loader loader : loaders().all()) {
            if (!loader.active() || loaders().shed(loader) || loaders().lagLocked(loader) || !loaders().loading(loader)) {
                continue;
            }
            Activity a = plugin.lag().activity(loader);
            double ratio = a == null ? 0 : a.lockRatio();
            if (ratio > bestRatio || (ratio == bestRatio && best != null && loader.area() > best.area())) {
                best = loader;
                bestRatio = ratio;
            }
        }
        return best;
    }

    private void shutDown(Loader loader, double mspt, long now) {
        Settings.ShedSettings s = settings();
        Deque<Long> times = history.computeIfAbsent(loader.id(), k -> new ArrayDeque<>());
        times.addLast(now);
        while (!times.isEmpty() && now - times.peekFirst() > s.windowMinutes() * 60_000L) {
            times.removeFirst();
        }
        if (s.maxShutdowns() > 0 && times.size() > s.maxShutdowns()) {
            // Too often: lock instead of disabling it once more (the lock switches it off).
            int count = times.size();
            history.remove(loader.id());
            untold.remove(loader.id());
            plugin.lag().lockRepeated(loader, count);
            return;
        }
        loaders().shed(loader, now);
        Activity a = plugin.lag().activity(loader);
        String load = a == null ? "0" : String.valueOf(Math.round(a.lockRatio() * 100));
        plugin.getLogger().warning("Server tick time is " + format(mspt) + " ms (limit " + format(s.shutdownMspt())
                + ") - chunk loader " + Texts.consoleName(plugin, loader) + " (load " + load + "%) is disabled by lag"
                + " for at least " + s.minMinutes() + " min (" + times.size() + "x in " + s.windowMinutes() + " min).");
        TagResolver tags = tags(loader, mspt, times.size(), load);
        Player owner = Bukkit.getPlayer(loader.owner());
        if (owner != null) {
            plugin.messages().send(owner, "shed-owner", tags);
        } else {
            untold.add(loader.id());
        }
        var teleport = Messages.lazy("teleport", () -> plugin.messages().get("lag-teleport-label").clickEvent(
                ClickEvent.runCommand("/mrchunks tp " + LoaderManager.name(loader.owner()) + " " + loader.number())));
        team("shed-team", owner, tags, teleport);
        plugin.discord().log("loader-lag-shutdown", plugin.adminActions().discordTags(loader,
                "mspt", format(mspt), "limit", format(s.shutdownMspt()), "load", load, "count", times.size(),
                "minutes", s.windowMinutes(), "min", s.minMinutes()));
    }

    /** One disabled loader comes back: the most recently disabled one whose minimum time is over. */
    private boolean restoreOne(long now, long min, double mspt) {
        List<Integer> ids = new ArrayList<>(loaders().shedIds());
        for (int i = ids.size() - 1; i >= 0; i--) {
            int id = ids.get(i);
            Loader loader = loaders().byId(id);
            if (loader == null || !loader.active()) {
                // Deleted or switched off meanwhile: nothing to load again.
                loaders().forgetShed(id);
                untold.remove(id);
                continue;
            }
            long since = loaders().shedSince(loader);
            if (now - since < min) {
                continue;
            }
            loaders().unshed(loader);
            untold.remove(id);
            String time = at.marcelr.mrchunks.util.DurationUtil.format(now - since);
            plugin.getLogger().info("Server tick time is " + format(mspt) + " ms again - chunk loader "
                    + Texts.consoleName(plugin, loader) + " loads again (" + loaders().shedIds().size()
                    + " still disabled by lag).");
            TagResolver tags = TagResolver.resolver(Texts.loader(plugin, loader), tag("mspt", format(mspt)),
                    tag("time", time));
            Player owner = Bukkit.getPlayer(loader.owner());
            if (owner != null) {
                plugin.messages().send(owner, "shed-back-owner", tags);
            }
            team("shed-back-team", owner, tags);
            plugin.discord().log("loader-lag-back", plugin.adminActions().discordTags(loader,
                    "mspt", format(mspt), "time", time));
            return true;
        }
        return false;
    }

    /** All disabled loaders come back at once (staged shutdown switched off). */
    private void restoreAll() {
        for (int id : loaders().shedIds()) {
            Loader loader = loaders().byId(id);
            if (loader != null) {
                loaders().unshed(loader);
            } else {
                loaders().forgetShed(id);
            }
        }
        untold.clear();
    }

    private void warn(double mspt) {
        Settings.ShedSettings s = settings();
        plugin.getLogger().warning("Server tick time is " + format(mspt) + " ms (warning from " + format(s.warnMspt())
                + " ms, chunk loaders are disabled from " + format(s.shutdownMspt()) + " ms).");
        team("shed-warning", null, tags(mspt));
        plugin.discord().log("server-lag-warning", "mspt", format(mspt), "warn", format(s.warnMspt()),
                "limit", format(s.shutdownMspt()), "count", loaders().loadedChunkCount());
    }

    private void normal(double mspt, long duration) {
        String time = at.marcelr.mrchunks.util.DurationUtil.format(duration);
        plugin.getLogger().info("Server tick time is " + format(mspt) + " ms again - warning over after " + time + ".");
        team("shed-normal", null, TagResolver.resolver(tags(mspt), tag("time", time)));
        plugin.discord().log("server-lag-normal", "mspt", format(mspt), "time", time);
    }

    /** Team members online (admin menu), except {@code except} (already told as owner). */
    private void team(String key, Player except, TagResolver... tags) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission(PERM_TEAM) && !player.equals(except)) {
                plugin.messages().send(player, key, tags);
            }
        }
    }

    /** Owners whose loader was disabled while they were offline hear about it when they join (if it still is). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        for (Loader loader : loaders().byOwner(loaders().main(player.getUniqueId()))) {
            if (untold.remove(loader.id()) && loaders().shed(loader)) {
                plugin.messages().send(player, "shed-join", Texts.loader(plugin, loader),
                        Messages.lazy("time", () -> Texts.date(plugin.messages(), loaders().shedSince(loader))));
            }
        }
    }

    // ----------------------------------------------------------------- access

    /** The average tick time over the window (ms), or the current one while the window is not full yet. */
    public double mspt() {
        return filled < samples.length || samples.length == 0 ? Bukkit.getAverageTickTime() : average();
    }

    /** Whether the warning is on right now. */
    public boolean warning() {
        return warnedSince != 0;
    }

    /** Placeholders for texts about a disabled loader: loader, since, back (earliest return), mspt. */
    public TagResolver tags(Loader loader) {
        long since = loaders().shedSince(loader);
        return TagResolver.resolver(Texts.loader(plugin, loader), tags(mspt()),
                Messages.lazy("time", () -> Texts.date(plugin.messages(), since)),
                Messages.lazy("back", () -> Texts.date(plugin.messages(), since + settings().minMinutes() * 60_000L)));
    }

    private TagResolver tags(Loader loader, double mspt, int count, String load) {
        return TagResolver.resolver(Texts.loader(plugin, loader), tags(mspt), tag("count", count), tag("load", load));
    }

    /** Placeholders for status texts: mspt (average now), warn, limit, min, minutes, max. */
    public TagResolver tags() {
        return tags(mspt());
    }

    private TagResolver tags(double mspt) {
        Settings.ShedSettings s = settings();
        return TagResolver.resolver(tag("mspt", format(mspt)), tag("warn", format(s.warnMspt())),
                tag("limit", format(s.shutdownMspt())), tag("min", s.minMinutes()), tag("minutes", s.windowMinutes()),
                tag("max", s.maxShutdowns() + 1));
    }

    private static String format(double mspt) {
        return String.format(Locale.ROOT, "%.1f", mspt);
    }
}
