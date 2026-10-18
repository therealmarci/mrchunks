package at.marcelr.mrchunks.gui;

import at.marcelr.mrchunks.MRChunks;
import at.marcelr.mrchunks.util.TitleWidth;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Base class of all MRchunks menus. The menu is its own {@link InventoryHolder}, which is how
 * {@link MenuListener} recognises it. {@link #render()} rebuilds all items and is called again
 * periodically, so the menu shows live data without being reopened.
 */
public abstract class Menu implements InventoryHolder {

    protected final MRChunks plugin;
    protected final Player viewer;
    private final Inventory inventory;
    private final Map<Integer, Consumer<ClickType>> actions = new HashMap<>();

    protected Menu(MRChunks plugin, Player viewer, int rows, Component title) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.inventory = Bukkit.createInventory(this, rows * 9, fit(title));
    }

    /**
     * Keeps the title inside the window: Minecraft does not cut titles off, a long one (e.g. with a long loader or
     * player name) runs out of the box. Too long titles are shortened with "…" in the colour of their first part.
     */
    static Component fit(Component title) {
        String plain = PlainTextComponentSerializer.plainText().serialize(title);
        if (TitleWidth.width(plain) <= TitleWidth.MAX) {
            return title;
        }
        return Component.text(TitleWidth.fit(plain, TitleWidth.MAX), title.style());
    }

    /** Fills the inventory. Implementations call {@link #set} for every slot they use. */
    protected abstract void draw();

    public final void render() {
        actions.clear();
        inventory.clear();
        draw();
    }

    public final void open() {
        render();
        viewer.openInventory(inventory);
    }

    protected void set(int slot, ItemStack item) {
        inventory.setItem(slot, item);
    }

    protected void set(int slot, ItemStack item, Consumer<ClickType> action) {
        inventory.setItem(slot, item);
        actions.put(slot, action);
    }

    /** Fills all empty slots of a row with the decorative pane. */
    protected void fillRow(int row) {
        for (int slot = row * 9; slot < row * 9 + 9; slot++) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, Items.filler());
            }
        }
    }

    final void click(int slot, ClickType type) {
        Consumer<ClickType> action = actions.get(slot);
        if (action != null) {
            action.accept(type);
        }
    }

    /** Runs {@code action} if the viewer has {@code permission}, otherwise tells them and plays the deny sound. */
    protected boolean allowed(String permission) {
        if (viewer.hasPermission(permission)) {
            return true;
        }
        plugin.messages().send(viewer, "no-permission");
        sound(Sound.ENTITY_VILLAGER_NO);
        return false;
    }

    protected void sound(Sound sound) {
        if (plugin.settings().gui().sounds()) {
            viewer.playSound(viewer.getLocation(), sound, 0.6f, 1.2f);
        }
    }

    protected void clickSound() {
        sound(Sound.UI_BUTTON_CLICK);
    }

    protected void successSound() {
        sound(Sound.ENTITY_EXPERIENCE_ORB_PICKUP);
    }

    @Override
    public @NonNull Inventory getInventory() {
        return inventory;
    }
}
