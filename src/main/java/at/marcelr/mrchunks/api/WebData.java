package at.marcelr.mrchunks.api;

import at.marcelr.mrchunks.Loader;
import at.marcelr.mrchunks.LoaderManager;
import at.marcelr.mrchunks.LoaderStatus;
import at.marcelr.mrchunks.MRChunks;
import at.marcelr.mrchunks.Messages;
import at.marcelr.mrchunks.Texts;
import at.marcelr.mrchunks.lag.Activity;
import at.marcelr.mrchunks.lag.LagLimits;
import at.marcelr.mrchunks.lag.LagLock;
import at.marcelr.mrchunks.lag.Metric;
import at.marcelr.mrchunks.lag.ServerPause;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Plain data for MRadmin's web interface (see {@link MRadminModule}): only strings, numbers, booleans, lists and
 * maps, texts already in MRchunks' language. Server thread only (MRadmin calls it there).
 * <pre>
 *  overview                         counts, server pause, lag protection on/off
 *  chunks                           number of chunks the loaders keep loaded (cheap, MRadmin asks every second)
 *  loaders                          all loaders with status
 *  loader  {id}                     one loader with every measured value, lock, members
 *  action  {id, action, admin}      toggle | unlock | delete | dummy | resize {direction, delta}  -> null or an error key
 *  player  {uuid}                   one player: loaders, budget and dummy limit (with source), ban, invites, loaders shared with them,
 *                                   linked accounts (main, secondAccounts), dummies
 *  flags                            uuid -> marks for MRadmin's player list (chunks-locked, chunks-warn, chunks-banned)
 *  playerAction {uuid, action, admin, kind?, value?, reason?}   limit (kind budget|dummy) | ban | unban
 *                                   | deleteAll | unlink | link {other, role}  -> null or an error key
 * </pre>
 */
final class WebData {

    private WebData() {
    }

    static Object handle(MRChunks plugin, String request, Map<?, ?> args) {
        return switch (request) {
            case "overview" -> overview(plugin);
            case "chunks" -> plugin.loaders().loadedChunkCount();
            case "loaders" -> loaders(plugin);
            case "loader" -> {
                Loader loader = loader(plugin, args);
                yield loader == null ? null : detail(plugin, loader);
            }
            case "action" -> action(plugin, args);
            case "player" -> {
                OfflinePlayer target = target(args.get("uuid"));
                yield target == null ? null : player(plugin, target);
            }
            case "flags" -> flags(plugin);
            case "playerAction" -> playerAction(plugin, args);
            default -> null;
        };
    }

    // ---------------------------------------------------------------- players

