package at.marcelr.mrchunks.gui;

import at.marcelr.mrchunks.AdminActions;
import at.marcelr.mrchunks.Loader;
import at.marcelr.mrchunks.MRChunks;
import at.marcelr.mrchunks.Messages;
import at.marcelr.mrchunks.Texts;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import static at.marcelr.mrchunks.Messages.tag;

/**
 * Size of one loader: how many chunks it loads in each direction. Layout (5 rows):
 * <pre>
 *  . . . . [budget] . . . .
 *  . . . . [north]  . . . .
 *  . . . [west] [all] [east] . . .
 *  . . . . [south]  . . . .
 *  [back] . . . [close] . [border] . .
 * </pre>
 * Left click +1 chunk, right click -1 ({@link AdminActions#resize} checks budget, maximum, overlap and dummy).
 * With a dummy on the loader only [all] works - a dummy needs a square with the block in the middle.
 */
public final class SizeMenu extends Menu {

    private static final int BUDGET = 4;
    private static final int NORTH = 13;
    private static final int WEST = 21;
    private static final int ALL = 22;
    private static final int EAST = 23;
    private static final int SOUTH = 31;
    private static final int BACK = 36;
    private static final int CLOSE = 40;
    private static final int BORDER = 42;

    private final int loaderId;
    private final Menu parent;

    public SizeMenu(MRChunks plugin, Player viewer, int loaderId, Menu parent) {
        super(plugin, viewer, 5, title(plugin, loaderId));
        this.loaderId = loaderId;
        this.parent = parent;
    }

    private static net.kyori.adventure.text.Component title(MRChunks plugin, int loaderId) {
        Loader loader = plugin.loaders().byId(loaderId);
        return loader == null ? net.kyori.adventure.text.Component.empty()
                : plugin.messages().get("gui-title-size", Texts.loader(plugin, loader));
    }

    @Override
    protected void draw() {
        Loader loader = plugin.loaders().byId(loaderId);
        if (loader == null) {
            // Deleted meanwhile: back on the next tick, not while drawing.
            Bukkit.getScheduler().runTask(plugin, () -> plugin.messages().run(viewer, parent::open));
            return;
        }
        Messages messages = plugin.messages();
        boolean permitted = AdminActions.canResize(viewer, loader);
        boolean evenOnly = loader.dummy() && plugin.dummies().enabled();
        TagResolver tags = TagResolver.resolver(Texts.loader(plugin, loader),
                plugin.adminActions().budgetTags(Bukkit.getOfflinePlayer(loader.owner())),
                tag("max", plugin.settings().loaders().maxReach() * 2 + 1));

        set(BUDGET, Items.of(Material.EXPERIENCE_BOTTLE).name(messages.item("gui-size-budget-name", tags))
                .lore(messages.lore("gui-size-budget-lore", tags)).build());

        direction(NORTH, Loader.Direction.NORTH, "gui-size-north", loader, permitted && !evenOnly, evenOnly, tags);
        direction(SOUTH, Loader.Direction.SOUTH, "gui-size-south", loader, permitted && !evenOnly, evenOnly, tags);
        direction(WEST, Loader.Direction.WEST, "gui-size-west", loader, permitted && !evenOnly, evenOnly, tags);
        direction(EAST, Loader.Direction.EAST, "gui-size-east", loader, permitted && !evenOnly, evenOnly, tags);

        Items all = Items.of(plugin.loaders().blockOf(loader)).name(messages.item("gui-size-all-name", tags))
                .lore(messages.lore("gui-size-all-lore", tags)).glint(loader.active());
        if (!permitted) {
            all.lore(messages.item("gui-no-permission"));
        }
        set(ALL, all.build(), click -> resize(loader, null, click, permitted));

        set(BACK, Items.of(Material.ARROW).name(messages.item("gui-back")).build(), click -> {
            clickSound();
            parent.open();
        });
        set(CLOSE, Items.of(Material.BARRIER).name(messages.item("gui-close")).build(), click -> viewer.closeInventory());
        boolean showing = plugin.border().isShowing(viewer, loader);
        set(BORDER, Items.of(showing ? Material.SPYGLASS : Material.ENDER_EYE)
                .name(messages.item(showing ? "gui-border-hide-name" : "gui-border-name", tags))
                .lore(messages.lore(showing ? "gui-border-hide-lore" : "gui-size-border-lore", tags)).build(), click -> {
            clickSound();
            if (plugin.border().isShowing(viewer, loader)) {
                plugin.border().hide(viewer);
            } else {
                plugin.border().show(viewer, loader);
            }
            render();
        });
        for (int row = 0; row < 5; row++) {
            fillRow(row);
        }
    }

    private void direction(int slot, Loader.Direction direction, String nameKey, Loader loader, boolean permitted,
                           boolean evenOnly, TagResolver tags) {
        Messages messages = plugin.messages();
        TagResolver all = TagResolver.resolver(tags, tag("reach", loader.reach(direction)));
        Items item = Items.of(permitted ? Material.ARROW : Material.GRAY_DYE).name(messages.item(nameKey, all))
                .lore(messages.lore("gui-size-direction-lore", all));
        if (evenOnly) {
            item.lore(messages.item("gui-size-dummy-even"));
        } else if (!permitted) {
            item.lore(messages.item("gui-no-permission"));
        }
        set(slot, item.build(), click -> resize(loader, direction, click, permitted && !evenOnly));
    }

    private void resize(Loader loader, Loader.Direction direction, ClickType click, boolean permitted) {
        if (!permitted) {
            messages().send(viewer, loader.dummy() && AdminActions.canResize(viewer, loader) ? "resize-dummy" : "no-permission",
                    Texts.loader(plugin, loader));
            sound(Sound.ENTITY_VILLAGER_NO);
            return;
        }
        int delta = click.isRightClick() ? -1 : click.isLeftClick() ? 1 : 0;
        if (delta == 0) {
            return;
        }
        if (plugin.adminActions().resize(viewer, loader, direction, delta)) {
            clickSound();
        } else {
            sound(Sound.ENTITY_VILLAGER_NO);
        }
        render();
    }

    private Messages messages() {
        return plugin.messages();
    }
}
