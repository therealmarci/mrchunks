package at.marcelr.mrchunks.api;

import at.marcelr.mradmin.api.AdminModule;
import at.marcelr.mradmin.api.HealthEntry;
import at.marcelr.mradmin.api.MRadminService;
import at.marcelr.mradmin.api.Report;
import at.marcelr.mradmin.api.WebAlert;
import at.marcelr.mradmin.api.WebModule;
import at.marcelr.mradmin.api.WebPushKind;
import at.marcelr.mradmin.api.WebTile;
import at.marcelr.mradmin.api.WebUser;
import at.marcelr.mrchunks.Loader;
import at.marcelr.mrchunks.LoaderManager;
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
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static at.marcelr.mrchunks.Messages.tag;

/**
 * MRchunks as a module of MRadmin (module API, see MRadmin's docs/MODULE-API.md): dashboard button with counts, the
 * player's loaders in the player menu, conspicuous loaders in the server health, tickets for lag locks and the
 * chunk loader pages of the web interface. <b>Only loaded while MRadmin runs</b> - everything else in MRchunks goes
 * through {@link MRadminLink}, which has no MRadmin types.
 */
final class MRadminModule implements AdminModule, WebModule {

    static final String ID = "mrchunks";
    private static final String PERM_GUI = "mrchunks.admin.gui";
    private static final int MAX_HEALTH = 21;
    /** Health id of the server-pause entry (loaders use their numeric id). */
    private static final String PAUSE_ID = "pause";
    /** Read-only requests of {@link WebData} (its actions go through {@link #action} / {@link #playerAction}). */
    private static final Set<String> DATA = Set.of("overview", "chunks", "loaders", "loader");
    /** Ticket category of lag locks and the web push kind about them. */
    private static final String LOCK_KIND = "chunkloader-lock";
    private static final String PUSH_LOCKED = "chunks-locked";

    private final MRChunks plugin;

