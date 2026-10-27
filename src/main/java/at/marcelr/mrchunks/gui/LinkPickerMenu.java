package at.marcelr.mrchunks.gui;

import at.marcelr.mrchunks.AdminActions;
import at.marcelr.mrchunks.MRChunks;
import at.marcelr.mrchunks.Messages;
import at.marcelr.mrchunks.dummy.Dummies;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static at.marcelr.mrchunks.Messages.tag;

/**
 * Online players the viewer can ask to become their second account (not linked yet, not excluded). A click sends the
 * request; the other player accepts in a dialog.
 */
public final class LinkPickerMenu extends Menu {

    private static final int PER_PAGE = 45;

    private final Menu parent;
    private int page;

    public LinkPickerMenu(MRChunks plugin, Player viewer, Menu parent) {
        super(plugin, viewer, 6, plugin.messages().get("gui-title-link-pick"));
        this.parent = parent;
    }

    @Override
    protected void draw() {
        Messages messages = plugin.messages();
        List<Player> candidates = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online != viewer && !Dummies.isDummy(online) && !AdminActions.isExempt(online)
                    && !plugin.loaders().isLinked(online.getUniqueId()) && viewer.canSee(online)) {
                candidates.add(online);
            }
        }
        candidates.sort(Comparator.comparing(p -> p.getName().toLowerCase(Locale.ROOT)));

        int pages = Pages.count(candidates.size(), PER_PAGE);
        page = Math.min(page, pages - 1);
        List<Player> visible = Pages.slice(candidates, page, PER_PAGE);
        for (int i = 0; i < visible.size(); i++) {
            Player candidate = visible.get(i);
            set(i, Items.head(candidate)
                    .name(messages.item("gui-member-name", tag("player", candidate.getName())))
                    .lore(messages.lore("gui-link-pick-lore", tag("count", plugin.loaders().count(candidate.getUniqueId()))))
                    .build(), click -> {
                successSound();
                viewer.closeInventory();
                plugin.linkRequests().request(viewer, candidate);
            });
        }
        if (candidates.isEmpty()) {
            set(22, Items.of(Material.STRUCTURE_VOID).name(messages.item("gui-link-nobody"))
                    .lore(messages.lore("gui-link-nobody-lore")).build());
        }

        Pages.navigation(this, messages, page, pages, p -> page = p);
        set(48, Items.of(Material.ARROW).name(messages.item("gui-back")).build(), click -> {
            clickSound();
            parent.open();
        });
        set(49, Items.of(Material.BARRIER).name(messages.item("gui-close")).build(), click -> viewer.closeInventory());
        fillRow(5);
    }
}
