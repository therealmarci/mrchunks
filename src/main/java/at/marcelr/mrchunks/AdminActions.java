package at.marcelr.mrchunks;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

import static at.marcelr.mrchunks.Messages.tag;

/**
 * Every action on loaders and players, shared by commands and menus so both behave identically
 * (permission check, feedback, notice to the owner, Discord log).
 */
public final class AdminActions {

    public static final String PERM_USE = "mrchunks.use";
    public static final String PERM_VIEW = "mrchunks.view";
    public static final String PERM_MANAGE = "mrchunks.admin.manage";
    public static final String PERM_TELEPORT = "mrchunks.admin.teleport";
    public static final String PERM_LIMIT = "mrchunks.admin.limit";
    public static final String PERM_BAN = "mrchunks.admin.ban";

    private final MRChunks plugin;

    public AdminActions(MRChunks plugin) {
        this.plugin = plugin;
    }

    private Messages messages() {
        return plugin.messages();
    }

    private LoaderManager loaders() {
        return plugin.loaders();
    }

    public static String name(OfflinePlayer player) {
        String name = player.getName();
        return name == null ? player.getUniqueId().toString().substring(0, 8) : name;
    }

    public static boolean isOwner(CommandSender sender, Loader loader) {
        return sender instanceof Player player && player.getUniqueId().equals(loader.owner());
    }

    /** Owner (with mrchunks.use) or admin with mrchunks.admin.manage. */
    public static boolean canManage(CommandSender sender, Loader loader) {
        return (isOwner(sender, loader) && sender.hasPermission(PERM_USE)) || sender.hasPermission(PERM_MANAGE);
    }

    /** Owner, admins and everyone with mrchunks.view may open a loader's menu. */
    public static boolean canView(CommandSender sender, Loader loader) {
        return isOwner(sender, loader) || sender.hasPermission(PERM_VIEW) || sender.hasPermission(PERM_MANAGE);
    }

    // ------------------------------------------------------------- loaders

    public void toggle(CommandSender sender, Loader loader) {
        if (!canManage(sender, loader)) {
            messages().send(sender, isOwner(sender, loader) ? "no-permission" : "not-owner");
            return;
        }
        boolean active = !loader.active();
        OfflinePlayer owner = org.bukkit.Bukkit.getOfflinePlayer(loader.owner());
        // Owners stay within their active limit; admins (mrchunks.admin.manage) may go beyond it.
        if (active && !sender.hasPermission(PERM_MANAGE) && !loaders().canActivate(owner)) {
            messages().send(sender, "active-limit-reached",
                    tag("active", loaders().activeCount(loader.owner())),
                    tag("active-limit", Texts.limit(loaders().activeLimit(owner))));
            return;
        }
        loaders().setActive(loader, active);
        String state = Texts.state(plugin, active);
        messages().send(sender, "toggled", Texts.loader(plugin, loader), tag("state", state));
        if (!isOwner(sender, loader)) {
            notifyOwner(loader.owner(), "owner-notify-toggled", Texts.loader(plugin, loader),
                    tag("state", state), tag("admin", sender.getName()));
        }
        plugin.discord().log("loader-toggled", discordTags(loader, "admin", sender.getName(), "state", state));
    }

    public void delete(CommandSender sender, Loader loader) {
        if (!canManage(sender, loader)) {
            messages().send(sender, isOwner(sender, loader) ? "no-permission" : "not-owner");
            return;
        }
        TagResolver tags = Texts.loader(plugin, loader);
        boolean byOwner = isOwner(sender, loader);
        loaders().delete(loader);
        messages().send(sender, "deleted", tags);
        if (byOwner) {
            plugin.discord().log("loader-deleted", discordTags(loader));
        } else {
            notifyOwner(loader.owner(), "owner-notify-deleted", tags, tag("admin", sender.getName()));
            plugin.discord().log("admin-delete", discordTags(loader, "admin", sender.getName()));
        }
    }

    public void teleport(Player player, Loader loader) {
        if (!player.hasPermission(PERM_TELEPORT)) {
            messages().send(player, "no-permission");
            return;
        }
        Location location = loader.location();
        if (location == null) {
            messages().send(player, "loader-not-found", tag("id", loader.id()));
            return;
        }
        // Stand on top of the block, looking in the same direction as before.
        Location target = location.add(0.5, 1, 0.5);
        target.setYaw(player.getLocation().getYaw());
        target.setPitch(player.getLocation().getPitch());
        player.teleportAsync(target);
        messages().send(player, "teleported", Texts.loader(plugin, loader));
    }

    // ------------------------------------------------------------- players

