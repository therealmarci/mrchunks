package at.marcelr.mrchunks.gui;

import at.marcelr.mrchunks.Loader;
import at.marcelr.mrchunks.LoaderStatus;
import at.marcelr.mrchunks.MRChunks;
import at.marcelr.mrchunks.Messages;
import at.marcelr.mrchunks.Texts;
import at.marcelr.mrchunks.lag.Activity;
import at.marcelr.mrchunks.lag.LagLimits;
import at.marcelr.mrchunks.lag.LagMonitor;
import at.marcelr.mrchunks.lag.Metric;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static at.marcelr.mrchunks.Messages.tag;

/**
 * The item that represents one loader in menus, its material showing the {@link LoaderStatus}: the loader block when
 * everything is fine, grey when switched off, magma over a warn limit, a redstone block when locked because of lag,
 * a cobweb during a server pause - each with the reason in the lore.
 */
public final class LoaderIcons {

    /** At most this many metrics are listed in the activity lines (the ones closest to their limit). */
    private static final int ACTIVITY_LINES = 3;

    private LoaderIcons() {
    }

    public static ItemStack loader(MRChunks plugin, Loader loader, boolean clickHint) {
        Messages messages = plugin.messages();
        LoaderStatus status = LoaderStatus.of(plugin, loader);
        Items item = Items.of(status.material(plugin))
                .name(messages.item(status == LoaderStatus.LOCKED ? "gui-loader-name-locked" : "gui-loader-name",
                        Texts.loader(plugin, loader)))
                .lore(messages.lore("gui-loader-lore", Texts.loader(plugin, loader)))
                .glint(status == LoaderStatus.OK || status == LoaderStatus.WARN);
        switch (status) {
            case LOCKED -> item.lore(messages.lore("gui-loader-locked", plugin.lag().tags(loader, plugin.loaders().lagLock(loader))));
            case PAUSED -> item.lore(messages.lore("gui-loader-paused", plugin.pause().tags()));
            case WARN -> {
                Activity a = plugin.lag().activity(loader);
                item.lore(messages.lore("gui-loader-warn",
                        tag("metric", messages.plain("lag-metric-" + a.worst().key())),
                        tag("value", LagMonitor.format(a.value(a.worst()))),
                        tag("limit", LagMonitor.format(plugin.settings().lag().limits().warn(a.worst())))));
            }
            default -> {
            }
        }
        if (clickHint) {
            item.lore(Component.empty()).lore(messages.item("gui-loader-click"));
        }
        return item.build();
    }

    /**
     * What happens in the loader's area according to the last measurement: the metrics closest to their warn limit,
     * each with value and limit.
     */
    public static List<Component> activity(MRChunks plugin, Loader loader) {
        Messages messages = plugin.messages();
        if (!plugin.settings().lag().enabled()) {
            return List.of();
        }
        Activity a = plugin.lag().activity(loader);
        if (a == null) {
            return List.of(messages.item(loader.active() ? "gui-activity-unknown"
                    : plugin.loaders().lagLocked(loader) ? "gui-activity-locked-unknown" : "gui-activity-off"));
        }
        LagLimits limits = plugin.settings().lag().limits();
        List<Component> lines = new ArrayList<>();
        lines.add(messages.item(a.lockRatio() >= 1 ? "gui-activity-header-high"
                : a.conspicuous() ? "gui-activity-header-warn" : "gui-activity-header-ok"));
        List<Metric> metrics = new ArrayList<>(List.of(Metric.values()));
        metrics.removeIf(m -> limits.warn(m) <= 0 || a.value(m) <= 0);
        metrics.sort(Comparator.comparingDouble((Metric m) -> a.value(m) / limits.warn(m)).reversed());
        for (Metric metric : metrics.subList(0, Math.min(ACTIVITY_LINES, metrics.size()))) {
            lines.add(messages.item("gui-activity-line",
                    tag("metric", messages.plain("lag-metric-" + metric.key())),
                    tag("value", LagMonitor.format(a.value(metric))),
                    tag("warn", LagMonitor.format(limits.warn(metric))),
                    tag("lock", LagMonitor.format(limits.lock(metric)))));
        }
        if (plugin.loaders().lagLocked(loader)) {
            // Locked loaders are measured only while someone loads the area: how fresh and how complete it is.
            long age = System.currentTimeMillis() - a.time();
            lines.add(messages.item("gui-activity-measured",
                    tag("ago", at.marcelr.mrchunks.util.DurationUtil.format(age)),
                    tag("loaded", a.loadedChunks()), tag("total", a.totalChunks())));
            if (age > plugin.settings().lag().sampleSeconds() * 2000L) {
                lines.add(messages.item("gui-activity-stale"));
            } else if (a.loadedChunks() < a.totalChunks()) {
                lines.add(messages.item("gui-activity-partial"));
            }
        }
        return lines;
    }
}
