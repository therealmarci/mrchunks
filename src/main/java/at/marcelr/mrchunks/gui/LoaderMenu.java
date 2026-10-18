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

import static at.marcelr.mrchunks.Messages.tag;

/**
 * Menu of one loader (right-click on the block, or from a list). Layout (3 rows):
 * <pre>
 *  . . . . [info] . . . .
 *  . [toggle] [rename] . [border] . [teleport] [delete] .
 *  [back] . . . [close] . . . .
 * </pre>
 * Toggle and delete need ownership or mrchunks.admin.manage; deleting needs a second click to confirm.
 */
public final class LoaderMenu extends Menu {

    private final int loaderId;
    private final Menu parent;
    /** Delete is armed until this time (ms) after the first click. */
    private long confirmUntil;

    public LoaderMenu(MRChunks plugin, Player viewer, int loaderId, Menu parent) {
        super(plugin, viewer, 3, title(plugin, loaderId));
        this.loaderId = loaderId;
        this.parent = parent;
    }

    private static net.kyori.adventure.text.Component title(MRChunks plugin, int loaderId) {
        Loader loader = plugin.loaders().byId(loaderId);
        return loader == null ? net.kyori.adventure.text.Component.empty()
                : plugin.messages().get("gui-title-loader", Texts.loader(plugin, loader));
    }

    @Override
    protected void draw() {
        Loader loader = plugin.loaders().byId(loaderId);
        if (loader == null) {
            // Deleted meanwhile (by an admin, a ban, ...): leave on the next tick, not while drawing.
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (parent != null) {
                    parent.open();
                } else {
                    viewer.closeInventory();
                }
            });
            return;
        }
        Messages messages = plugin.messages();
        AdminActions actions = plugin.adminActions();
        TagResolver tags = TagResolver.resolver(Texts.loader(plugin, loader),
                tag("seconds", plugin.settings().border().durationSeconds()));
        boolean manage = AdminActions.canManage(viewer, loader);

        set(4, LoaderIcons.loader(plugin, loader, false));

        if (loader.active()) {
            button(10, Material.RED_DYE, "gui-toggle-off-name", "gui-toggle-off-lore", manage,
                    () -> actions.toggle(viewer, loader), tags);
        } else {
            button(10, Material.LIME_DYE, "gui-toggle-on-name", "gui-toggle-on-lore", manage,
                    () -> actions.toggle(viewer, loader), tags);
        }

        button(11, Material.NAME_TAG, "gui-rename-name", "gui-rename-lore", manage,
                () -> RenameDialog.open(plugin, viewer, loader, parent), tags);

        if (plugin.border().isShowing(viewer, loader)) {
            button(13, Material.SPYGLASS, "gui-border-hide-name", "gui-border-hide-lore", true, () -> {
                plugin.border().hide(viewer);
                messages.send(viewer, "border-hidden", tags);
            }, tags);
        } else {
            button(13, Material.ENDER_EYE, "gui-border-name", "gui-border-lore", true, () -> {
                viewer.closeInventory();
                plugin.border().show(viewer, loader);
                messages.send(viewer, "border-shown", tags);
            }, tags);
        }

        if (viewer.hasPermission(AdminActions.PERM_TELEPORT)) {
            button(15, Material.COMPASS, "gui-teleport-name", null, true, () -> {
                viewer.closeInventory();
                actions.teleport(viewer, loader);
            }, tags);
        }

        boolean armed = System.currentTimeMillis() < confirmUntil;
        button(16, armed ? Material.TNT_MINECART : Material.TNT,
                armed ? "gui-delete-confirm-name" : "gui-delete-name",
                armed ? "gui-delete-confirm-lore" : "gui-delete-lore", manage, () -> {
                    if (System.currentTimeMillis() < confirmUntil) {
                        actions.delete(viewer, loader);
                        if (parent != null) {
                            parent.open();
                        } else {
                            viewer.closeInventory();
                        }
                    } else {
                        confirmUntil = System.currentTimeMillis() + plugin.settings().gui().confirmSeconds() * 1000L;
                        sound(Sound.BLOCK_NOTE_BLOCK_BASS);
                        messages.send(viewer, "delete-confirm", tags);
                    }
                }, tags);

        if (parent != null) {
            set(18, Items.of(Material.ARROW).name(messages.item("gui-back")).build(), click -> {
                clickSound();
                parent.open();
            });
        }
        set(22, Items.of(Material.BARRIER).name(messages.item("gui-close")).build(), click -> viewer.closeInventory());

        for (int row = 0; row < 3; row++) {
            fillRow(row);
        }
    }

    /** A button that is either usable or shows "no permission" and refuses the click. */
    private void button(int slot, Material material, String nameKey, String loreKey, boolean permitted,
                        Runnable action, TagResolver tags) {
        Messages messages = plugin.messages();
        Items item = Items.of(material).name(messages.item(nameKey, tags));
        if (loreKey != null) {
            item.lore(messages.lore(loreKey, tags));
        }
        if (!permitted) {
            item.lore(messages.item("gui-no-permission"));
        }
        set(slot, item.build(), click -> {
            if (!permitted) {
                messages.send(viewer, "no-permission");
                sound(Sound.ENTITY_VILLAGER_NO);
                return;
            }
            clickSound();
            action.run();
            if (viewer.getOpenInventory().getTopInventory().getHolder(false) == this) {
                render();
            }
        });
    }
}
