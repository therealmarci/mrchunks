package at.marcelr.mrchunks.command;

import at.marcelr.mrchunks.AdminActions;
import at.marcelr.mrchunks.LoaderManager;
import at.marcelr.mrchunks.MRChunks;
import at.marcelr.mrchunks.Messages;
import at.marcelr.mrchunks.Texts;
import at.marcelr.mrchunks.gui.MyLoadersMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static at.marcelr.mrchunks.Messages.tag;

/**
 * /chunkloader give [anzahl]       - loader items into the inventory (within the limit)
 * /chunkloader remove [anzahl|all] - take loader items out of the inventory
 * /chunkloader list                - menu with the own loaders
 * /chunkloader info                - command help (the own loaders are in /chunkloader list)
 * /chunkloader map                 - link to the web map
 */
public final class ChunkloaderCommand implements TabExecutor {

    private static final String PERM_MAP = "mrchunks.map";

    private final MRChunks plugin;

    public ChunkloaderCommand(MRChunks plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // From the console every text is English, also values built along the way.
        return plugin.messages().as(sender, () -> handle(sender, command, label, args));
    }

    private boolean handle(CommandSender sender, Command command, String label, String[] args) {
        Messages messages = plugin.messages();
        if (!(sender instanceof Player player)) {
            messages.send(sender, "players-only");
            return true;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "give", "get" -> give(player, args);
            case "remove", "take" -> remove(player, args);
            case "list", "menu", "gui" -> {
                if (check(player, AdminActions.PERM_USE)) {
                    new MyLoadersMenu(plugin, player).open();
                }
            }
            case "info", "help", "hilfe" -> messages.send(player, "usage");
            case "map", "karte" -> {
                if (check(player, PERM_MAP)) {
                    sendMapLink(player);
                }
            }
            default -> messages.send(player, "usage");
        }
        return true;
    }

    private void give(Player player, String[] args) {
        Messages messages = plugin.messages();
        if (!check(player, AdminActions.PERM_USE)) {
            return;
        }
        if (AdminActions.isExempt(player)) {
            messages.send(player, "exempt");
            return;
        }
        LoaderManager loaders = plugin.loaders();
        LoaderManager.Ban ban = loaders.ban(player.getUniqueId());
        if (ban != null) {
            messages.send(player, "banned", tag("duration", Texts.banDuration(plugin, ban)), tag("reason", ban.reason()));
            return;
        }
        int wanted = 1;
        if (args.length > 1) {
            try {
                wanted = Math.max(1, Integer.parseInt(args[1]));
            } catch (NumberFormatException e) {
                messages.send(player, "invalid-number", tag("input", args[1]));
                return;
            }
        }
        LoaderManager.Limit limit = loaders.limit(player);
        int placed = loaders.count(player.getUniqueId());
        int held = plugin.loaderItem().count(player);
        long free = (long) limit.value() - placed - held;
        if (free <= 0) {
            messages.send(player, "give-none-left", tag("placed", placed), tag("held", held), tag("limit", Texts.limit(limit)));
            return;
        }
        int amount = (int) Math.min(wanted, free);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(plugin.loaderItem().create(amount));
        int notGiven = leftover.values().stream().mapToInt(ItemStack::getAmount).sum();
        int given = amount - notGiven;
        if (given == 0) {
            messages.send(player, "give-inventory-full");
            return;
        }
        if (given < wanted) {
            messages.send(player, notGiven > 0 ? "give-inventory-full" : "give-partial", tag("count", given));
        }
        messages.send(player, "give-ok", tag("count", given), tag("used", placed + held + given),
                tag("limit", Texts.limit(limit)));
    }

    private void remove(Player player, String[] args) {
        Messages messages = plugin.messages();
        if (!check(player, AdminActions.PERM_USE)) {
            return;
        }
        int max = Integer.MAX_VALUE;
        if (args.length > 1 && !args[1].equalsIgnoreCase("all")) {
            try {
                max = Math.max(1, Integer.parseInt(args[1]));
            } catch (NumberFormatException e) {
                messages.send(player, "invalid-number", tag("input", args[1]));
                return;
            }
        }
        int removed = plugin.loaderItem().remove(player, max);
        messages.send(player, removed == 0 ? "remove-none" : "remove-ok", tag("count", removed));
    }

    /** Clickable link to the squaremap web map (only if squaremap is installed and its address is known). */
    private void sendMapLink(Player player) {
        String url = plugin.maps().mapUrl();
        if (url.isEmpty()) {
            plugin.messages().send(player, "map-unavailable");
            return;
        }
        Component link = plugin.messages().get("map-link-label", tag("url", url)).clickEvent(ClickEvent.openUrl(url));
        plugin.messages().send(player, "map-link", tag("link", link));
    }

    private boolean check(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        plugin.messages().send(sender, "no-permission");
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            if (sender.hasPermission(AdminActions.PERM_USE)) {
                options.addAll(List.of("give", "remove", "list"));
            }
            options.add("info");
            if (sender.hasPermission(PERM_MAP)) {
                options.add("map");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
            options.addAll(List.of("1", "all"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            options.add("1");
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(o -> !o.startsWith(prefix));
        return options;
    }
}
