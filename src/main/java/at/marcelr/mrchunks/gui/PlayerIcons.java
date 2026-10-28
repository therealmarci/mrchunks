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
import java.util.UUID;

import static at.marcelr.mrchunks.Messages.tag;

/** Head of a player with their loader count, limit and status (online / offline / banned). */
public final class PlayerIcons {

    private PlayerIcons() {
    }

    public static ItemStack head(MRChunks plugin, OfflinePlayer target, boolean clickHint) {
        Messages messages = plugin.messages();
        LoaderManager loaders = plugin.loaders();
        // Linked accounts: the loaders and limits of the main account.
        UUID main = loaders.main(target.getUniqueId());
        List<Loader> owned = loaders.byOwner(main);
        LoaderManager.Limit limit = loaders.budget(target);

        Component source = messages.get(switch (limit.source()) {
            case CUSTOM -> "gui-limit-custom";
            case PERMISSION, UNLIMITED -> "gui-limit-permission";
            case DEFAULT -> "gui-limit-default";
        });
        LoaderManager.Ban ban = loaders.groupBan(target.getUniqueId());
        Component status = ban != null
                ? messages.get("gui-banned", tag("duration", Texts.banDuration(plugin, ban)), tag("reason", ban.reason()))
                : messages.get(target.isOnline() ? "gui-online" : "gui-offline");

        Items item = Items.head(target)
                .name(messages.item("gui-player-name", tag("player", AdminActions.name(target))))
                .lore(messages.lore("gui-player-lore",
                        tag("placed", owned.size()),
                        tag("limit", loaders.maxLoaders()),
                        tag("active", owned.stream().filter(Loader::active).count()),
                        tag("used", loaders.usedBudget(main)),
                        tag("budget", Texts.limit(limit)),
                        tag("limit-source", source),
                        tag("status", status)))
                .glint(ban != null);
        if (plugin.dummies().enabled()) {
            item.lore(messages.item("gui-player-dummies", tag("dummies", loaders.dummyCount(main)),
                    tag("dummy-limit", Texts.limit(loaders.limit(target, LoaderManager.LimitKind.DUMMY)))));
        }
        if (!main.equals(target.getUniqueId())) {
            item.lore(messages.item("gui-player-linked-second", tag("main", LoaderManager.name(main))));
        } else if (!loaders.secondaries(main).isEmpty()) {
            item.lore(messages.item("gui-player-linked-main", tag("seconds", String.join(", ",
                    loaders.secondaries(main).stream().map(LoaderManager::name).toList()))));
        }
        if (clickHint) {
            item.lore(Component.empty()).lore(messages.item("gui-player-click"));
        }
        return item.build();
    }
}
