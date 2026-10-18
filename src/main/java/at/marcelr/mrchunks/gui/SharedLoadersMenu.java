package at.marcelr.mrchunks.gui;

import at.marcelr.mrchunks.Loader;
import at.marcelr.mrchunks.MRChunks;
import at.marcelr.mrchunks.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/** Loaders of other players that the viewer was invited to; a click opens the loader's menu (switch on/off). */
public final class SharedLoadersMenu extends Menu {

    private static final int PER_PAGE = 45;
    private final Menu parent;
    private int page;

    public SharedLoadersMenu(MRChunks plugin, Player viewer, Menu parent) {
        super(plugin, viewer, 6, plugin.messages().get("gui-title-shared"));
        this.parent = parent;
    }

    @Override
    protected void draw() {
        Messages messages = plugin.messages();
        List<Loader> shared = plugin.adminActions().sharedWith(viewer);
        int pages = Pages.count(shared.size(), PER_PAGE);
        page = Math.min(page, pages - 1);
        List<Loader> visible = Pages.slice(shared, page, PER_PAGE);
        for (int i = 0; i < visible.size(); i++) {
            Loader loader = visible.get(i);
            set(i, LoaderIcons.loader(plugin, loader, true), click -> {
                clickSound();
                new LoaderMenu(plugin, viewer, loader.id(), this).open();
            });
        }
        if (shared.isEmpty()) {
            set(22, Items.of(Material.STRUCTURE_VOID).name(messages.item("gui-shared-empty")).build());
        }
        Pages.navigation(this, messages, page, pages, p -> page = p);
        if (parent != null) {
            set(48, Items.of(Material.ARROW).name(messages.item("gui-back")).build(), click -> {
                clickSound();
                parent.open();
            });
        }
        set(49, Items.of(Material.BARRIER).name(messages.item("gui-close")).build(), click -> viewer.closeInventory());
        fillRow(5);
    }
}
