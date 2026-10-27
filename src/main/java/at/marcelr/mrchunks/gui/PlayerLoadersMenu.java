package at.marcelr.mrchunks.gui;

import at.marcelr.mrchunks.AdminActions;
import at.marcelr.mrchunks.Loader;
import at.marcelr.mrchunks.LoaderManager;
import at.marcelr.mrchunks.MRChunks;
import at.marcelr.mrchunks.Messages;
import at.marcelr.mrchunks.Settings;
import at.marcelr.mrchunks.Texts;
import at.marcelr.mrchunks.util.DurationUtil;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.List;
import java.util.UUID;

import static at.marcelr.mrchunks.Messages.tag;

/**
 * One player's loaders plus player actions. Layout (6 rows):
 * <pre>
 *  [limit] [active limit] [delete all] [dummy limit] [head] [unlink] [ban1] [ban2] [ban3]   (or [unban] while banned)
 *  rows 1-4: the player's loaders (click: loader menu with admin actions)
 *  [«] . . . [back] . . . [»]
 * </pre>
 */
public final class PlayerLoadersMenu extends Menu {

    private static final int PER_PAGE = 36;
    private static final int FIRST_SLOT = 9;

    private final UUID targetId;
    /** What "back" does: reopen the admin menu, or return to whoever opened this (e.g. MRadmin). */
    private final Runnable back;
    private int page;

    public PlayerLoadersMenu(MRChunks plugin, Player viewer, UUID targetId, Menu parent) {
        this(plugin, viewer, targetId, parent::open);
    }

    public PlayerLoadersMenu(MRChunks plugin, Player viewer, UUID targetId, Runnable back) {
        super(plugin, viewer, 6, plugin.messages().get("gui-title-player",
                tag("player", AdminActions.name(Bukkit.getOfflinePlayer(targetId)))));
        this.targetId = targetId;
        this.back = back;
    }

    @Override
    protected void draw() {
        Messages messages = plugin.messages();
        AdminActions actions = plugin.adminActions();
        LoaderManager loaders = plugin.loaders();
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetId);
        String name = AdminActions.name(target);
        // A linked second account shows the loaders of its main account (they share them).
        List<Loader> owned = loaders.byOwner(loaders.main(targetId));

        // ---- the two caps: left +1, right -1, shift = reset
        limitButton(0, Material.EXPERIENCE_BOTTLE, "gui-limit-name", target, LoaderManager.LimitKind.PLACE);
        limitButton(1, Material.BEACON, "gui-active-limit-name", target, LoaderManager.LimitKind.ACTIVE);
        if (plugin.dummies().enabled()) {
            limitButton(3, Material.ARMOR_STAND, "gui-dummy-limit-name", target, LoaderManager.LimitKind.DUMMY);
        }

        // ---- delete all
        TagResolver tags = TagResolver.resolver(tag("player", name), tag("placed", owned.size()));
        guarded(2, Material.LAVA_BUCKET, "gui-delete-all-name", "gui-delete-all-lore", AdminActions.PERM_MANAGE,
                () -> actions.deleteAll(viewer, target), tags);

        set(4, PlayerIcons.head(plugin, target, false));
        if (loaders.isLinked(targetId)) {
            guarded(5, Material.IRON_CHAIN, "gui-unlink-name", "gui-unlink-lore", AdminActions.PERM_MANAGE,
                    () -> actions.unlink(viewer, targetId), tags);
        }

        // ---- ban / unban
        if (loaders.ban(targetId) != null) {
            guarded(7, Material.LIME_DYE, "gui-unban-name", null, AdminActions.PERM_BAN,
                    () -> actions.unban(viewer, target), tags);
        } else {
            List<Long> durations = plugin.settings().gui().banDurations();
            for (int i = 0; i < durations.size(); i++) {
                long duration = durations.get(i);
                String time = duration == Settings.PERMANENT
                        ? messages.plain("ban-duration-permanent") : DurationUtil.format(duration);
                guarded(6 + i, duration == Settings.PERMANENT ? Material.OBSIDIAN : Material.IRON_BARS,
                        "gui-ban-name", "gui-ban-lore", AdminActions.PERM_BAN,
                        () -> actions.ban(viewer, target, duration, plugin.settings().gui().banReason()),
                        TagResolver.resolver(tags, tag("time", time)));
            }
        }
        fillRow(0);

        // ---- loaders
        int pages = Pages.count(owned.size(), PER_PAGE);
        page = Math.min(page, pages - 1);
        List<Loader> shown = Pages.slice(owned, page, PER_PAGE);
        for (int i = 0; i < shown.size(); i++) {
            Loader loader = shown.get(i);
            set(FIRST_SLOT + i, LoaderIcons.loader(plugin, loader, true), click -> {
                clickSound();
                new LoaderMenu(plugin, viewer, loader.id(), this).open();
            });
        }
        if (shown.isEmpty()) {
            set(31, Items.of(Material.STRUCTURE_VOID).name(messages.item("gui-empty")).build());
        }

        // ---- navigation
        Pages.navigation(this, messages, page, pages, p -> page = p);
        set(49, Items.of(Material.ARROW).name(messages.item("gui-back")).build(), click -> {
            clickSound();
            back.run();
        });
        fillRow(5);
    }

    private void limitButton(int slot, Material material, String nameKey, OfflinePlayer target,
                             LoaderManager.LimitKind kind) {
        Messages messages = plugin.messages();
        LoaderManager.Limit limit = plugin.loaders().limit(target, kind);
        Items item = Items.of(material)
                .name(messages.item(nameKey, tag("limit", Texts.limit(limit))))
                .lore(messages.lore("gui-limit-lore"));
        if (!viewer.hasPermission(AdminActions.PERM_LIMIT)) {
            item.lore(messages.item("gui-no-permission"));
        }
        set(slot, item.build(), click -> {
            if (!allowed(AdminActions.PERM_LIMIT)) {
                return;
            }
            if (click.isShiftClick()) {
                plugin.adminActions().setLimit(viewer, target, kind, null);
            } else if (limit.value() != Integer.MAX_VALUE) {
                int next = limit.value() + (click == ClickType.RIGHT ? -1 : 1);
                plugin.adminActions().setLimit(viewer, target, kind, Math.max(0, next));
            }
            successSound();
            render();
        });
    }

    private void guarded(int slot, Material material, String nameKey, String loreKey, String permission,
                         Runnable action, TagResolver tags) {
        Messages messages = plugin.messages();
        Items item = Items.of(material).name(messages.item(nameKey, tags));
        if (loreKey != null) {
            item.lore(messages.lore(loreKey, tags));
        }
        if (!viewer.hasPermission(permission)) {
            item.lore(messages.item("gui-no-permission"));
        }
        set(slot, item.build(), click -> {
            if (allowed(permission)) {
                action.run();
                successSound();
                render();
            }
        });
    }
}
