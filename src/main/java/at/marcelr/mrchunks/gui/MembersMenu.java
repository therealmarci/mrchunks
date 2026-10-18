package at.marcelr.mrchunks.gui;

import at.marcelr.mrchunks.AdminActions;
import at.marcelr.mrchunks.Loader;
import at.marcelr.mrchunks.LoaderManager;
import at.marcelr.mrchunks.MRChunks;
import at.marcelr.mrchunks.Messages;
import at.marcelr.mrchunks.Texts;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static at.marcelr.mrchunks.Messages.tag;

/**
 * Invited players of one loader ({@code loaderId != null}) or of all loaders of {@code owner}. A click on a head
 * removes the invite; "invite" opens {@link InviteDialog}. For one loader, players invited for all loaders are
 * shown as well (greyed out, managed in "my chunk loaders").
 */
public final class MembersMenu extends Menu {

    private static final int PER_PAGE = 45;

    private final UUID owner;
    private final Integer loaderId;
    private final Menu parent;
    private int page;

    public MembersMenu(MRChunks plugin, Player viewer, UUID owner, Integer loaderId, Menu parent) {
        super(plugin, viewer, 6, plugin.messages().get("gui-title-members", tag("scope", scope(plugin, owner, loaderId))));
        this.owner = owner;
        this.loaderId = loaderId;
        this.parent = parent;
    }

    private static String scope(MRChunks plugin, UUID owner, Integer loaderId) {
        Loader loader = loaderId == null ? null : plugin.loaders().byId(loaderId);
        return loader != null ? Texts.name(plugin, loader)
                : plugin.messages().plain("invite-scope-all-of", "owner", LoaderManager.name(owner));
    }

    @Override
    protected void draw() {
        Messages messages = plugin.messages();
        Loader loader = loaderId == null ? null : plugin.loaders().byId(loaderId);
        if (loaderId != null && loader == null) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (parent != null) {
                    parent.open();
                } else {
                    viewer.closeInventory();
                }
            });
            return;
        }
        boolean manage = AdminActions.canManageInvites(viewer, owner);

        List<Map.Entry<UUID, Long>> direct = sorted(plugin.loaders().members(owner, loader));
        List<Map.Entry<UUID, Long>> inherited = loader == null ? List.of() : sorted(plugin.loaders().members(owner, null));
        int total = direct.size() + inherited.size();
        int pages = Pages.count(total, PER_PAGE);
        page = Math.min(page, pages - 1);
        for (int i = 0; i < PER_PAGE && page * PER_PAGE + i < total; i++) {
            int index = page * PER_PAGE + i;
            if (index < direct.size()) {
                Map.Entry<UUID, Long> entry = direct.get(index);
                UUID member = entry.getKey();
                Items item = Items.head(Bukkit.getOfflinePlayer(member))
                        .name(messages.item("gui-member-name", tag("player", LoaderManager.name(member))))
                        .lore(messages.lore(manage ? "gui-member-lore" : "gui-member-lore-readonly",
                                tag("duration", Texts.until(plugin, entry.getValue()))));
                set(i, item.build(), click -> {
                    if (!manage) {
                        return;
                    }
                    clickSound();
                    plugin.adminActions().uninvite(viewer, owner, loader, member);
                    render();
                });
            } else {
                Map.Entry<UUID, Long> entry = inherited.get(index - direct.size());
                set(i, Items.head(Bukkit.getOfflinePlayer(entry.getKey()))
                        .name(messages.item("gui-member-name", tag("player", LoaderManager.name(entry.getKey()))))
                        .lore(messages.lore("gui-member-inherited-lore",
                                tag("duration", Texts.until(plugin, entry.getValue()))))
                        .build());
            }
        }
        if (total == 0) {
            set(22, Items.of(Material.STRUCTURE_VOID).name(messages.item("gui-members-empty")).build());
        }

        Pages.navigation(this, messages, page, pages, p -> page = p);
        if (manage) {
            int max = plugin.settings().invites().maxMembers();
            set(47, Items.of(Material.WRITABLE_BOOK)
                    .name(messages.item("gui-invite-name"))
                    .lore(messages.lore("gui-invite-lore", tag("count", direct.size()), tag("max", max)))
                    .build(), click -> {
                clickSound();
                InviteDialog.open(plugin, viewer, owner, loader, this);
            });
        }
        if (parent != null) {
            set(48, Items.of(Material.ARROW).name(messages.item("gui-back")).build(), click -> {
                clickSound();
                parent.open();
            });
        }
        set(49, Items.of(Material.BARRIER).name(messages.item("gui-close")).build(), click -> viewer.closeInventory());
        fillRow(5);
    }

    private static List<Map.Entry<UUID, Long>> sorted(Map<UUID, Long> members) {
        List<Map.Entry<UUID, Long>> list = new ArrayList<>(members.entrySet());
        list.sort(Comparator.comparing(e -> LoaderManager.name(e.getKey()).toLowerCase(java.util.Locale.ROOT)));
        return list;
    }
}
