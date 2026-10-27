package at.marcelr.mrchunks.command;

import at.marcelr.mrchunks.AdminActions;
import at.marcelr.mrchunks.DiscordWebhook;
import at.marcelr.mrchunks.Loader;
import at.marcelr.mrchunks.LoaderManager;
import at.marcelr.mrchunks.MRChunks;
import at.marcelr.mrchunks.Messages;
import at.marcelr.mrchunks.Settings;
import at.marcelr.mrchunks.Texts;
import at.marcelr.mrchunks.UpdateChecker;
import at.marcelr.mrchunks.dummy.Dummies;
import at.marcelr.mrchunks.gui.AdminMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import static at.marcelr.mrchunks.Messages.tag;

/** /mrchunks - admin commands. Every sub command checks its own permission. */
public final class MRChunksCommand implements TabExecutor {

    private static final String PERM_GUI = "mrchunks.admin.gui";
    private static final String PERM_RELOAD = "mrchunks.admin.reload";
    private static final String PERM_MAP = "mrchunks.admin.map";
    private static final String PERM_DISCORD = "mrchunks.admin.discord";
    private static final List<String> ADMIN_PERMS = List.of(PERM_GUI, PERM_RELOAD, PERM_MAP, PERM_DISCORD,
            AdminActions.PERM_MANAGE, AdminActions.PERM_TELEPORT, AdminActions.PERM_LIMIT, AdminActions.PERM_BAN);

    private final MRChunks plugin;

    public MRChunksCommand(MRChunks plugin) {
        this.plugin = plugin;
    }

    private Messages messages() {
        return plugin.messages();
    }

    private AdminActions actions() {
        return plugin.adminActions();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // From the console every text is English, also values built along the way.
        return plugin.messages().as(sender, () -> handle(sender, command, label, args));
    }

