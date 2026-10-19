package at.marcelr.mrchunks;

import at.marcelr.mrchunks.lag.LagLock;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
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
    public static final String PERM_INVITE = "mrchunks.invite";
    /** Excludes a player from chunk loaders completely (e.g. second accounts). Strict: also for admins. */
    public static final String PERM_EXEMPT = "mrchunks.exempt";

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

    /** Owner, invited players, admins and everyone with mrchunks.view may open a loader's menu. */
    public boolean canView(CommandSender sender, Loader loader) {
        return isOwner(sender, loader) || isMember(sender, loader) || sender.hasPermission(PERM_VIEW)
                || sender.hasPermission(PERM_MANAGE);
    }

    public static boolean isExempt(CommandSender sender) {
        return sender instanceof Player && sender.hasPermission(PERM_EXEMPT);
    }

    /** Invited (directly or for all loaders of the owner) and not excluded via mrchunks.exempt. */
    public boolean isMember(CommandSender sender, Loader loader) {
        return sender instanceof Player player && !isExempt(player)
                && plugin.loaders().isMember(player.getUniqueId(), loader);
    }

    /** Loaders of other players the viewer was invited to (none if excluded via mrchunks.exempt). */
    public java.util.List<Loader> sharedWith(Player player) {
        return isExempt(player) ? java.util.List.of() : plugin.loaders().sharedWith(player.getUniqueId());
    }

    /** Switching on/off: owner, admins - and invited players (only this, never deleting or renaming). */
    public boolean canToggle(CommandSender sender, Loader loader) {
        return canManage(sender, loader) || isMember(sender, loader);
    }

    /** Managing the invites of a loader / of all loaders of {@code owner}: the owner (mrchunks.invite) or admins. */
    public static boolean canManageInvites(CommandSender sender, UUID owner) {
        boolean isOwner = sender instanceof Player player && player.getUniqueId().equals(owner);
        return (isOwner && sender.hasPermission(PERM_INVITE) && !isExempt(sender)) || sender.hasPermission(PERM_MANAGE);
    }

    // ------------------------------------------------------------- loaders

    public void toggle(CommandSender sender, Loader loader) {
        if (!canToggle(sender, loader)) {
            messages().send(sender, isOwner(sender, loader) ? "no-permission" : "not-owner");
            return;
        }
        boolean active = !loader.active();
        // A lag lock blocks switching on for everyone - admins lift it explicitly first (unlock).
        LagLock lock = loaders().lagLock(loader);
        if (active && lock != null) {
            messages().send(sender, "lag-locked-toggle", plugin.lag().tags(loader, lock));
            return;
        }
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
        if (active && loaders().paused()) {
            messages().send(sender, "pause-toggle-hint", plugin.pause().tags());
        }
        if (!isOwner(sender, loader)) {
            notifyOwner(loader.owner(), "owner-notify-toggled", Texts.loader(plugin, loader),
                    tag("state", state), tag("admin", sender.getName()));
        }
        plugin.discord().log("loader-toggled", discordTags(loader, "admin", sender.getName(), "state", state));
    }

    /** Lifts a lag lock (mrchunks.admin.manage). The loader stays off; the owner can switch it on again. */
    public void unlock(CommandSender sender, Loader loader) {
        if (!sender.hasPermission(PERM_MANAGE)) {
            messages().send(sender, "no-permission");
            return;
        }
        LagLock lock = loaders().lagLock(loader);
        if (lock == null) {
            messages().send(sender, "lag-not-locked", Texts.loader(plugin, loader));
            return;
        }
        loaders().lagUnlock(loader);
        plugin.lag().unlocked(loader);
        messages().send(sender, "lag-unlocked", Texts.loader(plugin, loader));
        notifyOwner(loader.owner(), "lag-unlocked-owner", Texts.loader(plugin, loader), tag("admin", sender.getName()));
        plugin.getLogger().info(sender.getName() + " lifted the lag lock of " + Texts.consoleName(plugin, loader) + ".");
        plugin.discord().log("loader-lag-unlocked", discordTags(loader, "admin", sender.getName()));
        var data = at.marcelr.mrchunks.lag.LagMonitor.eventData(plugin, loader, "unlock");
        data.put("by", sender.getName());
        org.bukkit.Bukkit.getPluginManager().callEvent(new at.marcelr.mrchunks.api.LoaderLockEvent(data));
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

    /**
     * Gives the loader a name, or with an empty name the default name back ("Chunkloader 3").
     * The name is plain text: no formatting, no control characters, at most gui.max-name-length characters.
     */
    public void rename(CommandSender sender, Loader loader, String raw) {
        if (!canManage(sender, loader)) {
            messages().send(sender, isOwner(sender, loader) ? "no-permission" : "not-owner");
            return;
        }
        String clean = raw == null ? "" : raw.replaceAll("\\p{Cntrl}", "").replaceAll("\\s+", " ").strip();
        int max = plugin.settings().gui().maxNameLength();
        if (clean.codePointCount(0, clean.length()) > max) {
            clean = clean.substring(0, clean.offsetByCodePoints(0, max)).strip();
        }
        String name = clean.isEmpty() ? null : clean;
        String oldName = Texts.name(plugin, loader);
        if (java.util.Objects.equals(name, loader.name())) {
            return;
        }
        loaders().rename(loader, name);
        TagResolver tags = TagResolver.resolver(Texts.loader(plugin, loader), tag("old", oldName));
        messages().send(sender, name == null ? "rename-reset" : "renamed", tags);
        if (!isOwner(sender, loader)) {
            notifyOwner(loader.owner(), "owner-notify-renamed", tags, tag("admin", sender.getName()));
        }
        plugin.discord().log("loader-renamed", discordTags(loader, "old", oldName, "admin", sender.getName()));
    }

    public void teleport(Player player, Loader loader) {
        if (!player.hasPermission(PERM_TELEPORT)) {
            messages().send(player, "no-permission");
            return;
        }
        Location location = loader.location();
        if (location == null) {
            messages().send(player, "world-not-loaded", Texts.loader(plugin, loader));
            return;
        }
        // Stand on top of the block, looking in the same direction as before.
        Location target = location.add(0.5, 1, 0.5);
        target.setYaw(player.getLocation().getYaw());
        target.setPitch(player.getLocation().getPitch());
        player.teleportAsync(target);
        messages().send(player, "teleported", Texts.loader(plugin, loader));
    }

    // ------------------------------------------------------- loader items

    /** Permission for the link to the web map. */
    public static final String PERM_MAP = "mrchunks.map";

    /** Puts up to {@code wanted} loader items into the player's inventory, within their limit (minus placed and held). */
    public void give(Player player, int wanted) {
        if (!player.hasPermission(PERM_USE)) {
            messages().send(player, "no-permission");
            return;
        }
        if (isExempt(player)) {
            messages().send(player, "exempt");
            return;
        }
        LoaderManager loaders = loaders();
        LoaderManager.Ban ban = loaders.ban(player.getUniqueId());
        if (ban != null) {
            messages().send(player, "banned", tag("duration", Texts.banDuration(plugin, ban)), tag("reason", ban.reason()));
            return;
        }
        LoaderManager.Limit limit = loaders.limit(player);
        int placed = loaders.count(player.getUniqueId());
        int held = plugin.loaderItem().count(player);
        long free = (long) limit.value() - placed - held;
        if (free <= 0) {
            messages().send(player, "give-none-left", tag("placed", placed), tag("held", held), tag("limit", Texts.limit(limit)));
            return;
        }
        // At most one stack at a time (unlimited players would otherwise get a huge amount).
        int amount = (int) Math.min(Math.min(Math.max(1, wanted), 64), free);
        Map<Integer, org.bukkit.inventory.ItemStack> leftover = player.getInventory().addItem(plugin.loaderItem().create(amount));
        int notGiven = leftover.values().stream().mapToInt(org.bukkit.inventory.ItemStack::getAmount).sum();
        int given = amount - notGiven;
        if (given == 0) {
            messages().send(player, "give-inventory-full");
            return;
        }
        if (notGiven > 0) {
            messages().send(player, "give-inventory-full");
        }
        messages().send(player, "give-ok", tag("count", given), tag("used", placed + held + given),
                tag("limit", Texts.limit(limit)));
    }

    /** Takes up to {@code max} loader items out of the player's inventory (they count towards the limit). */
    public void remove(Player player, int max) {
        int removed = plugin.loaderItem().remove(player, max);
        messages().send(player, removed == 0 ? "remove-none" : "remove-ok", tag("count", removed));
    }

    /** Clickable link to the squaremap web map (only if squaremap is installed and its address is known). */
    public void sendMapLink(Player player) {
        if (!player.hasPermission(PERM_MAP)) {
            messages().send(player, "no-permission");
            return;
        }
        String url = plugin.maps().mapUrl();
        if (url.isEmpty()) {
            messages().send(player, "map-unavailable");
            return;
        }
        var link = messages().get("map-link-label", tag("url", url))
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.openUrl(url));
        messages().send(player, "map-link", tag("link", link));
    }

    // ------------------------------------------------------------- invites

    /** "Farm" or "alle deine Chunkloader" - what an invite covers, for messages. */
    private String scope(Loader loader, String allKey, UUID owner) {
        return loader != null ? Texts.name(plugin, loader)
                : messages().plain(allKey, "owner", LoaderManager.name(owner));
    }

    /**
     * Invites a player to switch one loader ({@code loader}) or all loaders of {@code owner} ({@code loader == null})
     * on and off. Only the owner (or an admin) may invite; the invited player must have been on the server before.
     * @param durationMillis duration, or {@link Settings#PERMANENT}
     */
    public void invite(CommandSender sender, UUID owner, Loader loader, String targetName, long durationMillis) {
        if (!canManageInvites(sender, owner)) {
            messages().send(sender, "no-permission");
            return;
        }
        String name = targetName == null ? "" : targetName.trim();
        OfflinePlayer target = name.isEmpty() ? null : org.bukkit.Bukkit.getPlayerExact(name);
        if (target == null && !name.isEmpty()) {
            target = org.bukkit.Bukkit.getOfflinePlayerIfCached(name);
        }
        if (target == null) {
            messages().send(sender, "player-not-found", tag("player", name));
            return;
        }
        if (target.getUniqueId().equals(owner)) {
            messages().send(sender, "invite-self");
            return;
        }
        Player online = target.getPlayer();
        if (online != null && isExempt(online)) {
            messages().send(sender, "invite-exempt", tag("player", name(target)));
            return;
        }
        Map<UUID, Long> members = loaders().members(owner, loader);
        int max = plugin.settings().invites().maxMembers();
        if (!members.containsKey(target.getUniqueId()) && members.size() >= max) {
            messages().send(sender, "invite-full", tag("max", max));
            return;
        }
        loaders().invite(owner, loader, target.getUniqueId(), durationMillis);
        long until = loaders().members(owner, loader).getOrDefault(target.getUniqueId(), Long.MAX_VALUE);
        String duration = Texts.until(plugin, until);
        messages().send(sender, "invited", tag("player", name(target)),
                tag("scope", scope(loader, "invite-scope-all", owner)), tag("duration", duration));
        if (online != null) {
            messages().send(online, "invite-notify", tag("owner", LoaderManager.name(owner)),
                    tag("scope", scope(loader, "invite-scope-all-of", owner)), tag("duration", duration));
        }
        plugin.discord().log("loader-invite", "owner", LoaderManager.name(owner), "player", name(target),
                "scope", loader == null ? messages().plain("invite-scope-all-of", "owner", LoaderManager.name(owner))
                        : Texts.name(plugin, loader),
                "time", duration, "admin", sender.getName());
    }

    public void uninvite(CommandSender sender, UUID owner, Loader loader, UUID member) {
        if (!canManageInvites(sender, owner)) {
            messages().send(sender, "no-permission");
            return;
        }
        if (!loaders().uninvite(owner, loader, member)) {
            return;
        }
        String memberName = LoaderManager.name(member);
        messages().send(sender, "uninvited", tag("player", memberName),
                tag("scope", scope(loader, "invite-scope-all", owner)));
        Player online = org.bukkit.Bukkit.getPlayer(member);
        if (online != null) {
            messages().send(online, "uninvite-notify", tag("owner", LoaderManager.name(owner)),
                    tag("scope", scope(loader, "invite-scope-all-of", owner)));
        }
        plugin.discord().log("loader-uninvite", "owner", LoaderManager.name(owner), "player", memberName,
                "scope", loader == null ? messages().plain("invite-scope-all-of", "owner", LoaderManager.name(owner))
                        : Texts.name(plugin, loader),
                "admin", sender.getName());
    }

    /**
     * mrchunks.exempt: the player may not have chunk loaders at all. Deletes their loaders and takes their loader
     * items (on join and on reload). Permissions are only known while the player is online.
     */
    public void enforceExempt(Player player) {
        if (!isExempt(player)) {
            return;
        }
        int items = plugin.loaderItem().remove(player, Integer.MAX_VALUE);
        int deleted = loaders().deleteAll(player.getUniqueId());
        if (deleted == 0 && items == 0) {
            return;
        }
        messages().send(player, "exempt-cleared-notify", tag("count", deleted));
        plugin.getLogger().info(player.getName() + " has mrchunks.exempt: " + deleted + " chunk loader(s) deleted, "
                + items + " item(s) removed.");
        plugin.discord().log("exempt-cleared", "player", player.getName(), "count", deleted);
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
                "name", Texts.name(plugin, loader),
                "number", loader.number(),
                "id", loader.number(),
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
