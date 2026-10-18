package at.marcelr.mrchunks.gui;

import at.marcelr.mrchunks.AdminActions;
import at.marcelr.mrchunks.Loader;
import at.marcelr.mrchunks.LoaderManager;
import at.marcelr.mrchunks.MRChunks;
import at.marcelr.mrchunks.Messages;
import at.marcelr.mrchunks.Texts;
import net.kyori.adventure.text.Component;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;

import java.util.List;

import static at.marcelr.mrchunks.Messages.tag;

/** Head of a player with their loader count, limit and status (online / offline / banned). */
public final class PlayerIcons {

    private PlayerIcons() {
    }

    public static ItemStack head(MRChunks plugin, OfflinePlayer target, boolean clickHint) {
        Messages messages = plugin.messages();
        LoaderManager loaders = plugin.loaders();
        List<Loader> owned = loaders.byOwner(target.getUniqueId());
        LoaderManager.Limit limit = loaders.limit(target);

        Component source = messages.get(switch (limit.source()) {
            case CUSTOM -> "gui-limit-custom";
            case PERMISSION, UNLIMITED -> "gui-limit-permission";
            case DEFAULT -> "gui-limit-default";
        });
        LoaderManager.Ban ban = loaders.ban(target.getUniqueId());
        Component status = ban != null
                ? messages.get("gui-banned", tag("duration", Texts.banDuration(plugin, ban)), tag("reason", ban.reason()))
                : messages.get(target.isOnline() ? "gui-online" : "gui-offline");

        Items item = Items.head(target)
                .name(messages.item("gui-player-name", tag("player", AdminActions.name(target))))
                .lore(messages.lore("gui-player-lore",
                        tag("placed", owned.size()),
                        tag("active", owned.stream().filter(Loader::active).count()),
                        tag("limit", Texts.limit(limit)),
                        tag("active-limit", Texts.limit(loaders.activeLimit(target))),
                        tag("limit-source", source),
                        tag("status", status)))
                .glint(ban != null);
        if (clickHint) {
            item.lore(Component.empty()).lore(messages.item("gui-player-click"));
        }
        return item.build();
    }
}
