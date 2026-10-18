package at.marcelr.mrchunks.gui;

import at.marcelr.mrchunks.Loader;
import at.marcelr.mrchunks.MRChunks;
import at.marcelr.mrchunks.Messages;
import at.marcelr.mrchunks.Texts;
import at.marcelr.mrchunks.lag.Activity;
import at.marcelr.mrchunks.lag.LagLimits;
import at.marcelr.mrchunks.lag.LagLock;
import at.marcelr.mrchunks.lag.LagMonitor;
import at.marcelr.mrchunks.lag.Metric;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static at.marcelr.mrchunks.Messages.tag;

/**
 * The item that represents one loader in menus: its block material while active, grey while off, a redstone block while
 * locked because of lag (with the reason).
 */
public final class LoaderIcons {

    /** At most this many metrics are listed in the activity lines (the ones closest to their limit). */
    private static final int ACTIVITY_LINES = 3;

    private LoaderIcons() {
    }

    public static ItemStack loader(MRChunks plugin, Loader loader, boolean clickHint) {
        Messages messages = plugin.messages();
        LagLock lock = plugin.loaders().lagLock(loader);
        Material material = lock != null ? Material.REDSTONE_BLOCK
                : loader.active() ? plugin.settings().item().material() : Material.GRAY_CONCRETE;
        Items item = Items.of(material)
                .name(messages.item(lock != null ? "gui-loader-name-locked" : "gui-loader-name", Texts.loader(plugin, loader)))
                .lore(messages.lore("gui-loader-lore", Texts.loader(plugin, loader)))
                .glint(loader.active());
        if (lock != null) {
            item.lore(messages.lore("gui-loader-locked", plugin.lag().tags(loader, lock)));
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
            return List.of(messages.item(loader.active() ? "gui-activity-unknown" : "gui-activity-off"));
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
        return lines;
    }
}
