package at.marcelr.mrchunks.lag;

import at.marcelr.mrchunks.AdminActions;
import at.marcelr.mrchunks.Loader;
import at.marcelr.mrchunks.MRChunks;
import at.marcelr.mrchunks.Settings;
import at.marcelr.mrchunks.util.DurationUtil;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static at.marcelr.mrchunks.Messages.tag;

/**
 * Pauses all chunk loaders while the whole server lags, wherever the lag comes from: once the TPS (1-minute average)
 * has been below {@code below-tps} for {@code pause-after-seconds}, every ticket is released; once it has been at
 * least {@code resume-tps} for {@code resume-after-seconds}, the active loaders load their chunks again.
 * The on/off state of the loaders is never changed, so nothing has to be switched on again by hand.
 */
public final class ServerPause {

    private final MRChunks plugin;
    private BukkitTask task;
    /** When the TPS crossed the pause / resume threshold (0 = not crossed). */
    private long belowSince;
    private long aboveSince;
    /** Start of the current pause (0 = not paused). */
    private long pausedSince;

    public ServerPause(MRChunks plugin) {
        this.plugin = plugin;
    }

    private Settings.PauseSettings settings() {
        return plugin.settings().pause();
    }

    /** (Re)starts the TPS check with the current config; switching the pause off ends a running pause. */
    public void start() {
        stop();
        belowSince = 0;
        aboveSince = 0;
        if (!settings().enabled()) {
            if (paused()) {
                resume(tps());
            }
            return;
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::check, 100L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public boolean paused() {
        return plugin.loaders().serverPaused();
    }

    /** Start of the current pause in ms, or 0. */
    public long pausedSince() {
        return pausedSince;
    }

    /** The server's TPS over the last minute (capped at 20). */
    public static double tps() {
        return Math.min(20, Bukkit.getTPS()[0]);
    }

    private void check() {
        Settings.PauseSettings s = settings();
        double tps = tps();
        long now = System.currentTimeMillis();
        if (!paused()) {
            aboveSince = 0;
            if (tps >= s.belowTps()) {
                belowSince = 0;
            } else if (belowSince == 0) {
                belowSince = now;
            }
            if (belowSince != 0 && now - belowSince >= s.pauseAfterSeconds() * 1000L) {
                pause(tps);
            }
        } else {
            belowSince = 0;
            if (tps < s.resumeTps()) {
                aboveSince = 0;
            } else if (aboveSince == 0) {
                aboveSince = now;
            }
            if (aboveSince != 0 && now - aboveSince >= s.resumeAfterSeconds() * 1000L) {
                resume(tps);
            }
        }
    }

    private void pause(double tps) {
        // Lock the loaders that cause the lag first - the pause would otherwise hide them.
        plugin.lag().lockOffendersBeforePause();
        int chunks = plugin.loaders().loadedChunkCount();
        plugin.loaders().setPaused(true);
        pausedSince = System.currentTimeMillis();
        belowSince = 0;
        String value = format(tps);
        plugin.getLogger().warning("Server TPS is " + value + " - all chunk loaders are paused (" + chunks
                + " chunks released) until the TPS is back at " + format(settings().resumeTps()) + ".");
        notify("pause-started", tag("tps", value), tag("resume", format(settings().resumeTps())));
        plugin.discord().log("server-paused", "value", value, "count", chunks);
    }

    private void resume(double tps) {
        long duration = pausedSince == 0 ? 0 : System.currentTimeMillis() - pausedSince;
        plugin.loaders().setPaused(false);
        pausedSince = 0;
        aboveSince = 0;
        String value = format(tps);
        String time = DurationUtil.format(duration);
        plugin.getLogger().info("Server TPS is " + value + " again - chunk loaders resumed after "
                + time + " ("
                + plugin.loaders().loadedChunkCount() + " chunks loaded).");
        notify("pause-ended", tag("tps", value), tag("time", time));
        plugin.discord().log("server-resumed", "value", value, "time", time);
    }

    /** Owners of active loaders and admins who are online. */
    private void notify(String key, TagResolver... tags) {
        if (!settings().notifyOwners()) {
            return;
        }
        Set<UUID> owners = new HashSet<>();
        for (Loader loader : plugin.loaders().all()) {
            if (loader.active()) {
                owners.add(loader.owner());
            }
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (owners.contains(plugin.loaders().main(player.getUniqueId())) || player.hasPermission(AdminActions.PERM_MANAGE)) {
                plugin.messages().send(player, key, tags);
            }
        }
    }

    /** Placeholders for status texts: tps, resume, time (since when). */
    public TagResolver tags() {
        return TagResolver.resolver(
                tag("tps", format(tps())),
                tag("resume", format(settings().resumeTps())),
                tag("time", DurationUtil.format(pausedSince == 0 ? 0 : System.currentTimeMillis() - pausedSince)));
    }

    private static String format(double tps) {
        return String.format(Locale.ROOT, "%.1f", tps);
    }
}