    /**
     * Sets one of the two caps. Lowering the active limit switches off the player's newest active loaders.
     * @param limit new limit, or null to reset to default/permission
     */
    public void setLimit(CommandSender sender, OfflinePlayer target, LoaderManager.LimitKind kind, Integer limit) {
        if (!sender.hasPermission(PERM_LIMIT)) {
            messages().send(sender, "no-permission");
            return;
        }
        loaders().setLimitOverride(target.getUniqueId(), kind, limit);
        String name = name(target);
        String effective = Texts.limit(loaders().limit(target, kind));
        String type = messages().plain(kind == LoaderManager.LimitKind.PLACE ? "limit-type-place" : "limit-type-active");
        messages().send(sender, limit == null ? "limit-reset" : "limit-set",
                tag("player", name), tag("limit", effective), tag("type", type));
        notifyOwner(target.getUniqueId(), "limit-notify", tag("limit", effective), tag("type", type));
        plugin.discord().log("admin-limit", "admin", sender.getName(), "player", name, "limit", effective, "type", type);

        if (kind == LoaderManager.LimitKind.ACTIVE) {
            int switchedOff = loaders().enforceActiveLimit(target).size();
            if (switchedOff > 0) {
                messages().send(sender, "active-limit-enforced", tag("player", name), tag("count", switchedOff));
                notifyOwner(target.getUniqueId(), "active-limit-enforced-notify", tag("count", switchedOff));
            }
        }
    }

    /** Bans the player: all their loaders are deleted and their loader items taken away. */
    public void ban(CommandSender sender, OfflinePlayer target, long duration, String reason) {
        if (!sender.hasPermission(PERM_BAN)) {
            messages().send(sender, "no-permission");
            return;
        }
        String cleanReason = reason == null || reason.isBlank() ? messages().plain("ban-no-reason") : reason;
        UUID uuid = target.getUniqueId();
        int deleted = loaders().deleteAll(uuid);
        loaders().ban(uuid, duration, cleanReason, sender.getName());
        Player online = target.getPlayer();
        if (online != null) {
            plugin.loaderItem().remove(online, Integer.MAX_VALUE);
        }
        String name = name(target);
        String durationText = Texts.banDuration(plugin, loaders().ban(uuid));
        messages().send(sender, "ban-set", tag("player", name), tag("duration", durationText),
                tag("reason", cleanReason), tag("count", deleted));
        notifyOwner(uuid, "ban-notify", tag("duration", durationText), tag("reason", cleanReason));
        plugin.discord().log("admin-ban", "admin", sender.getName(), "player", name,
                "time", durationText, "reason", cleanReason, "count", deleted);
    }

    public void unban(CommandSender sender, OfflinePlayer target) {
        if (!sender.hasPermission(PERM_BAN)) {
            messages().send(sender, "no-permission");
            return;
        }
        String name = name(target);
        if (loaders().unban(target.getUniqueId())) {
            messages().send(sender, "unban-set", tag("player", name));
            notifyOwner(target.getUniqueId(), "unban-notify");
            plugin.discord().log("admin-unban", "admin", sender.getName(), "player", name);
        } else {
            messages().send(sender, "unban-none", tag("player", name));
        }
    }

    /** Deletes all loaders of a player without banning them. */
    public void deleteAll(CommandSender sender, OfflinePlayer target) {
        if (!sender.hasPermission(PERM_MANAGE)) {
            messages().send(sender, "no-permission");
            return;
        }
        for (Loader loader : loaders().byOwner(target.getUniqueId())) {
            delete(sender, loader);
        }
    }

    public void reload(CommandSender sender) {
        plugin.reload();
        messages().send(sender, "reloaded");
        plugin.discord().log("admin-reload", "admin", sender.getName());
    }

    // ------------------------------------------------------------- helpers

    private void notifyOwner(UUID owner, String key, TagResolver... resolvers) {
        Player online = org.bukkit.Bukkit.getPlayer(owner);
        if (online != null) {
            messages().send(online, key, resolvers);
        }
    }

    /** Discord placeholders of a loader plus extra key/value pairs. */
    public Object[] discordTags(Loader loader, Object... extra) {
        Object[] base = {
                "id", loader.id(),
                "player", LoaderManager.name(loader.owner()),
                "world", loader.worldName(),
                "x", loader.x(), "y", loader.y(), "z", loader.z(),
                "chunks", plugin.settings().loaders().chunkCount()};
        Object[] all = new Object[base.length + extra.length];
        System.arraycopy(base, 0, all, 0, base.length);
        System.arraycopy(extra, 0, all, base.length, extra.length);
        return all;
    }
}
