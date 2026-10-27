package at.marcelr.mrchunks.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Small fluent helper to build menu items. */
final class Items {

    private final ItemStack item;
    private Component name;
    private final List<Component> lore = new ArrayList<>();
    private boolean glint;

    private Items(ItemStack item) {
        this.item = item;
    }

    static Items of(Material material) {
        return new Items(new ItemStack(material));
    }

    static Items head(Player owner) {
        return new Items(Heads.of(owner));
    }

    /** Head of a player who may be offline (skin looked up once and cached, see {@link Heads}). */
    static Items head(OfflinePlayer owner) {
        return new Items(Heads.of(owner));
    }

    static ItemStack filler() {
        return of(Material.GRAY_STAINED_GLASS_PANE).name(Component.text(" ")).build();
    }

    Items name(Component name) {
        this.name = name;
        return this;
    }

    Items lore(List<Component> lines) {
        lore.addAll(lines);
        return this;
    }

    Items lore(Component line) {
        lore.add(line);
        return this;
    }

    Items glint(boolean glint) {
        this.glint = glint;
        return this;
    }

    ItemStack build() {
        ItemMeta meta = item.getItemMeta();
        if (name != null) {
            meta.displayName(name);
        }
        if (!lore.isEmpty()) {
            meta.lore(lore);
        }
        if (glint) {
            meta.setEnchantmentGlintOverride(true);
        }
        item.setItemMeta(meta);
        return item;
    }
}