    /** A player's part of MRadmin's profile page: their loaders, limits, ban, invites and loaders shared with them. */
    private static Map<String, Object> player(MRChunks plugin, OfflinePlayer target) {
        LoaderManager loaders = plugin.loaders();
        UUID id = target.getUniqueId();
        Map<String, Object> m = new LinkedHashMap<>();
        List<Map<String, Object>> own = new ArrayList<>();
        // A linked second account shares the loaders of its main account.
        UUID main = loaders.main(id);
        for (Loader loader : loaders.byOwner(main)) {
            own.add(summary(plugin, loader));
        }
        m.put("loaders", own);
        if (!main.equals(id)) {
            m.put("main", Map.of("uuid", main.toString(), "name", LoaderManager.name(main)));
        }
        List<Map<String, Object>> seconds = new ArrayList<>();
        for (UUID second : loaders.secondaries(main)) {
            seconds.add(Map.of("uuid", second.toString(), "name", LoaderManager.name(second)));
        }
        m.put("secondAccounts", seconds);
        m.put("linksEnabled", plugin.settings().links().enabled());
        m.put("maxLinks", plugin.settings().links().maxAccounts());
        m.put("dummiesEnabled", plugin.dummies().enabled());
        m.put("dummies", loaders.dummyCount(main));
        m.put("dummyLimit", limit(loaders, target, LoaderManager.LimitKind.DUMMY));
        m.put("defaultDummyLimit", plugin.settings().dummies().defaultLimit());
        m.put("active", loaders.activeCount(main));
        m.put("maxLoaders", loaders.maxLoaders());
        m.put("budget", limit(loaders, target, LoaderManager.LimitKind.BUDGET));
        m.put("budgetUsed", loaders.usedBudget(main));
        m.put("defaultBudget", plugin.settings().loaders().defaultBudget());
        // For MRadmin before the chunk budget (place and active limit): the fixed maximum, active without a limit.
        m.put("limit", Map.of("value", loaders.maxLoaders(), "source", "default"));
        m.put("activeLimit", Map.of("value", -1, "source", "unlimited"));
        m.put("defaultLimit", loaders.maxLoaders());
        // Permissions (limits by permission, exempt) are only known while the player is online.
        Player online = target.getPlayer();
        m.put("online", online != null);
        m.put("exempt", online != null && online.hasPermission(at.marcelr.mrchunks.AdminActions.PERM_EXEMPT));
        LoaderManager.Ban ban = loaders.groupBan(id);
        if (ban != null) {
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("permanent", ban.until() == Long.MAX_VALUE);
            b.put("until", ban.until() == Long.MAX_VALUE ? 0 : ban.until());
            b.put("reason", ban.reason());
            b.put("by", ban.by());
            m.put("ban", b);
        }
        List<Map<String, Object>> members = new ArrayList<>();
        addMembers(members, loaders.members(id, null), "all");
        m.put("members", members);
        List<Map<String, Object>> shared = new ArrayList<>();
        for (Loader loader : loaders.sharedWith(id)) {
            shared.add(summary(plugin, loader));
        }
        m.put("shared", shared);
        m.put("banDurations", plugin.settings().gui().banDurations());
        return m;
    }

    /** value -1 = unlimited; source default | permission | custom | unlimited; custom = the admin's value or null. */
    private static Map<String, Object> limit(LoaderManager loaders, OfflinePlayer target, LoaderManager.LimitKind kind) {
        LoaderManager.Limit limit = loaders.limit(target, kind);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("value", limit.value() == Integer.MAX_VALUE ? -1 : limit.value());
        m.put("source", limit.source().name().toLowerCase(Locale.ROOT));
        m.put("custom", loaders.limitOverride(target.getUniqueId(), kind));
        return m;
    }

    /** Marks for MRadmin's player list: owners of locked or conspicuous loaders, players banned from loaders. */
    private static Map<String, List<String>> flags(MRChunks plugin) {
        Map<String, List<String>> flags = new LinkedHashMap<>();
        for (Loader loader : plugin.loaders().all()) {
            LoaderStatus status = LoaderStatus.of(plugin, loader);
            String flag = status == LoaderStatus.LOCKED ? "chunks-locked" : status == LoaderStatus.WARN ? "chunks-warn" : null;
            if (flag != null) {
                List<String> f = flags.computeIfAbsent(loader.owner().toString(), k -> new ArrayList<>());
                if (!f.contains(flag)) {
                    f.add(flag);
                }
            }
        }
        for (UUID id : plugin.loaders().activeBans().keySet()) {
            flags.computeIfAbsent(id.toString(), k -> new ArrayList<>()).add("chunks-banned");
        }
        return flags;
    }

