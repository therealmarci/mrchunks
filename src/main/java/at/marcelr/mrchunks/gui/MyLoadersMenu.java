package at.marcelr.mrchunks.gui;

import at.marcelr.mrchunks.AdminActions;
import at.marcelr.mrchunks.Loader;
import at.marcelr.mrchunks.LoaderManager;
import at.marcelr.mrchunks.MRChunks;
import at.marcelr.mrchunks.Messages;
import at.marcelr.mrchunks.Texts;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.List;
import java.util.UUID;

import static at.marcelr.mrchunks.Messages.tag;

/**
 * /chunkloader - the player's start menu, every player feature is in here:
 * <pre>
 *  rows 0-4: the own loaders (click opens the loader's menu)
 *  row 5:    members for all | shared with me | (page back) | web map | linked accounts | close | (page forward)
 *            | get loader items | return loader items
 * </pre>
 * A slot whose feature is off or not allowed stays a glass pane.
 */
public final class MyLoadersMenu extends Menu {

    private static final int PER_PAGE = 45;
    /** Shift-click gets at most one stack. */
    private static final int STACK = 64;
    private static final int MEMBERS = 45;
    private static final int SHARED = 46;
    private static final int PAGE_BACK = 47;
    private static final int LINKS = 48;
    private static final int MAP = 49;
    private static final int CLOSE = 50;
    private static final int PAGE_FORWARD = 51;
    private static final int GET = 52;
    private static final int RETURN = 53;
    private int page;

    public MyLoadersMenu(MRChunks plugin, Player viewer) {
        super(plugin, viewer, 6, plugin.messages().get("gui-title-mine",
                tag("placed", plugin.loaders().count(AdminActions.accountOf(viewer))),
                tag("limit", Texts.limit(plugin.loaders().limit(viewer))),
                tag("active", plugin.loaders().activeCount(AdminActions.accountOf(viewer))),
                tag("active-limit", Texts.limit(plugin.loaders().activeLimit(viewer)))));
    }

    @Override
    protected void draw() {
        Messages messages = plugin.messages();
        // Linked second accounts see (and manage) the loaders of their main account.
        UUID account = AdminActions.accountOf(viewer);
        List<Loader> own = plugin.loaders().byOwner(account);
        int pages = Pages.count(own.size(), PER_PAGE);
        page = Math.min(page, pages - 1);
        List<Loader> visible = Pages.slice(own, page, PER_PAGE);
        for (int i = 0; i < visible.size(); i++) {
            Loader loader = visible.get(i);
            set(i, LoaderIcons.loader(plugin, loader, true), click -> {
                clickSound();
                new LoaderMenu(plugin, viewer, loader.id(), this).open();
            });
        }
        if (own.isEmpty()) {
            set(22, Items.of(Material.STRUCTURE_VOID).name(messages.item("gui-mine-empty"))
                    .lore(messages.lore("gui-mine-empty-lore")).build());
        }
        Pages.navigation(this, messages, page, pages, p -> page = p, PAGE_BACK, PAGE_FORWARD);

        if (plugin.settings().invites().enabled()) {
            if (AdminActions.canManageInvites(viewer, account)) {
                int members = plugin.loaders().members(account, null).size();
                set(MEMBERS, Items.of(Material.PLAYER_HEAD).name(messages.item("gui-members-all-name", tag("count", members)))
                        .lore(messages.lore("gui-members-all-lore")).build(), click -> {
                    clickSound();
                    new MembersMenu(plugin, viewer, account, null, this).open();
                });
            }
            int shared = plugin.adminActions().sharedWith(viewer).size();
            set(SHARED, Items.of(Material.CHEST_MINECART).name(messages.item("gui-shared-name", tag("count", shared)))
                    .lore(messages.lore("gui-shared-lore")).build(), click -> {
                clickSound();
                new SharedLoadersMenu(plugin, viewer, this).open();
            });
        }
        if (viewer.hasPermission(AdminActions.PERM_MAP) && plugin.maps().squaremapActive()) {
            set(MAP, Items.of(Material.FILLED_MAP).name(messages.item("gui-map-name"))
                    .lore(messages.lore("gui-map-lore")).build(), click -> {
                clickSound();
                viewer.closeInventory();
                plugin.adminActions().sendMapLink(viewer);
            });
        }
        if (plugin.settings().links().enabled()
                && (viewer.hasPermission(AdminActions.PERM_LINK) || plugin.loaders().isLinked(viewer.getUniqueId()))) {
            int linked = plugin.loaders().group(viewer.getUniqueId()).size() - 1;
            set(LINKS, Items.of(Material.IRON_CHAIN).name(messages.item("gui-links-name", tag("count", linked)))
                    .lore(messages.lore("gui-links-lore")).build(), click -> {
                clickSound();
                new LinkMenu(plugin, viewer, this).open();
            });
        }
        set(CLOSE, Items.of(Material.BARRIER).name(messages.item("gui-close")).build(), click -> viewer.closeInventory());
        if (viewer.hasPermission(AdminActions.PERM_USE) && !AdminActions.isExempt(viewer)) {
            items();
        }
        fillRow(5);
    }

    /** Getting (left: 1, shift: a stack) and returning (left: 1, shift: all) loader items. */
    private void items() {
        Messages messages = plugin.messages();
        LoaderManager loaders = plugin.loaders();
        LoaderManager.Limit limit = loaders.limit(viewer);
        int placed = loaders.count(AdminActions.accountOf(viewer));
        int held = plugin.loaderItem().count(viewer);
        String free = limit.value() == Integer.MAX_VALUE ? Texts.limit(limit)
                : String.valueOf(Math.max(0, (long) limit.value() - placed - held));
        var tags = new net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[]{
                tag("held", held), tag("placed", placed), tag("limit", Texts.limit(limit)), tag("free", free)};
        set(GET, Items.of(plugin.settings().item().material()).name(messages.item("gui-items-name"))
                .lore(messages.lore("gui-items-lore", tags)).glint(true).build(), click -> {
            if (click == ClickType.LEFT || click == ClickType.SHIFT_LEFT) {
                plugin.adminActions().give(viewer, click == ClickType.SHIFT_LEFT ? STACK : 1);
                sound(Sound.ENTITY_ITEM_PICKUP);
                render();
            }
        });
        set(RETURN, Items.of(Material.HOPPER).name(messages.item("gui-return-name"))
                .lore(messages.lore("gui-return-lore", tags)).build(), click -> {
            if (click == ClickType.LEFT || click == ClickType.SHIFT_LEFT) {
                plugin.adminActions().remove(viewer, click == ClickType.SHIFT_LEFT ? Integer.MAX_VALUE : 1);
                sound(Sound.ENTITY_ITEM_PICKUP);
                render();
            }
        });
    }
}
