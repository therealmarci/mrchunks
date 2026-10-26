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
 *  action  {id, action, admin}      toggle | unlock | delete | dummy  -> null or an error key
 *  player  {uuid}                   one player: loaders, limits (with source), ban, invites, loaders shared with them,
 *                                   linked accounts (main, secondAccounts), dummies
 *  flags                            uuid -> marks for MRadmin's player list (chunks-locked, chunks-warn, chunks-banned)
 *  playerAction {uuid, action, admin, kind?, value?, reason?}   limit (kind place|active|dummy) | ban | unban
 *                                   | deleteAll | unlink  -> null or an error key
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
        m.put("dummiesEnabled", plugin.dummies().enabled());
        m.put("dummies", loaders.dummyCount(main));
        m.put("dummyLimit", limit(loaders, target, LoaderManager.LimitKind.DUMMY));
        m.put("defaultDummyLimit", plugin.settings().dummies().defaultLimit());
        m.put("active", loaders.activeCount(main));
        m.put("limit", limit(loaders, target, LoaderManager.LimitKind.PLACE));
        m.put("activeLimit", limit(loaders, target, LoaderManager.LimitKind.ACTIVE));
        m.put("defaultLimit", plugin.settings().loaders().defaultLimit());
        m.put("defaultActiveLimit", plugin.settings().loaders().defaultActiveLimit());
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
     * Player actions: limit {kind place|active, value number or "" = back to default/permission}, ban {value = duration
     * in ms from gui.ban-durations, reason}, unban, deleteAll. @return null or an error key
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
                    case "place" -> LoaderManager.LimitKind.PLACE;
                    case "active" -> LoaderManager.LimitKind.ACTIVE;
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
                if (limit != null && (limit < 0 || limit > 1000)) {
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
        result.put("chunks", loaders.loadedChunkCount());
        result.put("owners", all.stream().map(Loader::owner).distinct().count());
        result.put("size", plugin.settings().loaders().size());
        result.put("lagProtection", plugin.settings().lag().enabled());
        result.put("paused", loaders.paused());
        result.put("dummiesEnabled", plugin.dummies().enabled());
        result.put("dummies", plugin.dummies().count());
        result.put("links", loaders.links().size());
        if (loaders.paused()) {
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
        m.put("dummy", loader.dummy());
        m.put("dummyPresent", plugin.dummies().dummyOf(loader) != null);
        m.put("status", LoaderStatus.of(plugin, loader).name().toLowerCase(Locale.ROOT));
        m.put("created", loader.created());
        LagLock lock = plugin.loaders().lagLock(loader);
        if (lock != null) {
            m.put("lock", Map.of("metric", messages.plain("lag-metric-" + lock.metric().key()), "value", lock.value(),
                    "limit", lock.limit(), "since", lock.since()));
        }
        Activity a = plugin.settings().lag().enabled() ? plugin.lag().activity(loader) : null;
        if (a != null) {
            Metric worst = a.worst();
            m.put("worst", Map.of("metric", messages.plain("lag-metric-" + worst.key()), "value", a.value(worst),
                    "warn", plugin.settings().lag().limits().warn(worst)));
        }
        return m;
    }

    private static Map<String, Object> detail(MRChunks plugin, Loader loader) {
        Map<String, Object> m = summary(plugin, loader);
        m.put("dummiesEnabled", plugin.dummies().enabled());
        Messages messages = plugin.messages();
        Activity a = plugin.settings().lag().enabled() ? plugin.lag().activity(loader) : null;
        if (a != null) {
            LagLimits limits = plugin.settings().lag().limits();
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
            default -> "unknown-action";
        };
    }
}
