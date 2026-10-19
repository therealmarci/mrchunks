package at.marcelr.mrchunks.api;

import at.marcelr.mrchunks.Loader;
import at.marcelr.mrchunks.LoaderStatus;
import at.marcelr.mrchunks.MRChunks;
import at.marcelr.mrchunks.Texts;
import at.marcelr.mrchunks.gui.LoaderIcons;
import at.marcelr.mrchunks.gui.LoaderMenu;
import at.marcelr.mrchunks.gui.PlayerIcons;
import at.marcelr.mrchunks.gui.PlayerLoadersMenu;
import at.marcelr.mrchunks.lag.LagLock;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static at.marcelr.mrchunks.Messages.tag;

/**
 * Connection point for MRadmin's admin compass. MRadmin finds this class at runtime (no dependency in either
 * direction) and calls these methods; MRchunks works the same without MRadmin. Keep the signatures stable.
 * <p>
 * Version 1: summary, playerSummary, openPlayerMenu. Version 2 adds activity and openActivity (conspicuous loaders
 * in MRadmin's server health). Version 3 adds {@link #EVENT} (MRadmin opens a ticket when a loader is locked; the team lifts the lock
 * through openActivity, the loader's menu). Version 4 adds {@link #web} (data and actions for the web
 * interface). MRadmin uses them only if they exist, so both plugins stay independent.
 */
public final class MRadminHook {

    public static final int API_VERSION = 4;
    /** Fired on every lag lock and unlock; MRadmin listens to it while both plugins run. */
    public static final Class<LoaderLockEvent> EVENT = LoaderLockEvent.class;
    private static final int MAX_ACTIVITY = 21;
    private static final String PERM_GUI = "mrchunks.admin.gui";
    /** Activity id of the server-pause entry (loaders use their numeric id). */
    private static final String PAUSE_ID = "pause";

    private MRadminHook() {
    }

    private static MRChunks plugin() {
        return JavaPlugin.getPlugin(MRChunks.class);
    }

    /** Lines for the MRchunks button in MRadmin's menu: loaders, active ones, loaded chunks, bans. */
    public static List<Component> summary(Player viewer) {
        MRChunks plugin = plugin();
        return plugin.messages().as(viewer, () -> {
            var all = plugin.loaders().all();
            List<Component> lines = plugin.messages().lore("mradmin-summary",
                    tag("loaders", all.size()),
                    tag("active", all.stream().filter(Loader::active).count()),
                    tag("chunks", plugin.loaders().loadedChunkCount()),
                    tag("bans", plugin.loaders().activeBans().size()));
            if (plugin.loaders().paused()) {
                lines = new ArrayList<>(lines);
                lines.addAll(plugin.messages().lore("mradmin-summary-paused", plugin.pause().tags()));
            }
            return lines;
        });
    }

    /** The same lines as on the player's head in the admin menu (loaders, limits, status). */
    public static List<Component> playerSummary(Player viewer, UUID target) {
        MRChunks plugin = plugin();
        return plugin.messages().as(viewer, () -> {
            ItemMeta meta = PlayerIcons.head(plugin, Bukkit.getOfflinePlayer(target), false).getItemMeta();
            List<Component> lore = meta == null ? null : meta.lore();
            return lore == null ? List.of() : lore;
        });
    }

    /**
     * Locked and conspicuous loaders (worst first) for MRadmin's server health, as ready-made icons in MRchunks'
     * language. Each entry: "icon" (ItemStack) and "id" (String, passed back to {@link #openActivity}).
     */
    public static List<Map<String, Object>> activity(Player viewer) {
        MRChunks plugin = plugin();
        if (!viewer.hasPermission(PERM_GUI) || (!plugin.settings().lag().enabled() && !plugin.loaders().paused())) {
            return List.of();
        }
        return plugin.messages().as(viewer, () -> {
            List<Map<String, Object>> entries = new ArrayList<>();
            if (plugin.loaders().paused()) {
                entries.add(Map.of("icon", pauseIcon(plugin), "id", PAUSE_ID));
            }
            for (Loader loader : plugin.settings().lag().enabled() ? plugin.lag().conspicuous() : List.<Loader>of()) {
                if (entries.size() == MAX_ACTIVITY) {
                    break;
                }
                entries.add(Map.of("icon", activityIcon(plugin, loader), "id", String.valueOf(loader.id())));
            }
            return entries;
        });
    }

