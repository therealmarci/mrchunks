package at.marcelr.mrchunks.gui;

import at.marcelr.mrchunks.AdminActions;
import at.marcelr.mrchunks.LoaderManager;
import at.marcelr.mrchunks.MRChunks;
import at.marcelr.mrchunks.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

import static at.marcelr.mrchunks.Messages.tag;

/**
 * Linked accounts of the viewer (from /chunkloader). Layout (3 rows):
 * <pre>
 *  . . . . [info] . . . .
 *  . [main] [second] [second] ...                     (click on a second account: remove the link, click twice)
 *  [back] . . . [close] . [link an account] . .
 * </pre>
 * The main account removes any of its second accounts, a second account only itself. Only a main (or not yet linked)
 * account with mrchunks.link can link another one.
 */
public final class LinkMenu extends Menu {

    private final Menu parent;
    /** The second account whose remove button is armed, until {@link #confirmUntil}. */
    private UUID armed;
    private long confirmUntil;

    public LinkMenu(MRChunks plugin, Player viewer, Menu parent) {
        super(plugin, viewer, 3, plugin.messages().get("gui-title-links"));
        this.parent = parent;
    }

    @Override
    protected void draw() {
        Messages messages = plugin.messages();
        LoaderManager loaders = plugin.loaders();
        UUID self = viewer.getUniqueId();
        UUID main = loaders.main(self);
        List<UUID> seconds = loaders.secondaries(main);
        int max = plugin.settings().links().maxAccounts();

        set(4, Items.of(Material.BOOK).name(messages.item("gui-links-info-name"))
                .lore(messages.lore("gui-links-info-lore", tag("max", max))).build());

        set(10, Items.head(Bukkit.getOfflinePlayer(main))
                .name(messages.item("gui-member-name", tag("player", LoaderManager.name(main))))
                .lore(messages.lore(seconds.isEmpty() ? "gui-links-unlinked-lore" : "gui-links-main-lore"))
                .build());
        for (int i = 0; i < seconds.size() && i < 7; i++) {
            UUID second = seconds.get(i);
            boolean mayRemove = self.equals(main) || self.equals(second);
            boolean isArmed = second.equals(armed) && System.currentTimeMillis() < confirmUntil;
            Items item = Items.head(Bukkit.getOfflinePlayer(second))
                    .name(messages.item("gui-member-name", tag("player", LoaderManager.name(second))))
                    .lore(messages.lore("gui-links-second-lore"));
            if (mayRemove) {
                item.lore(messages.item(isArmed ? "gui-links-remove-confirm" : "gui-links-remove"));
            }
            set(11 + i, item.build(), click -> {
                if (!mayRemove) {
                    return;
                }
                if (second.equals(armed) && System.currentTimeMillis() < confirmUntil) {
                    armed = null;
                    successSound();
                    plugin.adminActions().unlink(viewer, second);
                } else {
                    armed = second;
                    confirmUntil = System.currentTimeMillis() + plugin.settings().gui().confirmSeconds() * 1000L;
                    sound(Sound.BLOCK_NOTE_BLOCK_BASS);
                }
                render();
            });
        }

        boolean canAdd = self.equals(main) && seconds.size() < max && viewer.hasPermission(AdminActions.PERM_LINK)
                && !AdminActions.isExempt(viewer);
        if (canAdd) {
            set(24, Items.of(Material.LEAD).name(messages.item("gui-links-add-name", tag("count", seconds.size()), tag("max", max)))
                    .lore(messages.lore("gui-links-add-lore")).build(), click -> {
                clickSound();
                new LinkPickerMenu(plugin, viewer, this).open();
            });
        }
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
}
