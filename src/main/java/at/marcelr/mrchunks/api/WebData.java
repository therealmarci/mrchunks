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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Plain data for MRadmin's web interface (see {@link MRadminHook#web}): only strings, numbers, booleans, lists and
 * maps, texts already in MRchunks' language. Server thread only (MRadmin calls it there).
 * <pre>
 *  overview                         counts, server pause, lag protection on/off
 *  loaders                          all loaders with status
 *  loader  {id}                     one loader with every measured value, lock, members
 *  action  {id, action, admin}      toggle | unlock | delete  -> null or an error key
 * </pre>
 */
final class WebData {

    private WebData() {
    }

    static Object handle(MRChunks plugin, String request, Map<?, ?> args) {
        return switch (request) {
            case "overview" -> overview(plugin);
            case "loaders" -> loaders(plugin);
            case "loader" -> {
                Loader loader = loader(plugin, args);
                yield loader == null ? null : detail(plugin, loader);
            }
            case "action" -> action(plugin, args);
            default -> null;
        };
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
            default -> "unknown-action";
        };
    }
}
