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
import at.marcelr.mrchunks.gui.AdminMenu;
import at.marcelr.mrchunks.web.WebMap;
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
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import static at.marcelr.mrchunks.Messages.tag;

/** /mrchunks - admin commands. Every sub command checks its own permission. */
public final class MRChunksCommand implements TabExecutor {

    private static final String PERM_GUI = "mrchunks.admin.gui";
    private static final String PERM_LIST = "mrchunks.admin.list";
    private static final String PERM_RELOAD = "mrchunks.admin.reload";
    private static final String PERM_WEB = "mrchunks.admin.web";
    private static final String PERM_DISCORD = "mrchunks.admin.discord";
    private static final List<String> ADMIN_PERMS = List.of(PERM_GUI, PERM_LIST, PERM_RELOAD, PERM_WEB, PERM_DISCORD,
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
            case "list" -> {
                if (check(sender, PERM_LIST)) {
                    list(sender, args);
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
            case "limit" -> limit(sender, args);
            case "ban" -> ban(sender, args);
            case "unban" -> withPlayer(sender, args, 1, target -> actions().unban(sender, target));
            case "bans" -> {
                if (check(sender, AdminActions.PERM_BAN)) {
                    bans(sender);
                }
            }
            case "web" -> {
                if (check(sender, PERM_WEB)) {
                    web(sender);
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

    private void list(CommandSender sender, String[] args) {
        Collection<Loader> loaders = plugin.loaders().all();
        String filter = "";
        if (args.length > 1) {
            OfflinePlayer target = resolvePlayer(sender, args[1]);
            if (target == null) {
                return;
            }
            loaders = plugin.loaders().byOwner(target.getUniqueId());
            filter = " von " + AdminActions.name(target);
        }
        if (loaders.isEmpty()) {
            messages().send(sender, "list-empty");
            return;
        }
        messages().send(sender, "list-header", tag("filter", filter), tag("count", loaders.size()));
        for (Loader loader : loaders) {
            messages().send(sender, "list-entry", Texts.loader(plugin, loader));
        }
    }

    private void limit(CommandSender sender, String[] args) {
        if (!check(sender, AdminActions.PERM_LIMIT)) {
            return;
        }
        if (args.length < 3) {
            messages().send(sender, "admin-usage");
            return;
        }
        Integer limit = null;
        if (!args[2].equalsIgnoreCase("reset")) {
            try {
                limit = Math.max(0, Integer.parseInt(args[2]));
            } catch (NumberFormatException e) {
                messages().send(sender, "invalid-number", tag("input", args[2]));
                return;
            }
        }
        Integer finalLimit = limit;
        withPlayer(sender, args, 1, target -> actions().setLimit(sender, target, finalLimit));
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

    // ---------------------------------------------------------------- misc

    private void web(CommandSender sender) {
        WebMap web = plugin.webMap();
        if (web.running()) {
            String url = web.url();
            Component link = messages().get("web-link-label", tag("url", url)).clickEvent(ClickEvent.openUrl(url));
            messages().send(sender, "web-status-on", tag("link", link));
        } else if (web.error() != null) {
            messages().send(sender, "web-status-error", tag("reason", web.error()));
        } else {
            messages().send(sender, "web-status-off");
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
        discord.test().whenComplete((error, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
            String problem = throwable != null ? throwable.getMessage() : error;
            if (problem == null) {
                messages().send(sender, "discord-test-ok");
            } else {
                messages().send(sender, "discord-test-failed", tag("reason", problem));
            }
        }));
    }

    private void version(CommandSender sender) {
        UpdateChecker updates = plugin.updateChecker();
        messages().send(sender, "version",
                tag("version", updates.currentVersion()),
                tag("author", String.join(", ", plugin.getPluginMeta().getAuthors())));
        Runnable links = () -> {
            sender.sendMessage(Component.empty());
            messages().send(sender, "version-links",
                    tag("modrinth", link("Modrinth", updates.modrinthPage())),
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

    private void withLoader(CommandSender sender, String[] args, Consumer<Loader> action) {
        if (args.length < 2) {
            messages().send(sender, "admin-usage");
            return;
        }
        try {
            int id = Integer.parseInt(args[1].replace("#", ""));
            Loader loader = plugin.loaders().byId(id);
            if (loader == null) {
                messages().send(sender, "loader-not-found", tag("id", id));
            } else {
                action.accept(loader);
            }
        } catch (NumberFormatException e) {
            messages().send(sender, "invalid-number", tag("input", args[1]));
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
            addIf(sender, PERM_LIST, options, "list");
            addIf(sender, AdminActions.PERM_TELEPORT, options, "tp");
            addIf(sender, AdminActions.PERM_MANAGE, options, "toggle", "delete");
            addIf(sender, AdminActions.PERM_LIMIT, options, "limit");
            addIf(sender, AdminActions.PERM_BAN, options, "ban", "unban", "bans");
            addIf(sender, PERM_WEB, options, "web");
            addIf(sender, PERM_DISCORD, options, "discord");
            addIf(sender, PERM_RELOAD, options, "reload");
        } else {
            String sub = args[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "tp", "teleport", "toggle", "delete", "remove" -> {
                    if (args.length == 2) {
                        plugin.loaders().all().forEach(l -> options.add(String.valueOf(l.id())));
                    }
                }
                case "list", "limit", "ban" -> {
                    if (args.length == 2) {
                        Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
                        plugin.loaders().knownPlayers().forEach(u -> options.add(LoaderManager.name(u)));
                    } else if (args.length == 3 && sub.equals("limit")) {
                        options.addAll(List.of("5", "10", "reset"));
                    } else if (args.length == 3 && sub.equals("ban")) {
                        options.addAll(List.of("permanent", "1d", "7d", "30d"));
                    }
                }
                case "unban" -> {
                    if (args.length == 2) {
                        plugin.loaders().activeBans().keySet().forEach(u -> options.add(LoaderManager.name(u)));
                    }
                }
                case "discord" -> {
                    if (args.length == 2) {
                        options.add("test");
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
