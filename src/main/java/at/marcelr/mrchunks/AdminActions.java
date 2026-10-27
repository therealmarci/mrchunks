package at.marcelr.mrchunks;

import at.marcelr.mrchunks.lag.LagLock;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
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
    public static final String PERM_BLOCK = "mrchunks.block";
    /** Linking another account with the own one (both share loaders and limits). */
    public static final String PERM_LINK = "mrchunks.link";
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

    /** The account whose loaders the player manages: their main account if linked, otherwise themself. */
    public static UUID accountOf(Player player) {
        return org.bukkit.plugin.java.JavaPlugin.getPlugin(MRChunks.class).loaders().main(player.getUniqueId());
    }

    /** The owner of the loader, or an account linked with the owner. */
    public static boolean isOwner(CommandSender sender, Loader loader) {
        return sender instanceof Player player && accountOf(player).equals(loader.owner());
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
        boolean isOwner = sender instanceof Player player && accountOf(player).equals(owner);
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
        plugin.mradmin().lagUnlocked(loader, sender.getName());
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

    // ------------------------------------------------------------- dummies

    /** Switches the loader's dummy player on or off: owner (within their dummy limit) or admins. */
    /** Choosing the block in the world: the owner with mrchunks.block, or an admin with mrchunks.admin.manage. */
    public boolean canChangeBlock(CommandSender sender, Loader loader) {
        return !plugin.settings().item().blockChoices().isEmpty() && canManage(sender, loader)
                && (sender.hasPermission(PERM_BLOCK) || sender.hasPermission(PERM_MANAGE));
    }

    /** Sets the block the loader stands as in the world (item.material or one of item.block-choices). */
    public void changeBlock(CommandSender sender, Loader loader, Material material) {
        Settings.ItemSettings item = plugin.settings().item();
        if (!canChangeBlock(sender, loader) || (material != item.material() && !item.blockChoices().contains(material))) {
            messages().send(sender, "no-permission");
            return;
        }
        loaders().setBlock(loader, material);
        messages().send(sender, "block-changed", Texts.loader(plugin, loader), tag("block", Component.translatable(material)));
    }

    public void toggleDummy(CommandSender sender, Loader loader) {
        if (!canManage(sender, loader)) {
            messages().send(sender, isOwner(sender, loader) ? "no-permission" : "not-owner");
            return;
        }
        if (!plugin.dummies().enabled()) {
            messages().send(sender, "dummy-disabled");
            return;
        }
        boolean on = !loader.dummy();
        OfflinePlayer owner = org.bukkit.Bukkit.getOfflinePlayer(loader.owner());
        // Owners stay within their dummy limit; admins may go beyond it.
        if (on && !sender.hasPermission(PERM_MANAGE) && !loaders().canAddDummy(owner)) {
            messages().send(sender, "dummy-limit-reached", dummyTags(owner));
            return;
        }
        loaders().setDummy(loader, on);
        String state = Texts.state(plugin, on);
        messages().send(sender, !on ? "dummy-off" : loader.active() ? "dummy-on" : "dummy-on-inactive",
                Texts.loader(plugin, loader), dummyTags(owner));
        if (!isOwner(sender, loader)) {
            notifyOwner(loader.owner(), "owner-notify-dummy", Texts.loader(plugin, loader),
                    tag("state", state), tag("admin", sender.getName()));
        }
        plugin.discord().log("dummy-toggled", discordTags(loader, "admin", sender.getName(), "state", state));
    }

    /** Placeholders {@code <dummies>} (switched on) and {@code <dummy-limit>} of the owner's account. */
    public TagResolver dummyTags(OfflinePlayer owner) {
        return TagResolver.resolver(tag("dummies", loaders().dummyCount(loaders().main(owner.getUniqueId()))),
                tag("dummy-limit", Texts.limit(loaders().limit(owner, LoaderManager.LimitKind.DUMMY))));
    }

    // ----------------------------------------------------- linked accounts

    /**
     * Links {@code secondary} to {@code main} after both agreed (see {@link at.marcelr.mrchunks.link.LinkRequests}) or
     * by an admin. Checks everything again, because the situation may have changed since the request.
     * @param by who did it (for the logs): the accepting player or the admin
     * @return an error message key, or null if linked
     */
    public String link(OfflinePlayer main, OfflinePlayer secondary, String by) {
        String problem = linkProblem(main, secondary);
        if (problem != null) {
            return problem;
        }
        int moved = loaders().link(main.getUniqueId(), secondary.getUniqueId());
        String mainName = name(main);
        String secondName = name(secondary);
        notifyOwner(main.getUniqueId(), "link-done", tag("main", mainName), tag("second", secondName), tag("count", moved));
        plugin.getLogger().info(secondName + " is now linked to " + mainName + " (" + moved + " loader(s) taken over, by "
                + by + ").");
        plugin.discord().log("accounts-linked", "player", secondName, "owner", mainName, "count", moved, "admin", by);
        return null;
    }

    /** /mrchunks link: an admin links two accounts directly, without asking them. */
    public void adminLink(CommandSender sender, OfflinePlayer main, OfflinePlayer secondary) {
        if (!sender.hasPermission(PERM_MANAGE)) {
            messages().send(sender, "no-permission");
            return;
        }
        String problem = link(main, secondary, sender.getName());
        if (problem != null) {
            messages().send(sender, problem, tag("player", name(secondary)),
                    tag("max", plugin.settings().links().maxAccounts()));
        } else {
            messages().send(sender, "link-admin-done", tag("main", name(main)), tag("second", name(secondary)));
        }
    }

    /** Why the two accounts cannot be linked (message key), or null. */
    public String linkProblem(OfflinePlayer main, OfflinePlayer secondary) {
        LoaderManager loaders = loaders();
        UUID mainId = main.getUniqueId();
        UUID secondId = secondary.getUniqueId();
        if (mainId.equals(secondId)) {
            return "link-self";
        }
        if (!loaders.main(mainId).equals(mainId)) {
            return "link-main-is-second";
        }
        if (loaders.isLinked(secondId)) {
            return "link-already-linked";
        }
        if (loaders.secondaries(mainId).size() >= plugin.settings().links().maxAccounts()) {
            return "link-full";
        }
        Player mainOnline = main.getPlayer();
        Player secondOnline = secondary.getPlayer();
        if ((mainOnline != null && isExempt(mainOnline)) || (secondOnline != null && isExempt(secondOnline))) {
            return "link-exempt";
        }
        if (loaders.groupBan(mainId) != null || loaders.ban(secondId) != null) {
            return "link-banned";
        }
        return null;
    }

    /**
     * Dissolves a link: a second account leaves its main account, or with a main account all its second accounts go.
     * The player themself (either side) or an admin may do this; the loaders stay with the main account.
     */
    public void unlink(CommandSender sender, UUID account) {
        boolean self = sender instanceof Player player && loaders().sameGroup(player.getUniqueId(), account);
        if (!self && !sender.hasPermission(PERM_MANAGE)) {
            messages().send(sender, "no-permission");
            return;
        }
        if (removeLinks(account, sender.getName(), sender) == 0) {
            messages().send(sender, "link-not-linked", tag("player", LoaderManager.name(account)));
        }
    }

    /**
     * Removes the links of {@code account} (see {@link #unlink}); both accounts hear about it.
     * @param sender gets the confirmation, or null (web)
     * @return number of removed links
     */
    private int removeLinks(UUID account, String by, CommandSender sender) {
        LoaderManager loaders = loaders();
        UUID main = loaders.main(account);
        List<UUID> seconds = main.equals(account) ? loaders.secondaries(main) : List.of(account);
        for (UUID second : seconds) {
            loaders.unlink(second);
            String secondName = LoaderManager.name(second);
            String mainName = LoaderManager.name(main);
            if (sender != null) {
                messages().send(sender, "link-removed", tag("main", mainName), tag("second", secondName));
            }
            for (UUID notify : List.of(main, second)) {
                Player online = org.bukkit.Bukkit.getPlayer(notify);
                if (online != null && online != sender) {
                    messages().send(online, "link-removed-notify", tag("main", mainName), tag("second", secondName),
                            tag("admin", by));
                }
            }
            plugin.getLogger().info(by + " removed the link of " + secondName + " to " + mainName + ".");
            plugin.discord().log("accounts-unlinked", "player", secondName, "owner", mainName, "admin", by);
        }
        return seconds.size();
    }

    // ----------------------------------------------------- web (MRadmin)

    // Actions from MRadmin's web interface: the admin is usually not online, so there is no sender to answer - the
    // owner hears about it, the Discord and server log get it as in game. MRadmin checks mrchunks.admin.manage
    // first. Each returns an error key for the web interface, or null if it worked.

    public String webToggle(String admin, Loader loader) {
        boolean active = !loader.active();
        if (active && loaders().lagLocked(loader)) {
            return "loader-locked";
        }
        loaders().setActive(loader, active);
        String state = Texts.state(plugin, active);
        notifyOwner(loader.owner(), "owner-notify-toggled", Texts.loader(plugin, loader), tag("state", state), tag("admin", admin));
        plugin.getLogger().info(admin + " (web) switched " + Texts.consoleName(plugin, loader) + (active ? " on." : " off."));
        plugin.discord().log("loader-toggled", discordTags(loader, "admin", admin, "state", state));
        return null;
    }

    public String webUnlock(String admin, Loader loader) {
        if (loaders().lagLock(loader) == null) {
            return "loader-not-locked";
        }
        loaders().lagUnlock(loader);
        plugin.lag().unlocked(loader);
        notifyOwner(loader.owner(), "lag-unlocked-owner", Texts.loader(plugin, loader), tag("admin", admin));
        plugin.getLogger().info(admin + " (web) lifted the lag lock of " + Texts.consoleName(plugin, loader) + ".");
        plugin.discord().log("loader-lag-unlocked", discordTags(loader, "admin", admin));
        plugin.mradmin().lagUnlocked(loader, admin);
        return null;
    }

    public String webToggleDummy(String admin, Loader loader) {
        if (!plugin.dummies().enabled()) {
            return "dummies-disabled";
        }
        boolean on = !loader.dummy();
        loaders().setDummy(loader, on);
        String state = Texts.state(plugin, on);
        notifyOwner(loader.owner(), "owner-notify-dummy", Texts.loader(plugin, loader), tag("state", state), tag("admin", admin));
        plugin.getLogger().info(admin + " (web) switched the dummy of " + Texts.consoleName(plugin, loader) + (on ? " on." : " off."));
        plugin.discord().log("dummy-toggled", discordTags(loader, "admin", admin, "state", state));
        return null;
    }

    public String webDelete(String admin, Loader loader) {
        TagResolver tags = Texts.loader(plugin, loader);
        loaders().delete(loader);
        notifyOwner(loader.owner(), "owner-notify-deleted", tags, tag("admin", admin));
        plugin.getLogger().info(admin + " (web) deleted " + Texts.consoleName(plugin, loader) + ".");
        plugin.discord().log("admin-delete", discordTags(loader, "admin", admin));
        return null;
    }

    // Player actions from the web (MRadmin checks mrchunks.admin.limit, .ban or .manage first).

    /** Sets or (limit null) resets one of the two caps, like /mrchunks limit; a lower active limit switches loaders off. */
    public String webSetLimit(String admin, OfflinePlayer target, LoaderManager.LimitKind kind, Integer limit) {
        loaders().setLimitOverride(target.getUniqueId(), kind, limit);
        String name = name(target);
        String effective = Texts.limit(loaders().limit(target, kind));
        notifyOwner(target.getUniqueId(), "limit-notify", tag("limit", effective), Messages.lazy("type", () -> limitType(kind)));
        plugin.getLogger().info(admin + " (web) set the " + kind.name().toLowerCase(java.util.Locale.ROOT) + " limit of "
                + name + " to " + (limit == null ? "default" : limit) + ".");
        plugin.discord().log("admin-limit", "admin", admin, "player", name, "limit", effective, "type", messages().server(() -> limitType(kind)));
        enforceLimit(target, kind);
        return null;
    }

    /** Bans the player from chunk loaders: their loaders are deleted, loader items taken (if online). */
    public String webBan(String admin, OfflinePlayer target, long duration, String reason) {
        String cleanReason = reason == null || reason.isBlank() ? messages().server(() -> messages().plain("ban-no-reason")) : reason;
        UUID uuid = target.getUniqueId();
        int deleted = loaders().deleteAll(loaders().main(uuid));
        loaders().ban(uuid, duration, cleanReason, admin);
        Player online = target.getPlayer();
        if (online != null) {
            plugin.loaderItem().remove(online, Integer.MAX_VALUE);
        }
        String name = name(target);
        LoaderManager.Ban banned = loaders().ban(uuid);
        java.util.function.Supplier<String> durationText = () -> Texts.banDuration(plugin, banned);
        notifyOwner(uuid, "ban-notify", Messages.lazy("duration", durationText), tag("reason", cleanReason));
        plugin.getLogger().info(admin + " (web) banned " + name + " from chunk loaders (" + messages().as(org.bukkit.Bukkit.getConsoleSender(), durationText) + ", "
                + deleted + " loader(s) deleted).");
        plugin.discord().log("admin-ban", "admin", admin, "player", name,
                "time", messages().server(durationText), "reason", cleanReason, "count", deleted);
        return null;
    }

    public String webUnban(String admin, OfflinePlayer target) {
        if (!loaders().unban(target.getUniqueId())) {
            return "not-banned";
        }
        String name = name(target);
        notifyOwner(target.getUniqueId(), "unban-notify");
        plugin.getLogger().info(admin + " (web) lifted the chunk loader ban of " + name + ".");
        plugin.discord().log("admin-unban", "admin", admin, "player", name);
        return null;
    }

    /** Removes the link of the player (a main account: of all its second accounts); the loaders stay. */
    /** Links two accounts from the web (like /mrchunks link): the second account's loaders go to the main account. */
    public String webLink(String admin, OfflinePlayer main, OfflinePlayer secondary) {
        if (!plugin.settings().links().enabled()) {
            return "links-disabled";
        }
        return link(main, secondary, admin + " (web)");
    }

    public String webUnlink(String admin, OfflinePlayer target) {
        return removeLinks(target.getUniqueId(), admin, null) == 0 ? "not-linked" : null;
    }

    /** Deletes all loaders of the player (without banning). */
    public String webDeleteAll(String admin, OfflinePlayer target) {
        List<Loader> owned = loaders().byOwner(loaders().main(target.getUniqueId()));
        if (owned.isEmpty()) {
            return "no-loaders";
        }
        for (Loader loader : List.copyOf(owned)) {
            webDelete(admin, loader);
        }
        return null;
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
        String oldCustom = loader.name();
        int number = loader.number();
        if (java.util.Objects.equals(name, loader.name())) {
            return;
        }
        loaders().rename(loader, name);
        // The old default name ("Chunkloader 3") in the language of whoever gets the message.
        TagResolver tags = TagResolver.resolver(Texts.loader(plugin, loader), Messages.lazy("old", () -> oldCustom != null
                ? oldCustom : messages().plain("loader-default-name", "number", number)));
        messages().send(sender, name == null ? "rename-reset" : "renamed", tags);
        if (!isOwner(sender, loader)) {
            notifyOwner(loader.owner(), "owner-notify-renamed", tags, tag("admin", sender.getName()));
        }
        plugin.discord().log("loader-renamed", discordTags(loader, "old", oldCustom != null ? oldCustom
                : messages().server(() -> messages().plain("loader-default-name", "number", number)), "admin", sender.getName()));
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
        LoaderManager.Ban ban = loaders.groupBan(player.getUniqueId());
        if (ban != null) {
            messages().send(player, "banned", Messages.lazy("duration", () -> Texts.banDuration(plugin, ban)), tag("reason", ban.reason()));
            return;
        }
        LoaderManager.Limit limit = loaders.limit(player);
        int placed = loaders.count(accountOf(player));
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
        if (target == null || (target.getPlayer() != null && at.marcelr.mrchunks.dummy.Dummies.isDummy(target.getPlayer()))) {
            messages().send(sender, "player-not-found", tag("player", name));
            return;
        }
        if (loaders().sameGroup(target.getUniqueId(), owner)) {
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
        java.util.function.Supplier<String> duration = () -> Texts.until(plugin, until);
        messages().send(sender, "invited", tag("player", name(target)),
                Messages.lazy("scope", () -> scope(loader, "invite-scope-all", owner)), Messages.lazy("duration", duration));
        if (online != null) {
            messages().send(online, "invite-notify", tag("owner", LoaderManager.name(owner)),
                    Messages.lazy("scope", () -> scope(loader, "invite-scope-all-of", owner)), Messages.lazy("duration", duration));
        }
        plugin.discord().log("loader-invite", "owner", LoaderManager.name(owner), "player", name(target),
                "scope", messages().server(() -> scope(loader, "invite-scope-all-of", owner)),
                "time", messages().server(duration), "admin", sender.getName());
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
                Messages.lazy("scope", () -> scope(loader, "invite-scope-all", owner)));
        Player online = org.bukkit.Bukkit.getPlayer(member);
        if (online != null) {
            messages().send(online, "uninvite-notify", tag("owner", LoaderManager.name(owner)),
                    Messages.lazy("scope", () -> scope(loader, "invite-scope-all-of", owner)));
        }
        plugin.discord().log("loader-uninvite", "owner", LoaderManager.name(owner), "player", memberName,
                "scope", messages().server(() -> scope(loader, "invite-scope-all-of", owner)),
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
        // An excluded account cannot be linked with another one.
        if (loaders().isLinked(player.getUniqueId())) {
            unlink(org.bukkit.Bukkit.getConsoleSender(), player.getUniqueId());
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
        String type = limitType(kind);
        messages().send(sender, limit == null ? "limit-reset" : "limit-set",
                tag("player", name), tag("limit", effective), tag("type", type));
        notifyOwner(target.getUniqueId(), "limit-notify", tag("limit", effective), Messages.lazy("type", () -> limitType(kind)));
        plugin.discord().log("admin-limit", "admin", sender.getName(), "player", name, "limit", effective, "type", messages().server(() -> limitType(kind)));
        int switchedOff = enforceLimit(target, kind);
        if (switchedOff > 0) {
            messages().send(sender, kind == LoaderManager.LimitKind.DUMMY ? "dummy-limit-enforced" : "active-limit-enforced",
                    tag("player", name), tag("count", switchedOff));
        }
    }

    private String limitType(LoaderManager.LimitKind kind) {
        return messages().plain(switch (kind) {
            case PLACE -> "limit-type-place";
            case ACTIVE -> "limit-type-active";
            case DUMMY -> "limit-type-dummy";
        });
    }

    /**
     * After a lower limit: switches off the newest active loaders (active limit) or dummies (dummy limit) and tells the
     * owner. @return how many were switched off
     */
    private int enforceLimit(OfflinePlayer target, LoaderManager.LimitKind kind) {
        int switchedOff = switch (kind) {
            case ACTIVE -> loaders().enforceActiveLimit(target).size();
            case DUMMY -> loaders().enforceDummyLimit(target).size();
            case PLACE -> 0;
        };
        if (switchedOff > 0) {
            notifyOwner(target.getUniqueId(), kind == LoaderManager.LimitKind.DUMMY ? "dummy-limit-enforced-notify"
                    : "active-limit-enforced-notify", tag("count", switchedOff));
        }
        return switchedOff;
    }

    /** Bans the player: all their loaders are deleted and their loader items taken away. */
    public void ban(CommandSender sender, OfflinePlayer target, long duration, String reason) {
        if (!sender.hasPermission(PERM_BAN)) {
            messages().send(sender, "no-permission");
            return;
        }
        String cleanReason = reason == null || reason.isBlank() ? messages().server(() -> messages().plain("ban-no-reason")) : reason;
        UUID uuid = target.getUniqueId();
        int deleted = loaders().deleteAll(loaders().main(uuid));
        loaders().ban(uuid, duration, cleanReason, sender.getName());
        Player online = target.getPlayer();
        if (online != null) {
            plugin.loaderItem().remove(online, Integer.MAX_VALUE);
        }
        String name = name(target);
        LoaderManager.Ban banned = loaders().ban(uuid);
        java.util.function.Supplier<String> durationText = () -> Texts.banDuration(plugin, banned);
        messages().send(sender, "ban-set", tag("player", name), Messages.lazy("duration", durationText),
                tag("reason", cleanReason), tag("count", deleted));
        notifyOwner(uuid, "ban-notify", Messages.lazy("duration", durationText), tag("reason", cleanReason));
        plugin.discord().log("admin-ban", "admin", sender.getName(), "player", name,
                "time", messages().server(durationText), "reason", cleanReason, "count", deleted);
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
        for (Loader loader : loaders().byOwner(loaders().main(target.getUniqueId()))) {
            delete(sender, loader);
        }
    }

    public void reload(CommandSender sender) {
        plugin.reload();
        messages().send(sender, "reloaded");
        plugin.discord().log("admin-reload", "admin", sender.getName());
    }

    // ------------------------------------------------------------- helpers

    /** Tells the owner - every online account of their link group - about something. */
    private void notifyOwner(UUID owner, String key, TagResolver... resolvers) {
        for (UUID account : loaders().group(owner)) {
            Player online = org.bukkit.Bukkit.getPlayer(account);
            if (online != null) {
                messages().send(online, key, resolvers);
            }
        }
    }

    /** Discord placeholders of a loader plus extra key/value pairs. */
    public Object[] discordTags(Loader loader, Object... extra) {
        Object[] base = {
                "name", messages().server(() -> Texts.name(plugin, loader)),
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
