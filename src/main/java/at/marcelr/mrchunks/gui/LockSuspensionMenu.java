package at.marcelr.mrchunks.gui;

import at.marcelr.mrchunks.AdminActions;
import at.marcelr.mrchunks.Loader;
import at.marcelr.mrchunks.MRChunks;
import at.marcelr.mrchunks.Messages;
import at.marcelr.mrchunks.Texts;
import at.marcelr.mrchunks.util.DurationUtil;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

import static at.marcelr.mrchunks.Messages.tag;

/**
 * Suspends the lock for exceeding the limits for one loader, opened from {@link LoaderMenu}
 * (mrchunks.admin.suspendlock). Layout (3 rows), the durations of lag-protection.lock-suspension-durations centred:
 * <pre>
 *  . . . . [loader] . . . .
 *  . . [1h] [3h] [6h] [12h] [24h] . .
 *  [back] . . . [close] . . . [end]     (end only while a suspension runs)
 * </pre>
 */
public final class LockSuspensionMenu extends Menu {

    private static final int MIDDLE = 13;

    private final int loaderId;
    private final Menu parent;

    public LockSuspensionMenu(MRChunks plugin, Player viewer, int loaderId, Menu parent) {
        super(plugin, viewer, 3, title(plugin, loaderId));
        this.loaderId = loaderId;
        this.parent = parent;
    }

    private static net.kyori.adventure.text.Component title(MRChunks plugin, int loaderId) {
        Loader loader = plugin.loaders().byId(loaderId);
        return loader == null ? net.kyori.adventure.text.Component.empty()
                : plugin.messages().get("gui-title-suspend", Texts.loader(plugin, loader));
    }

    @Override
    protected void draw() {
        Loader loader = plugin.loaders().byId(loaderId);
        if (loader == null || !viewer.hasPermission(AdminActions.PERM_SUSPEND_LOCK)) {
            // Deleted meanwhile: back on the next tick, not while drawing.
            Bukkit.getScheduler().runTask(plugin, () -> plugin.messages().run(viewer, parent::open));
            return;
        }
        Messages messages = plugin.messages();
        set(4, LoaderIcons.loader(plugin, loader, false));

        boolean locked = plugin.loaders().lagLocked(loader);
        List<Long> durations = plugin.settings().lag().suspensionDurations();
        for (int i = 0; i < durations.size(); i++) {
            long duration = durations.get(i);
            TagResolver tags = tag("duration", DurationUtil.format(duration));
            set(slot(i, durations.size()), Items.of(Material.CLOCK).name(messages.item("gui-suspend-choice-name", tags))
                    .lore(messages.lore(locked ? "gui-suspend-choice-locked-lore" : "gui-suspend-choice-lore", tags)).build(), click -> {
                clickSound();
                plugin.adminActions().suspendLock(viewer, loader, duration);
                parent.open();
            });
        }

        set(18, Items.of(Material.ARROW).name(messages.item("gui-back")).build(), click -> {
            clickSound();
            parent.open();
        });
        set(22, Items.of(Material.BARRIER).name(messages.item("gui-close")).build(), click -> viewer.closeInventory());
        if (plugin.loaders().lockSuspension(loader) != null) {
            set(26, Items.of(Material.REDSTONE).name(messages.item("gui-suspend-end-name"))
                    .lore(messages.lore("gui-suspend-end-lore")).build(), click -> {
                clickSound();
                plugin.adminActions().endLockSuspension(viewer, loader);
                parent.open();
            });
        }
        for (int row = 0; row < 3; row++) {
            fillRow(row);
        }
    }

    /** Centred around the middle slot; an even number leaves the middle itself free. */
    private static int slot(int index, int count) {
        int slot = MIDDLE - count / 2 + index;
        return count % 2 == 0 && slot >= MIDDLE ? slot + 1 : slot;
    }
}
