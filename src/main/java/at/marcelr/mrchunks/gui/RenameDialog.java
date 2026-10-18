package at.marcelr.mrchunks.gui;

import at.marcelr.mrchunks.Loader;
import at.marcelr.mrchunks.MRChunks;
import at.marcelr.mrchunks.Messages;
import at.marcelr.mrchunks.Texts;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;

import static at.marcelr.mrchunks.Messages.tag;

/**
 * Native Minecraft dialog with a text field to name a loader. "Save" applies the name (empty = default name),
 * "Cancel" closes it; afterwards the loader's menu opens again.
 */
public final class RenameDialog {

    private static final String INPUT = "name";

    private RenameDialog() {
    }

    public static void open(MRChunks plugin, Player viewer, Loader loader, Menu parent) {
        Messages messages = plugin.messages();
        int loaderId = loader.id();
        int max = plugin.settings().gui().maxNameLength();
        TagResolver tags = TagResolver.resolver(Texts.loader(plugin, loader), tag("max", max));

        DialogAction save = DialogAction.customClick((response, audience) -> {
            String text = response.getText(INPUT);
            // Run on the main thread: the click may arrive on the network thread.
            Bukkit.getScheduler().runTask(plugin, () -> {
                Loader current = plugin.loaders().byId(loaderId);
                if (current != null && viewer.isOnline()) {
                    plugin.adminActions().rename(viewer, current, text);
                    new LoaderMenu(plugin, viewer, loaderId, parent).open();
                }
            });
        }, ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(10)).build());

        DialogAction cancel = DialogAction.customClick((response, audience) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (viewer.isOnline() && plugin.loaders().byId(loaderId) != null) {
                new LoaderMenu(plugin, viewer, loaderId, parent).open();
            }
        }), ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(10)).build());

        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(messages.get("rename-title", tags))
                        .canCloseWithEscape(true)
                        .body(List.of(DialogBody.plainMessage(messages.get("rename-hint", tags))))
                        .inputs(List.of(DialogInput.text(INPUT, messages.get("rename-label", tags))
                                .initial(loader.name() == null ? "" : loader.name())
                                .maxLength(max)
                                .width(250)
                                .build()))
                        .build())
                .type(DialogType.confirmation(
                        ActionButton.create(messages.get("rename-save", tags), null, 120, save),
                        ActionButton.create(messages.get("rename-cancel", tags), null, 120, cancel))));
        viewer.closeInventory();
        viewer.showDialog(dialog);
    }
}