    private boolean handle(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            messages().send(sender, ADMIN_PERMS.stream().anyMatch(sender::hasPermission) ? "admin-usage" : "no-permission");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "version", "ver" -> version(sender);
            case "gui", "menu" -> {
                if (!(sender instanceof Player player)) {
                    messages().send(sender, "players-only");
                } else if (check(sender, PERM_GUI)) {
                    new AdminMenu(plugin, player).open();
                }
            }
            case "tp", "teleport" -> {
                if (!(sender instanceof Player player)) {
                    messages().send(sender, "players-only");
                } else {
                    withLoader(sender, args, loader -> actions().teleport(player, loader));
                }
            }
            case "toggle" -> withLoader(sender, args, loader -> actions().toggle(sender, loader));
            case "delete", "remove" -> withLoader(sender, args, loader -> actions().delete(sender, loader));
            case "unlock", "entsperren" -> withLoader(sender, args, loader -> actions().unlock(sender, loader));
            case "dummy" -> withLoader(sender, args, loader -> actions().toggleDummy(sender, loader));
            case "link" -> {
                if (check(sender, AdminActions.PERM_MANAGE)) {
                    withPlayer(sender, args, 1, main -> withPlayer(sender, args, 2,
                            second -> actions().adminLink(sender, main, second)));
                }
            }
            case "unlink" -> withPlayer(sender, args, 1, target -> actions().unlink(sender, target.getUniqueId()));
            case "lag" -> {
                if (check(sender, AdminActions.PERM_MANAGE)) {
                    lag(sender);
                }
            }
            case "limit" -> limit(sender, args);
            case "ban" -> ban(sender, args);
            case "unban" -> withPlayer(sender, args, 1, target -> actions().unban(sender, target));
            case "bans" -> {
                if (check(sender, AdminActions.PERM_BAN)) {
                    bans(sender);
                }
            }
            case "map", "web" -> {
                if (check(sender, PERM_MAP)) {
                    map(sender, args);
                }
            }
            case "discord" -> {
                if (check(sender, PERM_DISCORD)) {
                    discord(sender, args);
                }
            }
            case "reload" -> {
                if (check(sender, PERM_RELOAD)) {
                    actions().reload(sender);
                }
            }
            default -> messages().send(sender, "admin-usage");
        }
        return true;
    }

    // ------------------------------------------------------------- loaders

    /** /mrchunks limit <player> <place|active> <amount|reset> - one command for both caps. */
    private void limit(CommandSender sender, String[] args) {
        if (!check(sender, AdminActions.PERM_LIMIT)) {
            return;
        }
        if (args.length < 4) {
            messages().send(sender, "admin-usage");
            return;
        }
        LoaderManager.LimitKind kind = switch (args[2].toLowerCase(Locale.ROOT)) {
            case "place", "setzen", "set" -> LoaderManager.LimitKind.PLACE;
            case "active", "aktiv" -> LoaderManager.LimitKind.ACTIVE;
            case "dummy", "dummies" -> LoaderManager.LimitKind.DUMMY;
            default -> null;
        };
        if (kind == null) {
            messages().send(sender, "admin-usage");
            return;
        }
        Integer limit = null;
        if (!args[3].equalsIgnoreCase("reset")) {
            try {
                limit = Math.max(0, Integer.parseInt(args[3]));
            } catch (NumberFormatException e) {
                messages().send(sender, "invalid-number", tag("input", args[3]));
                return;
            }
        }
        Integer finalLimit = limit;
        withPlayer(sender, args, 1, target -> actions().setLimit(sender, target, kind, finalLimit));
    }

    /** /mrchunks ban <player> [duration|permanent] [reason...] - without a duration the ban is permanent. */
    private void ban(CommandSender sender, String[] args) {
        if (!check(sender, AdminActions.PERM_BAN)) {
            return;
        }
        long duration = Settings.PERMANENT;
        int reasonStart = 2;
        if (args.length >= 3) {
            long parsed = Settings.parseBanDuration(args[2]);
            if (parsed != 0) {
                duration = parsed;
                reasonStart = 3;
            }
        }
        String reason = args.length > reasonStart ? String.join(" ", Arrays.copyOfRange(args, reasonStart, args.length)) : "";
        long finalDuration = duration;
        withPlayer(sender, args, 1, target -> actions().ban(sender, target, finalDuration, reason));
    }

    private void bans(CommandSender sender) {
        var bans = plugin.loaders().activeBans();
        if (bans.isEmpty()) {
            messages().send(sender, "bans-empty");
            return;
        }
        messages().send(sender, "bans-header");
        bans.forEach((uuid, ban) -> messages().send(sender, "bans-entry",
                tag("player", LoaderManager.name(uuid)),
                tag("duration", Texts.banDuration(plugin, ban)),
                tag("reason", ban.reason()),
                tag("by", ban.by())));
    }

    /** /mrchunks lag - locked and conspicuous loaders with their worst metric. */
    private void lag(CommandSender sender) {
        if (!plugin.settings().lag().enabled()) {
            messages().send(sender, "lag-disabled");
        }
        if (plugin.loaders().paused()) {
            messages().send(sender, "pause-status", plugin.pause().tags());
        }
        var list = plugin.lag().conspicuous();
        if (list.isEmpty()) {
            messages().send(sender, "lag-list-empty");
            return;
        }
        messages().send(sender, "lag-list-header");
        for (Loader loader : list) {
            var lock = plugin.loaders().lagLock(loader);
            var activity = plugin.lag().activity(loader);
            if (lock != null) {
                messages().send(sender, "lag-list-locked", plugin.lag().tags(loader, lock));
            } else if (activity != null) {
                var metric = activity.worst();
                messages().send(sender, "lag-list-entry", Texts.loader(plugin, loader),
                        tag("metric", messages().plain("lag-metric-" + metric.key())),
                        tag("value", at.marcelr.mrchunks.lag.LagMonitor.format(activity.value(metric))),
                        tag("limit", at.marcelr.mrchunks.lag.LagMonitor.format(plugin.settings().lag().limits().warn(metric))));
            }
        }
    }

    // ---------------------------------------------------------------- misc

    /** /mrchunks map - squaremap status; /mrchunks map setup - apply the recommended squaremap settings again. */
    private void map(CommandSender sender, String[] args) {
        if (args.length > 1 && args[1].equalsIgnoreCase("setup")) {
            if (!plugin.maps().squaremapInstalled()) {
                messages().send(sender, "map-squaremap-missing");
                return;
            }
            plugin.maps().runSetup(sender, true);
            return;
        }
        if (plugin.maps().squaremapActive()) {
            String url = plugin.maps().mapUrl();
            Component link = url.isEmpty() ? messages().get("map-url-unknown")
                    : messages().get("map-link-label", tag("url", url)).clickEvent(ClickEvent.openUrl(url));
            messages().send(sender, "map-status-on", tag("link", link));
        } else {
            messages().send(sender, "map-status-off", tag("status", plugin.maps().squaremapStatus()));
        }
    }

    private void discord(CommandSender sender, String[] args) {
        DiscordWebhook discord = plugin.discord();
        if (discord.enabled()) {
            int[] counts = discord.eventCounts();
            messages().send(sender, "discord-status-active", tag("events", counts[0]), tag("total", counts[1]));
        } else {
            messages().send(sender, "discord-status-inactive", tag("reason", discord.disabledReason()));
        }
        String last = discord.lastResult();
        messages().send(sender, "discord-status-last", tag("result", last == null ? messages().plain("discord-status-never") : last));
        if (args.length < 2 || !args[1].equalsIgnoreCase("test")) {
            if (discord.enabled()) {
                messages().send(sender, "discord-test-hint");
            }
            return;
        }
        if (!discord.enabled()) {
            return;
        }
        messages().send(sender, "discord-test-sending");
        discord.test(sender).whenComplete((error, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
            String problem = throwable != null ? throwable.getMessage() : error;
            if (problem == null) {
                messages().send(sender, "discord-test-ok");
            } else {
                messages().send(sender, "discord-test-failed", tag("reason", problem));
            }
        }));
    }

    private void version(CommandSender sender) {
        sendVersion(plugin, sender);
    }

    /** Version, author, update status and links - also used by the help button in /chunkloader. */
    public static void sendVersion(MRChunks plugin, CommandSender sender) {
        Messages messages = plugin.messages();
        UpdateChecker updates = plugin.updateChecker();
        messages.send(sender, "version",
                tag("version", updates.currentVersion()), tag("mc", updates.minecraftVersion()),
                tag("author", String.join(", ", plugin.getPluginMeta().getAuthors())));
        Runnable links = () -> {
            sender.sendMessage(Component.empty());
            messages.send(sender, "version-links",
                    tag("website", link("Website", updates.websitePage())),
                    tag("modrinth", link("Website", updates.websitePage())),
                    tag("github", link("GitHub", updates.githubPage())));
        };
        if (plugin.settings().update().enabled()) {
            updates.checkNow(sender, links);
        } else {
            updates.sendStatus(sender);
            links.run();
        }
    }

    private static Component link(String label, String url) {
        return Component.text(label).color(NamedTextColor.AQUA).decorate(TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.openUrl(url));
    }

    // ------------------------------------------------------------- helpers

    /** Resolves {@code <player> <number>} (the number players see, e.g. 3 for "Chunkloader 3"). */
    private void withLoader(CommandSender sender, String[] args, Consumer<Loader> action) {
        if (args.length < 3) {
            messages().send(sender, "admin-usage");
            return;
        }
        OfflinePlayer owner = resolvePlayer(sender, args[1]);
        if (owner == null) {
            return;
        }
        int number;
        try {
            number = Integer.parseInt(args[2].replace("#", ""));
        } catch (NumberFormatException e) {
            messages().send(sender, "invalid-number", tag("input", args[2]));
            return;
        }
        Loader loader = plugin.loaders().byNumber(owner.getUniqueId(), number);
        if (loader == null) {
            messages().send(sender, "loader-not-found", tag("player", AdminActions.name(owner)), tag("number", number));
        } else {
            action.accept(loader);
        }
    }

    private void withPlayer(CommandSender sender, String[] args, int index, Consumer<OfflinePlayer> action) {
        if (args.length <= index) {
            messages().send(sender, "admin-usage");
            return;
        }
        OfflinePlayer target = resolvePlayer(sender, args[index]);
        if (target != null) {
            action.accept(target);
        }
    }

    private OfflinePlayer resolvePlayer(CommandSender sender, String name) {
        OfflinePlayer target = Bukkit.getPlayerExact(name);
        if (target == null) {
            target = Bukkit.getOfflinePlayerIfCached(name);
        }
        if (target == null) {
            messages().send(sender, "player-not-found", tag("player", name));
        }
        return target;
    }

    private boolean check(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        messages().send(sender, "no-permission");
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.add("version");
            addIf(sender, PERM_GUI, options, "gui");
            addIf(sender, AdminActions.PERM_TELEPORT, options, "tp");
            addIf(sender, AdminActions.PERM_MANAGE, options, "toggle", "delete", "unlock", "lag", "dummy", "link", "unlink");
            addIf(sender, AdminActions.PERM_LIMIT, options, "limit");
            addIf(sender, AdminActions.PERM_BAN, options, "ban", "unban", "bans");
            addIf(sender, PERM_MAP, options, "map");
            addIf(sender, PERM_DISCORD, options, "discord");
            addIf(sender, PERM_RELOAD, options, "reload");
        } else {
            String sub = args[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "tp", "teleport", "toggle", "delete", "remove", "unlock", "entsperren", "dummy" -> {
                    if (args.length == 2) {
                        plugin.loaders().all().forEach(l -> options.add(LoaderManager.name(l.owner())));
                    } else if (args.length == 3) {
                        OfflinePlayer owner = Bukkit.getOfflinePlayerIfCached(args[1]);
                        if (owner != null) {
                            plugin.loaders().byOwner(owner.getUniqueId())
                                    .forEach(l -> options.add(String.valueOf(l.number())));
                        }
                    }
                }
                case "limit", "ban" -> {
                    if (args.length == 2) {
                        Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
                        plugin.loaders().knownPlayers().forEach(u -> options.add(LoaderManager.name(u)));
                    } else if (args.length == 3 && sub.equals("limit")) {
                        options.addAll(List.of("place", "active", "dummy"));
                    } else if (args.length == 4 && sub.equals("limit")) {
                        options.addAll(List.of("3", "5", "10", "reset"));
                    } else if (args.length == 3 && sub.equals("ban")) {
                        options.addAll(List.of("permanent", "1d", "7d", "30d"));
                    }
                }
                case "unban" -> {
                    if (args.length == 2) {
                        plugin.loaders().activeBans().keySet().forEach(u -> options.add(LoaderManager.name(u)));
                    }
                }
                case "link" -> {
                    if (args.length == 2 || args.length == 3) {
                        Bukkit.getOnlinePlayers().stream().filter(p -> !Dummies.isDummy(p))
                                .forEach(p -> options.add(p.getName()));
                    }
                }
                case "unlink" -> {
                    if (args.length == 2) {
                        plugin.loaders().links().forEach((second, main) -> {
                            options.add(LoaderManager.name(second));
                            options.add(LoaderManager.name(main));
                        });
                    }
                }
                case "discord" -> {
                    if (args.length == 2) {
                        options.add("test");
                    }
                }
                case "map" -> {
                    if (args.length == 2) {
                        options.add("setup");
                    }
                }
                default -> {
                }
            }
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(o -> !o.toLowerCase(Locale.ROOT).startsWith(prefix));
        return options.stream().distinct().toList();
    }

    private static void addIf(CommandSender sender, String permission, List<String> target, String... values) {
        if (sender.hasPermission(permission)) {
            target.addAll(List.of(values));
        }
    }
}
