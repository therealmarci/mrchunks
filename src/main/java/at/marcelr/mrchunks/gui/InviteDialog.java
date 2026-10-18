package at.marcelr.mrchunks.gui;

import at.marcelr.mrchunks.Loader;
import at.marcelr.mrchunks.LoaderManager;
import at.marcelr.mrchunks.MRChunks;
import at.marcelr.mrchunks.Messages;
import at.marcelr.mrchunks.Texts;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static at.marcelr.mrchunks.Messages.tag;

/**
 * Dialog to invite a player: name field plus a choice of the durations from invites.durations.
 * One loader ({@code loader != null}) or all loaders of {@code owner}. Afterwards the members menu opens again.
 */
public final class InviteDialog {

    private static final String PLAYER = "player";
    private static final String DURATION = "duration";

    private InviteDialog() {
    }

    public static void open(MRChunks plugin, Player viewer, UUID owner, Loader loader, Menu returnTo) {
        Messages messages = plugin.messages();
        Integer loaderId = loader == null ? null : loader.id();
        String scope = loader != null ? Texts.name(plugin, loader)
                : messages.plain("invite-scope-all-of", "owner", LoaderManager.name(owner));
        List<Long> durations = plugin.settings().invites().durations();

        List<SingleOptionDialogInput.OptionEntry> options = new ArrayList<>();
        for (int i = 0; i < durations.size(); i++) {
            options.add(SingleOptionDialogInput.OptionEntry.create(String.valueOf(i),
                    net.kyori.adventure.text.Component.text(Texts.duration(plugin, durations.get(i))), i == 0));
        }

        ClickCallback.Options once = ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(10)).build();
        DialogAction save = DialogAction.customClick((response, audience) -> {
            String name = response.getText(PLAYER);
            String choice = response.getText(DURATION);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!viewer.isOnline()) {
                    return;
                }
                Loader current = loaderId == null ? null : plugin.loaders().byId(loaderId);
                if (loaderId != null && current == null) {
                    return; // deleted meanwhile
                }
                int index = parse(choice, durations.size());
                plugin.adminActions().invite(viewer, owner, current, name, durations.get(index));
                returnTo.open();
            });
        }, once);
        DialogAction cancel = DialogAction.customClick((response, audience) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (viewer.isOnline()) {
                        returnTo.open();
                    }
                }), once);

        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(messages.get("invite-title", tag("scope", scope)))
                        .canCloseWithEscape(true)
                        .body(List.of(DialogBody.plainMessage(messages.get("invite-hint", tag("scope", scope)))))
                        .inputs(List.of(
                                DialogInput.text(PLAYER, messages.get("invite-label-player")).maxLength(16).width(200).build(),
                                DialogInput.singleOption(DURATION, messages.get("invite-label-duration"), options)
                                        .width(200).build()))
                        .build())
                .type(DialogType.confirmation(
                        ActionButton.create(messages.get("invite-save"), null, 120, save),
                        ActionButton.create(messages.get("invite-cancel"), null, 120, cancel))));
        viewer.closeInventory();
        viewer.showDialog(dialog);
    }

    private static int parse(String choice, int size) {
        try {
            int index = Integer.parseInt(choice);
            return index >= 0 && index < size ? index : 0;
        } catch (NumberFormatException | NullPointerException e) {
            return 0;
        }
    }
}