    /**
     * Player actions: limit {kind budget|dummy, value number or "" = back to default/permission}, ban {value = duration
     * in ms from gui.ban-durations, reason}, unban, deleteAll, unlink, link {other = player name, role second|main}. @return null or an error key
     */
    private static String playerAction(MRChunks plugin, Map<?, ?> args) {
        OfflinePlayer target = target(args.get("uuid"));
        if (target == null) {
            return "player-unknown";
        }
        String admin = String.valueOf(args.get("admin"));
        String value = args.get("value") == null ? "" : String.valueOf(args.get("value")).trim();
        var actions = plugin.adminActions();
        return switch (String.valueOf(args.get("action"))) {
            case "limit" -> {
                LoaderManager.LimitKind kind = switch (String.valueOf(args.get("kind"))) {
                    case "budget" -> LoaderManager.LimitKind.BUDGET;
                    case "dummy" -> LoaderManager.LimitKind.DUMMY;
                    default -> null;
                };
                if (kind == null) {
                    yield "unknown-action";
                }
                Integer limit;
                try {
                    limit = value.isEmpty() ? null : Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    yield "invalid-limit";
                }
                if (limit != null && (limit < 0 || limit > (kind == LoaderManager.LimitKind.BUDGET ? 100_000 : 1000))) {
                    yield "invalid-limit";
                }
                yield actions.webSetLimit(admin, target, kind, limit);
            }
            case "ban" -> {
                long duration;
                try {
                    duration = Long.parseLong(value);
                } catch (NumberFormatException e) {
                    yield "invalid-duration";
                }
                if (!plugin.settings().gui().banDurations().contains(duration)) {
                    yield "invalid-duration";
                }
                String reason = args.get("reason") instanceof String r ? r.strip() : "";
                yield actions.webBan(admin, target, duration, reason.length() > 200 ? reason.substring(0, 200) : reason);
            }
            case "unban" -> actions.webUnban(admin, target);
            case "deleteAll" -> actions.webDeleteAll(admin, target);
            case "unlink" -> actions.webUnlink(admin, target);
            case "link" -> {
                OfflinePlayer other = named(args.get("other"));
                if (other == null) {
                    yield "player-unknown";
                }
                // role "second": the other account becomes a second account of the target, "main": its main account.
                yield switch (String.valueOf(args.get("role"))) {
                    case "second" -> actions.webLink(admin, target, other);
                    case "main" -> actions.webLink(admin, other, target);
                    default -> "unknown-action";
                };
            }
            default -> "unknown-action";
        };
    }

