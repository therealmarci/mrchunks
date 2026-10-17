package at.marcelr.mrchunks;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * The chunk loader item. It is marked in its persistent data, so a normal block of the same material
 * (e.g. a crafted emerald block) is never treated as a chunk loader.
 */
public final class LoaderItem {

    private final MRChunks plugin;
    private final NamespacedKey key;
    private final MiniMessage mini = MiniMessage.miniMessage();

    public LoaderItem(MRChunks plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "chunkloader");
    }

    public ItemStack create(int amount) {
        Settings.ItemSettings item = plugin.settings().item();
        Settings.LoaderSettings loaders = plugin.settings().loaders();
        TagResolver tags = TagResolver.resolver(
                Placeholder.unparsed("size", String.valueOf(loaders.size())),
                Placeholder.unparsed("chunks", String.valueOf(loaders.chunkCount())));

        ItemStack stack = new ItemStack(item.material(), Math.max(1, amount));
        stack.editMeta(meta -> {
            meta.displayName(noItalic(mini.deserialize(item.name(), tags)));
            List<Component> lore = new ArrayList<>();
            for (String line : item.lore()) {
                lore.add(noItalic(mini.deserialize(line, tags)));
            }
            meta.lore(lore);
            if (item.glint()) {
                meta.setEnchantmentGlintOverride(true);
            }
            meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        });
        return stack;
    }

    public boolean is(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.hasItemMeta()
                && stack.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    /** Chunk loader items in the player's inventory (main inventory, hotbar, off hand). */
    public int count(Player player) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (is(stack)) {
                count += stack.getAmount();
            }
        }
        return count;
    }

    /** Removes up to {@code max} loader items. @return how many were removed */
    public int remove(Player player, int max) {
        PlayerInventory inventory = player.getInventory();
        int removed = 0;
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length && removed < max; slot++) {
            ItemStack stack = contents[slot];
            if (!is(stack)) {
                continue;
            }
            int take = Math.min(stack.getAmount(), max - removed);
            removed += take;
            if (take == stack.getAmount()) {
                inventory.setItem(slot, null);
            } else {
                stack.setAmount(stack.getAmount() - take);
            }
        }
        return removed;
    }

    private static Component noItalic(Component component) {
        return component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
