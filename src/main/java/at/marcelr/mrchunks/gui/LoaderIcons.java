package at.marcelr.mrchunks.gui;

import at.marcelr.mrchunks.Loader;
import at.marcelr.mrchunks.MRChunks;
import at.marcelr.mrchunks.Messages;
import at.marcelr.mrchunks.Texts;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** The item that represents one loader in menus (its block material, glowing while active). */
final class LoaderIcons {

    private LoaderIcons() {
    }

    static ItemStack loader(MRChunks plugin, Loader loader, boolean clickHint) {
        Messages messages = plugin.messages();
        Material material = loader.active() ? plugin.settings().item().material() : Material.GRAY_CONCRETE;
        Items item = Items.of(material)
                .name(messages.item("gui-loader-name", Texts.loader(plugin, loader)))
                .lore(messages.lore("gui-loader-lore", Texts.loader(plugin, loader)))
                .glint(loader.active());
        if (clickHint) {
            item.lore(Component.empty()).lore(messages.item("gui-loader-click"));
        }
        return item.build();
    }
}