    /** Server pause: all loaders hold no chunks until the TPS has recovered. */
    private static ItemStack pauseIcon(MRChunks plugin) {
        ItemStack item = new ItemStack(Material.COBWEB);
        item.editMeta(meta -> {
            meta.displayName(plugin.messages().item("mradmin-pause-name"));
            meta.lore(plugin.messages().lore("mradmin-pause-lore", plugin.pause().tags()));
        });
        return item;
    }

    private static ItemStack activityIcon(MRChunks plugin, Loader loader) {
        var messages = plugin.messages();
        LagLock lock = plugin.loaders().lagLock(loader);
        // Same materials as in MRchunks' own menus (see LoaderStatus).
        ItemStack item = new ItemStack(LoaderStatus.of(plugin, loader).material(plugin));
        item.editMeta(meta -> {
            meta.displayName(messages.item(lock != null ? "mradmin-activity-name-locked" : "mradmin-activity-name",
                    Texts.loader(plugin, loader)));
            List<Component> lore = new ArrayList<>(messages.lore("mradmin-activity-lore", Texts.loader(plugin, loader)));
            if (lock != null) {
                lore.addAll(messages.lore("gui-loader-locked", plugin.lag().tags(loader, lock)));
            }
            List<Component> lines = LoaderIcons.activity(plugin, loader);
            if (!lines.isEmpty()) {
                lore.add(Component.empty());
                lore.addAll(lines);
            }
            lore.add(Component.empty());
            lore.add(messages.item("mradmin-activity-click"));
            meta.lore(lore);
        });
        return item;
    }

    /** Opens the loader's menu (teleport, unlock, toggle ...); "back" returns to MRadmin. @return false if not allowed */
    public static boolean openActivity(Player viewer, String id, Runnable back) {
        MRChunks plugin = plugin();
        if (!viewer.hasPermission(PERM_GUI)) {
            plugin.messages().send(viewer, "no-permission");
            return false;
        }
        if (id.equals(PAUSE_ID)) {
            plugin.messages().send(viewer, plugin.loaders().paused() ? "pause-status" : "pause-over", plugin.pause().tags());
            return true;
        }
        Loader loader;
        try {
            loader = plugin.loaders().byId(Integer.parseInt(id));
        } catch (NumberFormatException e) {
            loader = null;
        }
        if (loader == null) {
            return false;
        }
        new LoaderMenu(plugin, viewer, loader.id(), back).open();
        return true;
    }

    /**
     * Data and actions for MRadmin's web interface (hook version 4): {@code overview}, {@code loaders},
     * {@code loader} (args: id), {@code action} (args: id, action = toggle | unlock | delete, admin = name).
     * Answers with plain values only (maps, lists, strings, numbers); an action answers null or an error key.
     * MRadmin checks the permissions (mrchunks.admin.gui to look, mrchunks.admin.manage to act) and calls this on the
     * server thread.
     */
    public static Object web(String request, Map<String, Object> args) {
        return WebData.handle(plugin(), request, args);
    }

    /** Opens the player's loader menu; "back" returns to MRadmin. @return false without permission */
    public static boolean openPlayerMenu(Player viewer, UUID target, Runnable back) {
        MRChunks plugin = plugin();
        if (!viewer.hasPermission(PERM_GUI)) {
            plugin.messages().send(viewer, "no-permission");
            return false;
        }
        new PlayerLoadersMenu(plugin, viewer, target, back).open();
        return true;
    }
}
