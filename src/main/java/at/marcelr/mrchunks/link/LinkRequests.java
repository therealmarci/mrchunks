package at.marcelr.mrchunks.link;

import at.marcelr.mrchunks.AdminActions;
import at.marcelr.mrchunks.MRChunks;
import at.marcelr.mrchunks.Messages;
import at.marcelr.mrchunks.dummy.Dummies;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static at.marcelr.mrchunks.Messages.tag;

/**
 * Requests to link two accounts: the future main account asks, the other account accepts or declines (dialog, or the
 * buttons in the chat if the dialog was closed). Both have to be online; a request runs out after
 * linked-accounts.request-seconds. Main thread only.
 */
public final class LinkRequests {

    private final MRChunks plugin;
    /** future second account -> open request */
    private final Map<UUID, Request> open = new HashMap<>();

    private record Request(UUID main, long until) {
    }

    public LinkRequests(MRChunks plugin) {
        this.plugin = plugin;
    }

    /** {@code main} asks {@code target} to become their second account. */
    public void request(Player main, Player target) {
        Messages messages = plugin.messages();
        if (!plugin.settings().links().enabled()) {
            messages.send(main, "links-disabled");
            return;
        }
        if (!main.hasPermission(AdminActions.PERM_LINK)) {
            messages.send(main, "no-permission");
            return;
        }
        if (!target.isOnline() || Dummies.isDummy(target)) {
            messages.send(main, "player-not-found", tag("player", target.getName()));
            return;
        }
        String problem = plugin.adminActions().linkProblem(main, target);
        if (problem != null) {
            messages.send(main, problem, tag("player", target.getName()),
                    tag("max", plugin.settings().links().maxAccounts()));
            return;
        }
        int seconds = plugin.settings().links().requestSeconds();
        open.put(target.getUniqueId(), new Request(main.getUniqueId(), System.currentTimeMillis() + seconds * 1000L));
        messages.send(main, "link-request-sent", tag("player", target.getName()), tag("seconds", seconds));
        ask(target, main, seconds);
    }

    /** Dialog plus chat buttons for the asked player, in their language. */
    private void ask(Player target, Player main, int seconds) {
        plugin.messages().run(target, () -> askNow(target, main, seconds));
    }

    private void askNow(Player target, Player main, int seconds) {
        Messages messages = plugin.messages();
        UUID targetId = target.getUniqueId();
        UUID mainId = main.getUniqueId();
        int loaders = plugin.loaders().count(targetId);
        var tags = new net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[]{
                tag("main", main.getName()), tag("count", loaders), tag("seconds", seconds)};

        ClickCallback.Options once = ClickCallback.Options.builder().uses(1).lifetime(Duration.ofSeconds(seconds)).build();
        DialogAction accept = DialogAction.customClick((response, audience) ->
                Bukkit.getScheduler().runTask(plugin, () -> accept(targetId, mainId)), once);
        DialogAction decline = DialogAction.customClick((response, audience) ->
                Bukkit.getScheduler().runTask(plugin, () -> decline(targetId, mainId)), once);
        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(messages.get("link-dialog-title", tags))
                        .canCloseWithEscape(true)
                        .body(List.of(DialogBody.plainMessage(messages.get("link-dialog-text", tags))))
                        .build())
                .type(DialogType.confirmation(
                        ActionButton.create(messages.get("link-dialog-accept"), null, 120, accept),
                        ActionButton.create(messages.get("link-dialog-decline"), null, 120, decline))));
        target.closeInventory();
        target.showDialog(dialog);

        var acceptButton = messages.get("link-accept-label").clickEvent(ClickEvent.callback(
                audience -> Bukkit.getScheduler().runTask(plugin, () -> accept(targetId, mainId)), once));
        var declineButton = messages.get("link-decline-label").clickEvent(ClickEvent.callback(
                audience -> Bukkit.getScheduler().runTask(plugin, () -> decline(targetId, mainId)), once));
        messages.send(target, "link-request-received", tag("main", main.getName()), tag("count", loaders),
                tag("seconds", seconds), tag("accept", acceptButton), tag("decline", declineButton));
    }

    private Request take(UUID targetId, UUID mainId) {
        Request request = open.get(targetId);
        if (request == null || !request.main().equals(mainId)) {
            return null;
        }
        open.remove(targetId);
        return request.until() < System.currentTimeMillis() ? null : request;
    }

    private void accept(UUID targetId, UUID mainId) {
        Player target = Bukkit.getPlayer(targetId);
        if (target == null) {
            return;
        }
        Messages messages = plugin.messages();
        if (take(targetId, mainId) == null) {
            messages.send(target, "link-request-expired");
            return;
        }
        Player main = Bukkit.getPlayer(mainId);
        if (main == null) {
            messages.send(target, "link-main-offline", tag("player", Bukkit.getOfflinePlayer(mainId).getName()));
            return;
        }
        String problem = plugin.adminActions().link(main, target, target.getName());
        if (problem != null) {
            for (Player player : List.of(main, target)) {
                messages.send(player, problem, tag("player", target.getName()),
                        tag("max", plugin.settings().links().maxAccounts()));
            }
        }
    }

    private void decline(UUID targetId, UUID mainId) {
        if (take(targetId, mainId) == null) {
            return;
        }
        Player target = Bukkit.getPlayer(targetId);
        Player main = Bukkit.getPlayer(mainId);
        if (target != null) {
            plugin.messages().send(target, "link-declined-self");
        }
        if (main != null) {
            plugin.messages().send(main, "link-declined", tag("player", target == null ? "?" : target.getName()));
        }
    }
}