    /** A player who has been on the server (or is online); null for anything else. */
    private static OfflinePlayer target(Object uuid) {
        try {
            OfflinePlayer player = Bukkit.getOfflinePlayer(UUID.fromString(String.valueOf(uuid)));
            return player.isOnline() || player.hasPlayedBefore() ? player : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** A player by name who has been on the server; null for anything else. */
    private static OfflinePlayer named(Object name) {
        String raw = name == null ? "" : String.valueOf(name).strip();
        if (!raw.matches("[A-Za-z0-9_.]{1,16}")) {
            return null;
        }
        OfflinePlayer player = Bukkit.getOfflinePlayerIfCached(raw);
        return player != null && (player.isOnline() || player.hasPlayedBefore()) ? player : null;
    }

    private static Loader loader(MRChunks plugin, Map<?, ?> args) {
        try {
            return plugin.loaders().byId(Integer.parseInt(String.valueOf(args.get("id"))));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Map<String, Object> overview(MRChunks plugin) {
        LoaderManager loaders = plugin.loaders();
        var all = loaders.all();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("loaders", all.size());
        result.put("active", all.stream().filter(Loader::active).count());
        result.put("locked", loaders.lagLocks().size());
        result.put("warn", all.stream().filter(l -> LoaderStatus.of(plugin, l) == LoaderStatus.WARN).count());
        result.put("shed", loaders.shedIds().size());
        result.put("mspt", Math.round(plugin.shedder().mspt() * 10) / 10.0);
        result.put("msptWarning", plugin.shedder().warning());
        result.put("chunks", loaders.loadedChunkCount());
        result.put("owners", all.stream().map(Loader::owner).distinct().count());
        result.put("size", plugin.settings().loaders().defaultSize());
        result.put("defaultBudget", plugin.settings().loaders().defaultBudget());
        result.put("maxSize", plugin.settings().loaders().maxReach() * 2 + 1);
        result.put("lagProtection", plugin.settings().lag().enabled());
        result.put("paused", loaders.serverPaused());
        LoaderManager.Switch adminPause = loaders.adminPause();
        if (adminPause != null) {
            result.put("adminPause", Map.of("by", adminPause.by(), "since", adminPause.since()));
        }
        var shedSettings = plugin.settings().shed();
        LoaderManager.Switch monitoringOff = loaders.monitoringOff();
        Map<String, Object> monitoring = new LinkedHashMap<>();
        monitoring.put("configured", shedSettings.enabled());
        monitoring.put("active", plugin.shedder().active());
        monitoring.put("warn", shedSettings.warnMspt());
        monitoring.put("shutdown", shedSettings.shutdownMspt());
        monitoring.put("hardCap", shedSettings.hardCapMspt());
        if (monitoringOff != null) {
            monitoring.put("offBy", monitoringOff.by());
            monitoring.put("offSince", monitoringOff.since());
        }
        result.put("monitoring", monitoring);
        result.put("dummiesEnabled", plugin.dummies().enabled());
        result.put("dummies", plugin.dummies().count());
        result.put("links", loaders.links().size());
        if (loaders.serverPaused()) {
            result.put("pausedSince", plugin.pause().pausedSince());
            result.put("tps", Math.round(ServerPause.tps() * 10) / 10.0);
            result.put("resumeTps", plugin.settings().pause().resumeTps());
        }
        return result;
    }

    private static List<Map<String, Object>> loaders(MRChunks plugin) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Loader loader : plugin.loaders().all()) {
            list.add(summary(plugin, loader));
        }
        return list;
    }

    private static Map<String, Object> summary(MRChunks plugin, Loader loader) {
        Messages messages = plugin.messages();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(loader.id()));
        m.put("number", loader.number());
        m.put("name", Texts.name(plugin, loader));
        m.put("owner", loader.owner().toString());
        m.put("ownerName", LoaderManager.name(loader.owner()));
        m.put("world", loader.worldName());
        m.put("x", loader.x());
        m.put("y", loader.y());
        m.put("z", loader.z());
        m.put("active", loader.active());
        m.put("width", loader.width());
        m.put("length", loader.length());
        m.put("chunks", loader.area());
        Map<String, Object> reach = new LinkedHashMap<>();
        for (Loader.Direction direction : Loader.Direction.values()) {
            reach.put(direction.name().toLowerCase(Locale.ROOT), loader.reach(direction));
        }
        m.put("reach", reach);
        m.put("dummy", loader.dummy());
        m.put("dummyPresent", plugin.dummies().dummyOf(loader) != null);
        m.put("conspicuous", plugin.adminActions().conspicuous(loader));
        m.put("status", LoaderStatus.of(plugin, loader).name().toLowerCase(Locale.ROOT));
        m.put("created", loader.created());
        LagLock lock = plugin.loaders().lagLock(loader);
        if (lock != null) {
            m.put("lock", Map.of("metric", messages.plain("lag-metric-" + lock.metric().key()), "value", lock.value(),
                    "limit", lock.limit(), "since", lock.since()));
        }
        if (plugin.loaders().shed(loader)) {
            long since = plugin.loaders().shedSince(loader);
            m.put("shed", Map.of("since", since, "back", plugin.shedder().expectedBack(loader)));
        }
        Activity a = plugin.settings().lag().enabled() ? plugin.lag().activity(loader) : null;
        if (a != null) {
            Metric worst = a.worst();
            m.put("worst", Map.of("metric", messages.plain("lag-metric-" + worst.key()), "value", a.value(worst),
                    "warn", plugin.settings().lag().limitsFor(loader.area()).warn(worst)));
            // Load = share of the lock limit of the worst metric; the staged shutdown takes the highest first.
            m.put("load", Math.round(a.lockRatio() * 1000) / 1000.0);
            m.put("loadWarn", a.warnRatio() >= 1);
        }
        return m;
    }

    /** Metrics against the limits of the loader's own area. */
    private static Map<String, Object> detail(MRChunks plugin, Loader loader) {
        Map<String, Object> m = summary(plugin, loader);
        m.put("dummiesEnabled", plugin.dummies().enabled());
        m.put("dummyFits", at.marcelr.mrchunks.dummy.Dummies.fits(loader));
        m.put("maxReach", plugin.settings().loaders().maxReach());
        OfflinePlayer owner = Bukkit.getOfflinePlayer(loader.owner());
        LoaderManager.Limit budget = plugin.loaders().budget(owner);
        m.put("budget", budget.value() == Integer.MAX_VALUE ? -1 : budget.value());
        m.put("budgetUsed", plugin.loaders().usedBudget(loader.owner()));
        Messages messages = plugin.messages();
        Activity a = plugin.settings().lag().enabled() ? plugin.lag().activity(loader) : null;
        if (a != null) {
            LagLimits limits = plugin.settings().lag().limitsFor(loader.area());
            List<Map<String, Object>> metrics = new ArrayList<>();
            for (Metric metric : Metric.values()) {
                if (limits.warn(metric) > 0 || limits.lock(metric) > 0) {
                    metrics.add(Map.of("key", metric.key(), "label", messages.plain("lag-metric-" + metric.key()),
                            "value", a.value(metric), "warn", limits.warn(metric), "lock", limits.lock(metric),
                            "rate", metric.rate()));
                }
            }
            m.put("metrics", metrics);
            m.put("measured", a.time());
            m.put("loadedChunks", a.loadedChunks());
            m.put("totalChunks", a.totalChunks());
        }
        List<Map<String, Object>> members = new ArrayList<>();
        addMembers(members, plugin.loaders().members(loader.owner(), loader), "loader");
        addMembers(members, plugin.loaders().members(loader.owner(), null), "all");
        m.put("members", members);
        return m;
    }

    private static void addMembers(List<Map<String, Object>> into, Map<UUID, Long> members, String scope) {
        members.forEach((uuid, until) -> into.add(Map.of("uuid", uuid.toString(), "name", LoaderManager.name(uuid),
                "until", until == Long.MAX_VALUE ? 0L : until, "scope", scope)));
    }

    private static String action(MRChunks plugin, Map<?, ?> args) {
        // Server-wide switches (no loader): pause all loaders, staged shutdown on/off.
        boolean on = Boolean.parseBoolean(String.valueOf(args.get("on")));
        switch (String.valueOf(args.get("action"))) {
            case "pauseAll" -> {
                return plugin.adminActions().webPauseAll(String.valueOf(args.get("admin")), on);
            }
            case "monitoring" -> {
                return plugin.adminActions().webMonitoring(String.valueOf(args.get("admin")), on);
            }
            default -> {
                // the loader actions below
            }
        }
        Loader loader = loader(plugin, args);
        if (loader == null) {
            return "not-found";
        }
        String admin = String.valueOf(args.get("admin"));
        return switch (String.valueOf(args.get("action"))) {
            case "toggle" -> plugin.adminActions().webToggle(admin, loader);
            case "unlock" -> plugin.adminActions().webUnlock(admin, loader);
            case "delete" -> plugin.adminActions().webDelete(admin, loader);
            case "dummy" -> plugin.adminActions().webToggleDummy(admin, loader);
            case "conspicuousToggle" -> plugin.adminActions().webConspicuousToggle(admin, loader);
            case "conspicuousDummy" -> plugin.adminActions().webConspicuousDummy(admin, loader);
            case "resize" -> {
                // direction north|south|west|east or all, delta +1 / -1
                String raw = String.valueOf(args.get("direction")).toUpperCase(Locale.ROOT);
                Loader.Direction direction = null;
                if (!raw.equals("ALL")) {
                    try {
                        direction = Loader.Direction.valueOf(raw);
                    } catch (IllegalArgumentException e) {
                        yield "unknown-action";
                    }
                }
                int delta = String.valueOf(args.get("delta")).startsWith("-") ? -1 : 1;
                yield plugin.adminActions().webResize(admin, loader, direction, delta);
            }
            default -> "unknown-action";
        };
    }
}
