package at.marcelr.mrchunks.gui;

import at.marcelr.mrchunks.AdminActions;
import at.marcelr.mrchunks.Loader;
import at.marcelr.mrchunks.MRChunks;
import at.marcelr.mrchunks.Messages;
import at.marcelr.mrchunks.Texts;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import static at.marcelr.mrchunks.Messages.tag;

/**
 * Settings of one loader, opened from {@link LoaderMenu}. Layout (3 rows), the buttons centred in the middle row:
 * <pre>
 *  . . . . [info] . . . .
 *  [size] . [block] . [signal] . [rename] . [delete]   (without block choices or the signal: four or three, centred)
 *  [back] . . . [close] . . . .
 * </pre>
 * Everyone who can open the loader's menu sees the size; block, signal, rename and delete only with ownership or the
 * admin permission. The redstone signal before restarts (loaders.restart-signal, needs MRadmin): left click switches
 * the mode (off / on before restarts / off before restarts), right click the minutes before. Deleting needs a second
 * click to confirm.
 */
public final class LoaderSettingsMenu extends Menu {

    private static final int[] FIVE = {9, 11, 13, 15, 17};
    private static final int[] FOUR = {10, 12, 14, 16};
    private static final int[] THREE = {11, 13, 15};

    private final int loaderId;
    private final Menu parent;
    /** What happens after the loader was deleted (back to the list it came from, or close). */
    private final Runnable afterDelete;
    /** Delete is armed until this time (ms) after the first click. */
    private long confirmUntil;

    public LoaderSettingsMenu(MRChunks plugin, Player viewer, int loaderId, Menu parent, Runnable afterDelete) {
        super(plugin, viewer, 3, title(plugin, loaderId));
        this.loaderId = loaderId;
        this.parent = parent;
        this.afterDelete = afterDelete;
    }

    private static Component title(MRChunks plugin, int loaderId) {
        Loader loader = plugin.loaders().byId(loaderId);
        return loader == null ? Component.empty() : plugin.messages().get("gui-title-settings", Texts.loader(plugin, loader));
    }

    @Override
    protected void draw() {
        Loader loader = plugin.loaders().byId(loaderId);
        if (loader == null) {
            // Deleted meanwhile: back on the next tick, not while drawing.
            Bukkit.getScheduler().runTask(plugin, () -> plugin.messages().run(viewer, afterDelete));
            return;
        }
        Messages messages = plugin.messages();
        AdminActions actions = plugin.adminActions();
        TagResolver tags = Texts.loader(plugin, loader);
        boolean manage = AdminActions.canManage(viewer, loader);
        boolean blocks = manage && !plugin.settings().item().blockChoices().isEmpty();
        boolean signal = manage && plugin.settings().loaders().restartSignal();
        int count = 3 + (blocks ? 1 : 0) + (signal ? 1 : 0);
        int[] slots = count == 5 ? FIVE : count == 4 ? FOUR : THREE;
        int next = 0;

        set(4, LoaderIcons.loader(plugin, loader, false));

        set(slots[next++], Items.of(Material.MAP).name(messages.item("gui-size-name", tags))
                .lore(messages.lore(AdminActions.canResize(viewer, loader) ? "gui-size-lore" : "gui-size-view-lore", tags))
                .build(), click -> {
            clickSound();
            new SizeMenu(plugin, viewer, loader.id(), this).open();
        });

        if (blocks) {
            Material current = plugin.loaders().blockOf(loader);
            button(slots[next++], current, "gui-block-name", "gui-block-lore", actions.canChangeBlock(viewer, loader),
                    () -> new BlockMenu(plugin, viewer, loader.id(), this).open(),
                    TagResolver.resolver(tags, tag("block", Component.translatable(current))));
        }

        if (signal) {
            signalButton(slots[next++], loader, tags);
        }

        button(slots[next++], Material.NAME_TAG, "gui-rename-name", "gui-rename-lore", manage,
                () -> RenameDialog.open(plugin, viewer, loader, this::open), tags);

        boolean armed = System.currentTimeMillis() < confirmUntil;
        button(slots[next], armed ? Material.TNT_MINECART : Material.TNT,
                armed ? "gui-delete-confirm-name" : "gui-delete-name",
                armed ? "gui-delete-confirm-lore" : "gui-delete-lore", manage, () -> {
                    if (System.currentTimeMillis() < confirmUntil) {
                        actions.delete(viewer, loader);
                        afterDelete.run();
                    } else {
                        confirmUntil = System.currentTimeMillis() + plugin.settings().gui().confirmSeconds() * 1000L;
                        sound(Sound.BLOCK_NOTE_BLOCK_BASS);
                        messages.send(viewer, "delete-confirm", tags);
                    }
                }, tags);

        set(18, Items.of(Material.ARROW).name(messages.item("gui-back")).build(), click -> {
            clickSound();
            parent.open();
        });
        set(22, Items.of(Material.BARRIER).name(messages.item("gui-close")).build(), click -> viewer.closeInventory());

        for (int row = 0; row < 3; row++) {
            fillRow(row);
        }
    }

