package at.marcelr.mrchunks.gui;

import at.marcelr.mrchunks.AdminActions;
import at.marcelr.mrchunks.Loader;
import at.marcelr.mrchunks.MRChunks;
import at.marcelr.mrchunks.Messages;
import at.marcelr.mrchunks.Texts;
import at.marcelr.mrchunks.dummy.Dummies;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static at.marcelr.mrchunks.Messages.tag;

/**
 * "Invite a player": heads of the online players (without the owner, already invited and excluded players).
 * A click invites with the selected duration; the clock button switches the duration, "enter name" opens
 * {@link InviteDialog} for players who are offline.
 */
public final class InvitePickerMenu extends Menu {

    private static final int PER_PAGE = 45;

    private final UUID owner;
    private final Integer loaderId;
    private final Menu parent;
    private int page;
    private int durationIndex;

    public InvitePickerMenu(MRChunks plugin, Player viewer, UUID owner, Integer loaderId, Menu parent) {
        super(plugin, viewer, 6, plugin.messages().get("gui-title-invite"));
        this.owner = owner;
        this.loaderId = loaderId;
        this.parent = parent;
    }

    @Override
    protected void draw() {
        Messages messages = plugin.messages();
        Loader loader = loaderId == null ? null : plugin.loaders().byId(loaderId);
        if (loaderId != null && loader == null) {
            Bukkit.getScheduler().runTask(plugin, () -> parent.open());
            return;
        }
        List<Long> durations = plugin.settings().invites().durations();
        durationIndex = Math.min(durationIndex, durations.size() - 1);
        long duration = durations.get(durationIndex);
        String durationText = Texts.duration(plugin, duration);

        Map<UUID, Long> invited = plugin.loaders().members(owner, loader);
        List<Player> candidates = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!plugin.loaders().sameGroup(online.getUniqueId(), owner) && !invited.containsKey(online.getUniqueId())
                    && !AdminActions.isExempt(online) && !Dummies.isDummy(online) && viewer.canSee(online)) {
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
                    .lore(messages.lore("gui-invite-pick-lore", tag("duration", durationText)))
                    .build(), click -> {
                Loader current = loaderId == null ? null : plugin.loaders().byId(loaderId);
                if (loaderId != null && current == null) {
                    parent.open();
                    return;
                }
                successSound();
                plugin.adminActions().invite(viewer, owner, current, candidate.getName(), duration);
                parent.open();
            });
        }
        if (candidates.isEmpty()) {
            set(22, Items.of(Material.STRUCTURE_VOID).name(messages.item("gui-invite-nobody")).build());
        }

        Pages.navigation(this, messages, page, pages, p -> page = p);
        set(46, Items.of(Material.CLOCK)
                .name(messages.item("gui-invite-duration-name", tag("duration", durationText)))
                .lore(messages.lore("gui-invite-duration-lore"))
                .build(), click -> {
            durationIndex = (durationIndex + 1) % durations.size();
            clickSound();
            render();
        });
        set(47, Items.of(Material.WRITABLE_BOOK)
                .name(messages.item("gui-invite-by-name"))
                .lore(messages.lore("gui-invite-by-name-lore"))
                .build(), click -> {
            clickSound();
            InviteDialog.open(plugin, viewer, owner, loader, parent);
        });
        set(48, Items.of(Material.ARROW).name(messages.item("gui-back")).build(), click -> {
            clickSound();
            parent.open();
        });
        set(49, Items.of(Material.BARRIER).name(messages.item("gui-close")).build(), click -> viewer.closeInventory());
        fillRow(5);
    }
}
