package at.marcelr.mrchunks.gui;

import at.marcelr.mrchunks.AdminActions;
import at.marcelr.mrchunks.Loader;
import at.marcelr.mrchunks.MRChunks;
import at.marcelr.mrchunks.Messages;
import at.marcelr.mrchunks.Texts;
import at.marcelr.mrchunks.lag.LagLock;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

import static at.marcelr.mrchunks.Messages.tag;

/**
 * Menu of one loader (right-click on the block, or from a list). Layout (3 rows):
 * <pre>
 *  . . . . [info] . . . .
 *  [toggle] [members] [dummy] [size] [border] . [block] [rename] [delete]   (dummy only with dummies.enabled,
 *                                                                          block only with item.block-choices)
 *  [back] . . . [close] . . . .          (with mrchunks.admin.teleport: [back] . . [close] . [teleport] . . .)
 * </pre>
 * Toggle needs ownership, an invite or mrchunks.admin.manage; rename, invites and delete only ownership or the admin
 * permission. Deleting needs a second click to confirm.
 */
public final class LoaderMenu extends Menu {

    private final int loaderId;
    /** What "back" does (reopen the list it came from, or return to e.g. MRadmin); null = no back button. */
    private final Runnable back;
    /** Delete is armed until this time (ms) after the first click. */
    private long confirmUntil;

    public LoaderMenu(MRChunks plugin, Player viewer, int loaderId, Menu parent) {
        this(plugin, viewer, loaderId, parent == null ? null : (Runnable) parent::open);
    }

    public LoaderMenu(MRChunks plugin, Player viewer, int loaderId, Runnable back) {
        super(plugin, viewer, 3, title(plugin, loaderId));
        this.loaderId = loaderId;
        this.back = back;
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
                if (back != null) {
                    back.run();
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
        boolean toggle = actions.canToggle(viewer, loader);

        ItemStack info = LoaderIcons.loader(plugin, loader, false);
        List<Component> activity = LoaderIcons.activity(plugin, loader);
        if (!activity.isEmpty()) {
            info.editMeta(meta -> {
                List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
                lore.add(Component.empty());
                lore.addAll(activity);
                meta.lore(lore);
            });
        }
        set(4, info);

        LagLock lock = plugin.loaders().lagLock(loader);
        TagResolver lockTags = lock == null ? tags : TagResolver.resolver(tags, plugin.lag().tags(loader, lock));
        if (lock != null && viewer.hasPermission(AdminActions.PERM_MANAGE)) {
            button(9, Material.IRON_DOOR, "gui-unlock-name", "gui-unlock-lore", true,
                    () -> actions.unlock(viewer, loader), lockTags);
        } else if (lock != null) {
            set(9, Items.of(Material.REDSTONE_BLOCK).name(messages.item("gui-locked-name", lockTags))
                    .lore(messages.lore("gui-locked-lore", lockTags)).build());
        } else if (loader.active()) {
            button(9, Material.RED_DYE, "gui-toggle-off-name", "gui-toggle-off-lore", toggle,
                    () -> actions.toggle(viewer, loader), tags);
        } else {
            button(9, Material.LIME_DYE, "gui-toggle-on-name", "gui-toggle-on-lore", toggle,
                    () -> actions.toggle(viewer, loader), tags);
        }

        if (manage && !plugin.settings().item().blockChoices().isEmpty()) {
            Material current = plugin.loaders().blockOf(loader);
            button(15, current, "gui-block-name", "gui-block-lore", actions.canChangeBlock(viewer, loader),
                    () -> new BlockMenu(plugin, viewer, loader.id(), this).open(),
                    TagResolver.resolver(tags, tag("block", Component.translatable(current))));
        }

        button(16, Material.NAME_TAG, "gui-rename-name", "gui-rename-lore", manage,
                () -> RenameDialog.open(plugin, viewer, loader, back), tags);

        if (plugin.settings().invites().enabled() && AdminActions.canManageInvites(viewer, loader.owner())) {
            int count = plugin.loaders().members(loader.owner(), loader).size();
            button(10, Material.PLAYER_HEAD, "gui-members-name", "gui-members-lore", true,
                    () -> new MembersMenu(plugin, viewer, loader.owner(), loader.id(), this).open(),
                    TagResolver.resolver(tags, tag("count", count)));
        }

        if (plugin.dummies().enabled()) {
            dummyButton(loader, manage, tags);
        }

        // Everyone who can open the menu sees the size; changing it is checked in the size menu.
        set(12, Items.of(Material.MAP).name(messages.item("gui-size-name", tags))
                .lore(messages.lore(AdminActions.canResize(viewer, loader) ? "gui-size-lore" : "gui-size-view-lore", tags))
                .build(), click -> {
            clickSound();
            new SizeMenu(plugin, viewer, loader.id(), this).open();
        });

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

        boolean armed = System.currentTimeMillis() < confirmUntil;
        button(17, armed ? Material.TNT_MINECART : Material.TNT,
                armed ? "gui-delete-confirm-name" : "gui-delete-name",
                armed ? "gui-delete-confirm-lore" : "gui-delete-lore", manage, () -> {
                    if (System.currentTimeMillis() < confirmUntil) {
                        actions.delete(viewer, loader);
                        if (back != null) {
                            back.run();
                        } else {
                            viewer.closeInventory();
                        }
                    } else {
                        confirmUntil = System.currentTimeMillis() + plugin.settings().gui().confirmSeconds() * 1000L;
                        sound(Sound.BLOCK_NOTE_BLOCK_BASS);
                        messages.send(viewer, "delete-confirm", tags);
                    }
                }, tags);

        if (back != null) {
            set(18, Items.of(Material.ARROW).name(messages.item("gui-back")).build(), click -> {
                clickSound();
                back.run();
            });
        }
        // Admins with teleport: close moves one to the left, the middle stays glass, teleport on the right.
        boolean teleport = viewer.hasPermission(AdminActions.PERM_TELEPORT);
        set(teleport ? 21 : 22, Items.of(Material.BARRIER).name(messages.item("gui-close")).build(),
                click -> viewer.closeInventory());
        if (teleport) {
            button(23, Material.COMPASS, "gui-teleport-name", null, true, () -> {
                viewer.closeInventory();
                actions.teleport(viewer, loader);
            }, tags);
        }

        for (int row = 0; row < 3; row++) {
            fillRow(row);
        }
    }

    /** Dummy player on/off with the owner's dummies in use and whether it sits there right now. */
    private void dummyButton(Loader loader, boolean manage, TagResolver tags) {
        TagResolver all = TagResolver.resolver(tags,
                plugin.adminActions().dummyTags(Bukkit.getOfflinePlayer(loader.owner())));
        String lore;
        if (!at.marcelr.mrchunks.dummy.Dummies.fits(loader)) {
            // Only on a centred square of at least 5x5 (otherwise the dummy simulates chunks outside the area).
            lore = loader.dummy() ? "gui-dummy-square-waiting-lore" : "gui-dummy-square-lore";
        } else if (!loader.dummy()) {
            lore = "gui-dummy-on-lore";
        } else if (plugin.dummies().dummyOf(loader) != null) {
            lore = "gui-dummy-off-lore";
        } else {
            lore = "gui-dummy-waiting-lore";
        }
        button(11, loader.dummy() ? Material.ARMOR_STAND : Material.LEATHER_HELMET,
                loader.dummy() ? "gui-dummy-off-name" : "gui-dummy-on-name", lore, manage,
                () -> plugin.adminActions().toggleDummy(viewer, loader), all);
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
