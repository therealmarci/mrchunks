package at.marcelr.mrchunks.gui;

import at.marcelr.mrchunks.Loader;
import at.marcelr.mrchunks.MRChunks;
import at.marcelr.mrchunks.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

import static at.marcelr.mrchunks.Messages.tag;

/**
 * Choice of the block a loader stands as in the world: item.material and item.block-choices. Menus and the item keep
 * showing item.material. Layout: the blocks from the top left, bottom row [back] . . . [close] . . . .
 * Block names are translated by the client, so they are always in the player's language.
 */
public final class BlockMenu extends Menu {

    private final int loaderId;
    private final Menu parent;

    public BlockMenu(MRChunks plugin, Player viewer, int loaderId, Menu parent) {
        super(plugin, viewer, rows(plugin), plugin.messages().get("gui-title-block"));
        this.loaderId = loaderId;
        this.parent = parent;
    }

    private static List<Material> choices(MRChunks plugin) {
        List<Material> all = new ArrayList<>();
        all.add(plugin.settings().item().material());
        all.addAll(plugin.settings().item().blockChoices());
        return all;
    }

    private static int rows(MRChunks plugin) {
        return Math.min(6, (choices(plugin).size() + 8) / 9 + 1);
    }

    @Override
    protected void draw() {
        Loader loader = plugin.loaders().byId(loaderId);
        if (loader == null || !plugin.adminActions().canChangeBlock(viewer, loader)) {
            // Deleted or choice switched off meanwhile: back on the next tick, not while drawing.
            Bukkit.getScheduler().runTask(plugin, () -> plugin.messages().run(viewer, parent::open));
            return;
        }
        Messages messages = plugin.messages();
        Material current = plugin.loaders().blockOf(loader);
        List<Material> choices = choices(plugin);
        int bottom = getInventory().getSize() - 9;
        for (int i = 0; i < choices.size() && i < bottom; i++) {
            Material material = choices.get(i);
            boolean selected = material == current;
            TagResolver block = tag("block", Component.translatable(material));
            set(i, Items.of(material).name(messages.item("gui-block-choice-name", block))
                    .lore(messages.lore(selected ? "gui-block-selected-lore" : "gui-block-choice-lore", block))
                    .glint(selected).build(), click -> {
                if (selected) {
                    clickSound();
                } else {
                    plugin.adminActions().changeBlock(viewer, loader, material);
                    successSound();
                }
                parent.open();
            });
        }
        set(bottom, Items.of(Material.ARROW).name(messages.item("gui-back")).build(), click -> {
            clickSound();
            parent.open();
        });
        set(bottom + 4, Items.of(Material.BARRIER).name(messages.item("gui-close")).build(),
                click -> viewer.closeInventory());
        fillRow(bottom / 9);
    }
}