    /** The redstone signal before restarts: left click = mode, right click = minutes before. */
    private void signalButton(int slot, Loader loader, TagResolver tags) {
        Messages messages = plugin.messages();
        at.marcelr.mrchunks.RestartSignal signal = plugin.restartSignal();
        boolean available = signal.available();
        boolean permitted = plugin.adminActions().canRestartSignal(viewer, loader);
        at.marcelr.mrchunks.RestartSignal.Mode mode = loader.signalMode();
        java.util.List<Integer> choices = plugin.settings().loaders().restartSignalMinutes();
        long left = available ? signal.restartIn() : -1;
        TagResolver all = TagResolver.resolver(tags,
                tag("mode", messages.item("signal-mode-" + mode.name().toLowerCase(java.util.Locale.ROOT))),
                tag("minutes", loader.signalMinutes()),
                tag("next", left < 0 ? messages.item("signal-next-none")
                        : messages.item("signal-next", tag("time", Texts.duration(plugin, left)))),
                tag("state", messages.item(signal.powered(loader) ? "signal-state-on" : "signal-state-off")));
        Items item = Items.of(signal.powered(loader) ? Material.REDSTONE_BLOCK : Material.REDSTONE)
                .name(messages.item("gui-signal-name", all));
        if (!available) {
            item.lore(messages.lore("gui-signal-unavailable-lore", all));
        } else {
            item.lore(messages.lore(mode == at.marcelr.mrchunks.RestartSignal.Mode.NONE ? "gui-signal-off-lore" : "gui-signal-lore", all));
        }
        if (!permitted) {
            item.lore(messages.item("gui-no-permission"));
        }
        set(slot, item.build(), click -> {
            if (!permitted || !available) {
                messages.send(viewer, permitted ? "signal-unavailable" : "no-permission");
                sound(Sound.ENTITY_VILLAGER_NO);
                return;
            }
            clickSound();
            if (click.isRightClick()) {
                int index = choices.indexOf(loader.signalMinutes());
                int minutes = choices.get((index + 1) % choices.size());
                plugin.adminActions().setRestartSignal(viewer, loader, mode, minutes);
            } else {
                at.marcelr.mrchunks.RestartSignal.Mode[] modes = at.marcelr.mrchunks.RestartSignal.Mode.values();
                int minutes = choices.contains(loader.signalMinutes()) ? loader.signalMinutes() : choices.getFirst();
                plugin.adminActions().setRestartSignal(viewer, loader, modes[(mode.ordinal() + 1) % modes.length], minutes);
            }
            render();
        });
    }

    /** A button that is either usable or shows "no permission" and refuses the click. */
    private void button(int slot, Material material, String nameKey, String loreKey, boolean permitted,
                        Runnable action, TagResolver tags) {
        Messages messages = plugin.messages();
        Items item = Items.of(material).name(messages.item(nameKey, tags)).lore(messages.lore(loreKey, tags));
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
