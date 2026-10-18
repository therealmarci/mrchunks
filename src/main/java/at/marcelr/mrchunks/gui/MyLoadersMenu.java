package at.marcelr.mrchunks.gui;

import at.marcelr.mrchunks.AdminActions;
import at.marcelr.mrchunks.Loader;
import at.marcelr.mrchunks.MRChunks;
import at.marcelr.mrchunks.Messages;
import at.marcelr.mrchunks.Texts;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

import static at.marcelr.mrchunks.Messages.tag;

/** /chunkloader list: the viewer's own loaders; a click opens the loader's menu. */
public final class MyLoadersMenu extends Menu {

    private static final int PER_PAGE = 45;
    private int page;

    public MyLoadersMenu(MRChunks plugin, Player viewer) {
        super(plugin, viewer, 6, plugin.messages().get("gui-title-mine",
                tag("placed", plugin.loaders().count(viewer.getUniqueId())),
                tag("limit", Texts.limit(plugin.loaders().limit(viewer))),
                tag("active", plugin.loaders().activeCount(viewer.getUniqueId())),
                tag("active-limit", Texts.limit(plugin.loaders().activeLimit(viewer)))));
    }

    @Override
    protected void draw() {
        Messages messages = plugin.messages();
        List<Loader> own = plugin.loaders().byOwner(viewer.getUniqueId());
        int pages = Math.max(1, (own.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.min(page, pages - 1);
        for (int i = 0; i < PER_PAGE && page * PER_PAGE + i < own.size(); i++) {
            Loader loader = own.get(page * PER_PAGE + i);
            set(i, LoaderIcons.loader(plugin, loader, true), click -> {
                clickSound();
                new LoaderMenu(plugin, viewer, loader.id(), this).open();
            });
        }
        if (own.isEmpty()) {
            set(22, Items.of(Material.STRUCTURE_VOID).name(messages.item("gui-empty")).build());
        }
        Pages.navigation(this, messages, page, pages, p -> page = p);
        if (plugin.settings().invites().enabled()) {
            if (AdminActions.canManageInvites(viewer, viewer.getUniqueId())) {
                int members = plugin.loaders().members(viewer.getUniqueId(), null).size();
                set(47, Items.of(Material.PLAYER_HEAD).name(messages.item("gui-members-all-name", tag("count", members)))
                        .lore(messages.lore("gui-members-all-lore")).build(), click -> {
                    clickSound();
                    new MembersMenu(plugin, viewer, viewer.getUniqueId(), null, this).open();
                });
            }
            int shared = plugin.adminActions().sharedWith(viewer).size();
            set(51, Items.of(Material.CHEST_MINECART).name(messages.item("gui-shared-name", tag("count", shared)))
                    .lore(messages.lore("gui-shared-lore")).build(), click -> {
                clickSound();
                new SharedLoadersMenu(plugin, viewer, this).open();
            });
        }
        Pages.navigation(this, messages, page, pages, p -> page = p);
        set(49, Items.of(Material.BARRIER).name(messages.item("gui-close")).build(), click -> viewer.closeInventory());
        fillRow(5);
    }
}
