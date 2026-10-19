package at.marcelr.mrchunks.command;

import at.marcelr.mrchunks.MRChunks;
import at.marcelr.mrchunks.gui.MyLoadersMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * /chunkloader - the only command for players: opens the menu with the own loaders. Everything else (getting and
 * returning loader items, shared loaders, invites, web map, help, version) is a button in there.
 */
public final class ChunkloaderCommand implements TabExecutor {

    private final MRChunks plugin;

    public ChunkloaderCommand(MRChunks plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player) {
            new MyLoadersMenu(plugin, player).open();
        } else {
            plugin.messages().as(sender, () -> {
                plugin.messages().send(sender, "players-only-menu");
                return null;
            });
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return List.of();
    }
}