    private MRadminModule(MRChunks plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------- entry points (MRadminLink)

    /** Registers the module with the running MRadmin. @return false if MRadmin offers no module API */
    static boolean register(MRChunks plugin) {
        MRadminService service = MRadminService.get();
        if (service == null) {
            return false;
        }
        service.register(new MRadminModule(plugin));
        return true;
    }

    /** A loader was locked because of lag: a ticket for its owner. */
    static void reportLock(MRChunks plugin, Loader loader, String details) {
        MRadminService service = MRadminService.get();
        if (service == null) {
            return;
        }
        Location location = loader.location();
        service.report(ID, new Report(String.valueOf(loader.id()), LOCK_KIND, loader.owner(),
                LoaderManager.name(loader.owner()),
                plugin.messages().server(() -> plugin.messages().plain("report-lock-subject", "name", Texts.name(plugin, loader))), details,
                location == null ? null : location.add(0.5, 0, 0.5), PUSH_LOCKED));
    }

    /** The lock was lifted: a note in the owner's ticket. */
    static void resolveLock(MRChunks plugin, Loader loader, String admin) {
        MRadminService service = MRadminService.get();
        if (service != null) {
            service.resolve(ID, String.valueOf(loader.id()),
                    plugin.messages().server(() -> plugin.messages().plain("report-lock-lifted", "admin", admin)));
        }
    }

    /** The world belongs to another module (e.g. MRpvp's arena world). */
    static boolean reservedWorld(org.bukkit.World world) {
        MRadminService service = MRadminService.get();
        String module = service == null ? null : service.worldOwner(world);
        return module != null && !module.equals(ID);
    }

    // -------------------------------------------------------------- module

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Plugin plugin() {
        return plugin;
    }

    @Override
    public String viewPermission() {
        return PERM_GUI;
    }

    @Override
    public Material icon() {
        return Material.EMERALD_BLOCK;
    }

    /** Loaders, active ones, loaded chunks, bans (and the server pause). */
    @Override
    public List<Component> summary(Player viewer) {
        return plugin.messages().as(viewer, () -> {
            var all = plugin.loaders().all();
            List<Component> lines = new ArrayList<>(plugin.messages().lore("mradmin-summary",
                    tag("loaders", all.size()),
                    tag("active", all.stream().filter(Loader::active).count()),
                    tag("chunks", plugin.loaders().loadedChunkCount()),
                    tag("bans", plugin.loaders().activeBans().size())));
            if (plugin.loaders().serverPaused()) {
                lines.addAll(plugin.messages().lore("mradmin-summary-paused", plugin.pause().tags()));
            }
            if (plugin.loaders().adminPause() != null) {
                lines.addAll(plugin.messages().lore("mradmin-summary-admin-paused", Texts.adminPause(plugin)));
            }
            return lines;
        });
    }

    /** The same lines as on the player's head in MRchunks' admin menu. */
    @Override
    public List<Component> playerSummary(Player viewer, UUID player) {
        return plugin.messages().as(viewer, () -> {
            ItemMeta meta = PlayerIcons.head(plugin, Bukkit.getOfflinePlayer(player), false).getItemMeta();
            List<Component> lore = meta == null ? null : meta.lore();
            return lore == null ? List.of() : lore;
        });
    }

    @Override
    public boolean openMenu(Player viewer, Runnable back) {
        if (!viewer.hasPermission(PERM_GUI)) {
            plugin.messages().send(viewer, "no-permission");
            return false;
        }
        plugin.messages().run(viewer, () -> new at.marcelr.mrchunks.gui.AdminMenu(plugin, viewer).open());
        return true;
    }

    @Override
    public boolean openPlayerMenu(Player viewer, UUID player, Runnable back) {
        if (!viewer.hasPermission(PERM_GUI)) {
            plugin.messages().send(viewer, "no-permission");
            return false;
        }
        plugin.messages().run(viewer, () -> new PlayerLoadersMenu(plugin, viewer, player, back).open());
        return true;
    }

    /** The server pause and locked or conspicuous loaders, worst first. */
    @Override
    public List<HealthEntry> health(Player viewer) {
        if (!viewer.hasPermission(PERM_GUI) || (!plugin.settings().lag().enabled() && !plugin.loaders().serverPaused())) {
            return List.of();
        }
        return plugin.messages().as(viewer, () -> {
            List<HealthEntry> entries = new ArrayList<>();
            if (plugin.loaders().serverPaused()) {
                entries.add(new HealthEntry(PAUSE_ID, pauseIcon()));
            }
            for (Loader loader : plugin.settings().lag().enabled() ? plugin.lag().conspicuous() : List.<Loader>of()) {
                if (entries.size() == MAX_HEALTH) {
                    break;
                }
                entries.add(new HealthEntry(String.valueOf(loader.id()), loaderIcon(loader)));
            }
            return entries;
        });
    }

    private ItemStack pauseIcon() {
        ItemStack item = new ItemStack(Material.COBWEB);
        item.editMeta(meta -> {
            meta.displayName(plugin.messages().item("mradmin-pause-name"));
            meta.lore(plugin.messages().lore("mradmin-pause-lore", plugin.pause().tags()));
        });
        return item;
    }

    private ItemStack loaderIcon(Loader loader) {
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

    /** The loader's menu (teleport, unlock, toggle ...) - also from the ticket of a lag lock. */
    @Override
    public boolean openHealth(Player viewer, String id, Runnable back) {
        if (!viewer.hasPermission(PERM_GUI)) {
            plugin.messages().send(viewer, "no-permission");
            return false;
        }
        if (id.equals(PAUSE_ID)) {
            plugin.messages().send(viewer, plugin.loaders().serverPaused() ? "pause-status" : "pause-over", plugin.pause().tags());
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
        int loaderId = loader.id();
        plugin.messages().run(viewer, () -> new LoaderMenu(plugin, viewer, loaderId, back).open());
        return true;
    }

    // ----------------------------------------------------------------- web

    @Override
    public WebModule web() {
        return this;
    }

    private static final Map<String, String> PERMISSIONS = Map.of(
            "manage", "mrchunks.admin.manage", "limit", "mrchunks.admin.limit", "ban", "mrchunks.admin.ban",
            "toggle", "mrchunks.admin.toggle", "link", "mrchunks.admin.link", "resize", "mrchunks.admin.resize",
            "toggleConspicuous", "mrchunks.admin.toggle.conspicuous", "pause", "mrchunks.admin.pause",
            "monitoring", "mrchunks.admin.monitoring");
    private static final Map<String, String> ACTIONS = Map.of(
            "toggle", "toggle", "unlock", "manage", "delete", "manage", "dummy", "manage", "resize", "resize",
            "conspicuousToggle", "toggleConspicuous", "conspicuousDummy", "toggleConspicuous",
            "pauseAll", "pause", "monitoring", "monitoring");
    private static final Map<String, String> PLAYER_ACTIONS = Map.of(
            "limit", "limit", "ban", "ban", "unban", "ban", "deleteAll", "manage", "unlink", "link", "link", "link");

    @Override
    public Map<String, String> permissions() {
        return PERMISSIONS;
    }

    @Override
    public @Nullable Object data(String request, Map<String, String> args) {
        return DATA.contains(request) ? WebData.handle(plugin, request, args) : null;
    }

    @Override
    public Map<String, String> actions() {
        return ACTIONS;
    }

    @Override
    public @Nullable String action(String action, Map<String, Object> args, WebUser user) {
        Map<String, Object> all = new HashMap<>(args);
        all.put("action", action);
        all.put("admin", user.name());
        Object error = WebData.handle(plugin, "action", all);
        return error == null ? null : String.valueOf(error);
    }

    @Override
    public @Nullable Object player(UUID player) {
        return WebData.handle(plugin, "player", Map.of("uuid", player.toString()));
    }

    @Override
    public Map<String, String> playerActions() {
        return PLAYER_ACTIONS;
    }

    @Override
    public @Nullable String playerAction(UUID player, String action, Map<String, Object> args, WebUser user) {
        Map<String, Object> all = new HashMap<>(args);
        all.put("action", action);
        all.put("uuid", player.toString());
        all.put("admin", user.name());
        Object error = WebData.handle(plugin, "playerAction", all);
        return error == null ? null : String.valueOf(error);
    }

    // ------------------------------------------- since MRadmin API 3 (1.2.0)
    // An older MRadmin never calls these - its interfaces do not have them.

    /** Dummy players are no real players for MRadmin (lists, counts, maintenance kicks). */
    @Override
    public boolean isNpc(Player player) {
        return at.marcelr.mrchunks.dummy.Dummies.isDummy(player);
    }

    /** Chunks the loaders keep loaded, for MRadmin's server health. */
    @Override
    public int loadedChunks() {
        return plugin.loaders().loadedChunkCount();
    }

    /** Start page tile: active loaders, server pause, locks, conspicuous loaders, loaded chunks. */
    @Override
    public @Nullable WebTile tile() {
        var messages = plugin.messages();
        var all = plugin.loaders().all();
        long active = all.stream().filter(Loader::active).count();
        int locked = plugin.loaders().lagLocks().size();
        long warn = all.stream().filter(l -> LoaderStatus.of(plugin, l) == LoaderStatus.WARN).count();
        boolean paused = plugin.loaders().serverPaused();
        boolean adminPaused = plugin.loaders().adminPause() != null;
        int shed = plugin.loaders().shedIds().size();
        return messages.server(() -> {
            List<WebTile.Fact> facts = new ArrayList<>();
            if (paused) {
                facts.add(new WebTile.Fact(messages.plain("web-tile-paused"), "", "info"));
            }
            if (adminPaused) {
                facts.add(new WebTile.Fact(messages.plain("web-tile-admin-paused"), "", "info"));
            }
            if (shed > 0) {
                facts.add(new WebTile.Fact(messages.plain("web-tile-shed"), String.valueOf(shed), "warn"));
            }
            facts.add(new WebTile.Fact(messages.plain("web-tile-locked"), String.valueOf(locked), locked > 0 ? "bad" : ""));
            facts.add(new WebTile.Fact(messages.plain("web-tile-warn"), String.valueOf(warn), warn > 0 ? "warn" : ""));
            facts.add(new WebTile.Fact(messages.plain("web-tile-chunks"), String.valueOf(plugin.loaders().loadedChunkCount()), ""));
            return new WebTile(messages.plain("web-tile-label"), String.valueOf(active),
                    messages.plain("web-tile-unit", "loaders", String.valueOf(all.size())),
                    paused || adminPaused ? "info" : locked > 0 ? "bad" : warn > 0 || shed > 0 ? "warn" : "good", facts,
                    messages.plain("web-tile-headline", "active", String.valueOf(active)), "/chunks",
                    messages.plain("web-tile-link"));
        });
    }

    @Override
    public List<WebPushKind> pushKinds() {
        var messages = plugin.messages();
        return List.of(messages.server(() -> new WebPushKind(PUSH_LOCKED, messages.plain("web-push-locked"),
                messages.plain("web-push-locked-hint"), "🔒", true)));
    }

    @Override
    public Map<String, String> reportCategories() {
        var messages = plugin.messages();
        return Map.of(LOCK_KIND, messages.server(() -> messages.plain("report-lock-category")));
    }

    // ------------------------------------------- since MRadmin API 4 (1.3.0)

    /** The player's own page (/web): permissions MRadmin checks beforehand, also while the player is offline. */
    @Override
    public Set<String> selfPermissions() {
        return SelfPage.PERMISSIONS;
    }

    @Override
    public List<at.marcelr.mradmin.api.SelfArea> selfAreas(at.marcelr.mradmin.api.SelfViewer viewer) {
        return SelfPage.areas(plugin, viewer);
    }

    @Override
    public @Nullable String selfAction(at.marcelr.mradmin.api.SelfViewer viewer, String area, String action,
                                       Map<String, String> args) {
        return SelfPage.AREA.equals(area) ? SelfPage.action(plugin, viewer, action, args) : "?";
    }

    /** Server pause, staged shutdown (warning, loaders disabled by lag), locked loaders, loaders over the warning limit. */
    @Override
    public List<WebAlert> alerts() {
        var messages = plugin.messages();
        List<WebAlert> alerts = new ArrayList<>();
        LoaderManager.Switch adminPause = plugin.loaders().adminPause();
        if (adminPause != null) {
            alerts.add(new WebAlert("info", messages.plain("web-alert-admin-paused", "admin", adminPause.by(),
                    "time", Texts.date(messages, adminPause.since())), "/chunks"));
        }
        LoaderManager.Switch monitoringOff = plugin.loaders().monitoringOff();
        if (monitoringOff != null && plugin.settings().shed().enabled()) {
            alerts.add(new WebAlert("info", messages.plain("web-alert-monitoring-off", "admin", monitoringOff.by(),
                    "time", Texts.date(messages, monitoringOff.since())), "/chunks"));
        }
        if (plugin.loaders().serverPaused()) {
            alerts.add(new WebAlert("bad", messages.plain("web-alert-paused", "tps",
                    String.format(java.util.Locale.ROOT, "%.1f", at.marcelr.mrchunks.lag.ServerPause.tps())), "/chunks"));
        }
        int shed = plugin.loaders().shedIds().size();
        if (shed > 0) {
            alerts.add(new WebAlert("warn", messages.plain("web-alert-shed", "count", String.valueOf(shed),
                    "mspt", String.format(java.util.Locale.ROOT, "%.1f", plugin.shedder().mspt())), "/chunks"));
        } else if (plugin.shedder().warning()) {
            alerts.add(new WebAlert("warn", messages.plain("web-alert-mspt",
                    "mspt", String.format(java.util.Locale.ROOT, "%.1f", plugin.shedder().mspt())), "/chunks"));
        }
        int locked = plugin.loaders().lagLocks().size();
        if (locked > 0) {
            alerts.add(new WebAlert("warn", messages.plain("web-alert-locked", "count", String.valueOf(locked)), "/chunks"));
        }
        long warn = plugin.loaders().all().stream().filter(l -> LoaderStatus.of(plugin, l) == LoaderStatus.WARN).count();
        if (warn > 0) {
            alerts.add(new WebAlert("info", messages.plain("web-alert-warn", "count", String.valueOf(warn)), "/chunks"));
        }
        return alerts;
    }

    @Override
    public Map<UUID, List<String>> flags() {
        Map<UUID, List<String>> flags = new LinkedHashMap<>();
        if (WebData.handle(plugin, "flags", Map.of()) instanceof Map<?, ?> answer) {
            answer.forEach((uuid, keys) -> {
                if (keys instanceof List<?> list) {
                    flags.put(UUID.fromString(String.valueOf(uuid)), list.stream().map(String::valueOf).toList());
                }
            });
        }
        return flags;
    }
}
