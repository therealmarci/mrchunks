package at.marcelr.mrchunks.gui;

import at.marcelr.mrchunks.Loader;
import at.marcelr.mrchunks.LoaderManager;
import at.marcelr.mrchunks.MRChunks;
import at.marcelr.mrchunks.Messages;
import at.marcelr.mrchunks.Texts;
import at.marcelr.mrchunks.UpdateChecker;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static at.marcelr.mrchunks.Messages.tag;

/**
 * Admin overview: statistics on top, one head per player (with loaders, online, or banned - by filter),
 * navigation at the bottom. A click on a head opens {@link PlayerLoadersMenu}.
 */
public final class AdminMenu extends Menu {

    private static final int PER_PAGE = 36;
    private static final int FIRST_SLOT = 9;

    enum Filter {
        ALL("gui-filter-all"), ONLINE("gui-filter-online"), BANNED("gui-filter-banned");

        final String key;

        Filter(String key) {
            this.key = key;
        }

        Filter next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private int page;
    private Filter filter = Filter.ALL;

    public AdminMenu(MRChunks plugin, Player viewer) {
        super(plugin, viewer, 6, plugin.messages().get("gui-title-admin"));
    }

    @Override
    protected void draw() {
        Messages messages = plugin.messages();
        LoaderManager loaders = plugin.loaders();

        // ---- top row
        long active = loaders.all().stream().filter(Loader::active).count();
        set(1, Items.of(Material.EMERALD).name(messages.item("gui-stats-name"))
                .lore(messages.lore("gui-stats-lore",
                        tag("loaders", loaders.all().size()),
                        tag("active", active),
                        tag("chunks", loaders.loadedChunkCount()),
                        tag("owners", loaders.all().stream().map(Loader::owner).distinct().count()),
                        tag("banned", loaders.activeBans().size())))
                .build());
        set(4, infoItem());
        set(7, Items.of(Material.HOPPER)
                .name(messages.item("gui-filter-name", tag("filter", messages.plain(filter.key))))
                .lore(messages.lore("gui-filter-lore")).build(), click -> {
            filter = filter.next();
            page = 0;
            clickSound();
            render();
        });
        fillRow(0);

        // ---- heads
        List<OfflinePlayer> players = players();
        int pages = Pages.count(players.size(), PER_PAGE);
        page = Math.min(page, pages - 1);
        List<OfflinePlayer> shown = Pages.slice(players, page, PER_PAGE);
        for (int i = 0; i < shown.size(); i++) {
            OfflinePlayer target = shown.get(i);
            set(FIRST_SLOT + i, PlayerIcons.head(plugin, target, true), click -> {
                clickSound();
                new PlayerLoadersMenu(plugin, viewer, target.getUniqueId(), this).open();
            });
        }
        if (shown.isEmpty()) {
            set(22, Items.of(Material.STRUCTURE_VOID).name(messages.item("gui-empty")).build());
        }

        // ---- bottom row
        Pages.navigation(this, messages, page, pages, p -> page = p);
        set(49, Items.of(Material.BARRIER).name(messages.item("gui-close")).build(), click -> viewer.closeInventory());
        Items reload = Items.of(Material.COMPARATOR).name(messages.item("gui-reload-name"));
        if (!viewer.hasPermission("mrchunks.admin.reload")) {
            reload.lore(messages.item("gui-no-permission"));
        }
        set(51, reload.build(), click -> {
            if (allowed("mrchunks.admin.reload")) {
                plugin.adminActions().reload(viewer);
                successSound();
                render();
            }
        });
        fillRow(5);
    }

    private org.bukkit.inventory.ItemStack infoItem() {
        Messages messages = plugin.messages();
        UpdateChecker updates = plugin.updateChecker();
        Component update = switch (updates.state()) {
            case UP_TO_DATE -> messages.get("gui-update-ok");
            case OUTDATED -> messages.get("gui-update-outdated", tag("latest", updates.latestVersion()));
            default -> messages.get("gui-update-unknown");
        };
        Component web = messages.get(plugin.webMap().running() ? "gui-web-on" : "gui-web-off");
        return Items.of(Material.KNOWLEDGE_BOOK)
                .name(messages.item("gui-info-name", tag("version", updates.currentVersion())))
                .lore(messages.lore("gui-info-lore", tag("update", update), tag("web", web)))
                .build();
    }

    /** Players for the current filter: online first, then by number of loaders, then by name. */
    private List<OfflinePlayer> players() {
        LoaderManager loaders = plugin.loaders();
        Set<UUID> ids = new LinkedHashSet<>();
        switch (filter) {
            case ALL -> ids.addAll(loaders.knownPlayers());
            case ONLINE -> Bukkit.getOnlinePlayers().forEach(p -> ids.add(p.getUniqueId()));
            case BANNED -> ids.addAll(loaders.activeBans().keySet());
        }
        List<OfflinePlayer> result = new ArrayList<>();
        ids.forEach(id -> result.add(Bukkit.getOfflinePlayer(id)));
        result.sort(Comparator.<OfflinePlayer>comparingInt(p -> p.isOnline() ? 0 : 1)
                .thenComparing(p -> -loaders.count(p.getUniqueId()))
                .thenComparing(p -> String.valueOf(p.getName()).toLowerCase(Locale.ROOT)));
        return result;
    }
}
