package at.marcelr.mrchunks.gui;

import at.marcelr.mrchunks.Messages;
import org.bukkit.Material;

import java.util.List;
import java.util.function.IntConsumer;

import static at.marcelr.mrchunks.Messages.tag;

/** Previous/next arrows in the bottom row (slots 45 and 53) of a 6-row menu. */
final class Pages {

    private Pages() {
    }

    static void navigation(Menu menu, Messages messages, int page, int pages, IntConsumer setPage) {
        var lore = messages.lore("gui-page-lore", tag("page", page + 1), tag("pages", pages));
        if (page > 0) {
            menu.set(45, Items.of(Material.ARROW).name(messages.item("gui-prev")).lore(lore).build(), click -> {
                setPage.accept(page - 1);
                menu.clickSound();
                menu.render();
            });
        }
        if (page < pages - 1) {
            menu.set(53, Items.of(Material.ARROW).name(messages.item("gui-next")).lore(lore).build(), click -> {
                setPage.accept(page + 1);
                menu.clickSound();
                menu.render();
            });
        }
    }

    /** Number of pages for {@code size} entries with {@code perPage} per page (at least 1). */
    static int count(int size, int perPage) {
        return Math.max(1, (size + perPage - 1) / perPage);
    }

    static <T> List<T> slice(List<T> all, int page, int perPage) {
        int from = Math.min(all.size(), page * perPage);
        return all.subList(from, Math.min(all.size(), from + perPage));
    }
}
