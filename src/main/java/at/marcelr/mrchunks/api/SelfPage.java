package at.marcelr.mrchunks.api;

import at.marcelr.mradmin.api.MRadminService;
import at.marcelr.mradmin.api.SelfArea;
import at.marcelr.mradmin.api.SelfBlock;
import at.marcelr.mradmin.api.SelfViewer;
import at.marcelr.mrchunks.Loader;
import at.marcelr.mrchunks.LoaderManager;
import at.marcelr.mrchunks.LoaderStatus;
import at.marcelr.mrchunks.MRChunks;
import at.marcelr.mrchunks.Messages;
import at.marcelr.mrchunks.Texts;
import at.marcelr.mrchunks.dummy.Dummies;
import at.marcelr.mrchunks.lag.Activity;
import at.marcelr.mrchunks.lag.LagLimits;
import at.marcelr.mrchunks.lag.LagLock;
import at.marcelr.mrchunks.lag.LagMonitor;
import at.marcelr.mrchunks.lag.Metric;
import at.marcelr.mrchunks.util.DurationUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * MRchunks on the player's own page in MRadmin's web interface (/web): every loader as a card (status, size, how busy
 * it is) that opens with its limits, a live map around it and its buttons - switching on and off, renaming, dummies,
 * deleting; the invited players and a form to invite more; loaders shared with the player; the live map (squaremap).
 * Everything they may do in game except changing the size. Every action checks ownership, permissions and the same
 * rules as in game again. Needs MRadmin with API 5 (cards, bars, forms); with an older MRadmin the area is left out.
 * Only loaded while MRadmin runs (like {@link MRadminModule}).
 */
final class SelfPage {

    static final String AREA = "loaders";
    private static final String USE = "mrchunks.use";
    private static final String INVITE = "mrchunks.invite";
    private static final String MAP = "mrchunks.map";
    private static final String EXEMPT = "mrchunks.exempt";
    static final Set<String> PERMISSIONS = Set.of(USE, INVITE, MAP, EXEMPT);
    /** Cards, Meters, People, Form and Embed came with MRadmin's API 5. */
    private static final int NEEDED_API = 5;

    private SelfPage() {
    }

    private static boolean allowed(SelfViewer viewer) {
        return viewer.has(USE) && !viewer.has(EXEMPT);
    }

    // ---------------------------------------------------------------- page

    static List<SelfArea> areas(MRChunks plugin, SelfViewer viewer) {
        MRadminService service = MRadminService.get();
        if (!allowed(viewer) || service == null || service.apiVersion() < NEEDED_API) {
            return List.of();
        }
        return plugin.messages().in(viewer.lang(), () -> List.of(area(plugin, viewer)));
    }

    private static SelfArea area(MRChunks plugin, SelfViewer viewer) {
        Messages m = plugin.messages();
        LoaderManager loaders = plugin.loaders();
        UUID owner = loaders.main(viewer.player());
        OfflinePlayer player = Bukkit.getOfflinePlayer(viewer.player());
        List<Loader> own = loaders.byOwner(owner);
        long active = own.stream().filter(Loader::active).count();
        LoaderManager.Limit budget = loaders.budget(player);
        long free = loaders.freeBudget(player);
        LoaderManager.Ban ban = loaders.groupBan(viewer.player());
        String mapUrl = viewer.has(MAP) ? plugin.maps().mapUrl() : "";

        // Head of the page and tile on the start page: active loaders big, budget and placed loaders as bars
        // ("a / b"); a ban, lag locks, loaders near the lag limit and an exceeded budget as warning lines (MRadmin also
        // shows them in the slideshow).
        List<SelfBlock.Stat> stats = new ArrayList<>();
        stats.add(new SelfBlock.Stat(m.plain("self-active"), String.valueOf(active), active > 0 ? "good" : ""));
        stats.add(new SelfBlock.Stat(m.plain("self-budget"), m.plain("self-budget-value", "used", loaders.usedBudget(owner),
                "budget", Texts.limit(budget)), free <= 0 && budget.value() != Integer.MAX_VALUE ? "warn" : ""));
        stats.add(new SelfBlock.Stat(m.plain("self-loaders"), own.size() + " / " + loaders.maxLoaders(), ""));
        if (plugin.dummies().enabled()) {
            stats.add(new SelfBlock.Stat(m.plain("self-dummies"), loaders.dummyCount(owner) + " / "
                    + Texts.limit(loaders.limit(player, LoaderManager.LimitKind.DUMMY)), ""));
        }
        List<SelfBlock> summary = new ArrayList<>();
        summary.add(new SelfBlock.Stats(stats));
        if (ban != null) {
            summary.add(new SelfBlock.Text(m.plain("self-banned", "duration", Texts.banDuration(plugin, ban),
                    "reason", ban.reason()), "bad"));
        }
        long locked = own.stream().filter(loaders::lagLocked).count();
        if (locked > 0) {
            summary.add(new SelfBlock.Text(m.plain("self-notice-locked", "count", locked), "bad"));
        }
        long near = own.stream().filter(l -> l.active() && !loaders.lagLocked(l) && plugin.lag().conspicuous(l)).count();
        if (near > 0) {
            summary.add(new SelfBlock.Text(m.plain("self-notice-near", "count", near), "warn"));
        }
        if (free < 0 && budget.value() != Integer.MAX_VALUE) {
            summary.add(new SelfBlock.Text(m.plain("self-notice-budget", "used", loaders.usedBudget(owner),
                    "budget", Texts.limit(budget)), "warn"));
        }

        List<SelfBlock> blocks = new ArrayList<>();
        List<SelfBlock.Card> cards = new ArrayList<>();
        for (Loader loader : own) {
            cards.add(card(plugin, viewer, loader, ban == null, mapUrl));
        }
        blocks.add(new SelfBlock.Cards(m.plain("self-table"), cards, m.plain("self-empty")));

        // Order (user wish, mradmin#14): own loaders, loaders shared with the player, invite settings for all
        // loaders (invite on top, list below), map. Invites for one loader are in its opened card.
        List<Loader> shared = loaders.sharedWith(viewer.player());
        if (!shared.isEmpty()) {
            List<SelfBlock.Card> sharedCards = new ArrayList<>();
            for (Loader loader : shared) {
                sharedCards.add(sharedCard(plugin, loader, ban == null, mapUrl));
            }
            blocks.add(new SelfBlock.Cards(m.plain("self-shared"), sharedCards, ""));
        }
        if (plugin.settings().invites().enabled() && viewer.has(INVITE) && ban == null) {
            blocks.add(invites(plugin, owner, null));
        }
        if (!mapUrl.isEmpty()) {
            blocks.add(new SelfBlock.Embed(m.plain("self-map"), mapUrl, 520, m.plain("self-map-open")));
        }
        return new SelfArea(AREA, m.plain("self-title"), "🧱", summary, blocks);
    }

    /** One own loader: status, size, how busy; opened: limits, live map around it, all buttons. */
    private static SelfBlock.Card card(MRChunks plugin, SelfViewer viewer, Loader loader, boolean actions, String mapUrl) {
        Messages m = plugin.messages();
        LoaderStatus status = LoaderStatus.of(plugin, loader);
        String id = String.valueOf(loader.id());
        SelfBlock.Button primary = null;
        List<SelfBlock.Button> buttons = new ArrayList<>();
        if (actions) {
            if (status != LoaderStatus.LOCKED) {
                primary = new SelfBlock.Button(m.plain(loader.active() ? "self-off" : "self-on"), "toggle",
                        Map.of("id", id), loader.active() ? "" : "good", null, null);
            }
            buttons.add(new SelfBlock.Button(m.plain("self-rename"), "rename", Map.of("id", id), "", null,
                    new SelfBlock.Input(m.plain("self-rename-label"), loader.name() == null ? "" : loader.name(),
                            plugin.settings().gui().maxNameLength())));
            if (plugin.dummies().enabled()) {
                buttons.add(new SelfBlock.Button(m.plain(loader.dummy() ? "self-dummy-off" : "self-dummy-on"), "dummy",
                        Map.of("id", id), "", null, null));
            }
            buttons.add(new SelfBlock.Button(m.plain("self-delete"), "delete", Map.of("id", id), "bad",
                    m.plain("self-delete-confirm", "name", Texts.name(plugin, loader)), null));
        }
        List<SelfBlock> details = new ArrayList<>();
        LagLock lock = plugin.loaders().lagLock(loader);
        if (lock != null) {
            details.add(new SelfBlock.Text(m.plain("self-locked-detail", "metric", m.plain("lag-metric-" + lock.metric().key()),
                    "value", LagMonitor.format(lock.value()), "limit", LagMonitor.format(lock.limit()),
                    "since", Texts.date(m, lock.since())), "bad"));
        } else if (status == LoaderStatus.SHED) {
            details.add(new SelfBlock.Text(m.plain("self-shed-detail", "since",
                    Texts.date(m, plugin.loaders().shedSince(loader)), "back", Texts.date(m,
                            plugin.shedder().expectedBack(loader))), "warn"));
        }
        details.add(limits(plugin, loader));
        if (actions && plugin.settings().invites().enabled() && viewer.has(INVITE)) {
            details.add(invites(plugin, loader.owner(), loader));
        }
        if (!mapUrl.isEmpty()) {
            details.add(new SelfBlock.Embed(m.plain("self-card-map"), mapAt(mapUrl, loader), 320, m.plain("self-map-open")));
        }
        return new SelfBlock.Card(id, Texts.name(plugin, loader), place(plugin, loader), state(plugin, loader, status),
                tone(status), cardStats(plugin, loader), load(plugin, loader), primary, buttons, details);
    }

    /** A loader of someone else the player was invited to: only on/off. */
    private static SelfBlock.Card sharedCard(MRChunks plugin, Loader loader, boolean actions, String mapUrl) {
        Messages m = plugin.messages();
        LoaderStatus status = LoaderStatus.of(plugin, loader);
        SelfBlock.Button primary = !actions || status == LoaderStatus.LOCKED ? null
                : new SelfBlock.Button(m.plain(loader.active() ? "self-off" : "self-on"), "shared",
                Map.of("id", String.valueOf(loader.id())), loader.active() ? "" : "good", null, null);
        List<SelfBlock> details = new ArrayList<>();
        details.add(limits(plugin, loader));
        if (!mapUrl.isEmpty()) {
            details.add(new SelfBlock.Embed(m.plain("self-card-map"), mapAt(mapUrl, loader), 320, m.plain("self-map-open")));
        }
        return new SelfBlock.Card("shared-" + loader.id(), Texts.name(plugin, loader),
                m.plain("self-shared-sub", "owner", LoaderManager.name(loader.owner()), "place", place(plugin, loader)),
                state(plugin, loader, status), tone(status), cardStats(plugin, loader), load(plugin, loader), primary,
                List.of(), details);
    }

    private static String place(MRChunks plugin, Loader loader) {
        return plugin.messages().plain("self-card-place", "world", loader.worldName(), "x", loader.x(), "z", loader.z());
    }

    /** Short time for a card: "14:32" today, otherwise "31.10. 14:32". */
    private static String clock(long millis) {
        java.time.ZonedDateTime at = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault());
        boolean today = at.toLocalDate().equals(java.time.LocalDate.now());
        return at.format(java.time.format.DateTimeFormatter.ofPattern(today ? "HH:mm" : "dd.MM. HH:mm"));
    }

    private static List<SelfBlock.Stat> cardStats(MRChunks plugin, Loader loader) {
        Messages m = plugin.messages();
        List<SelfBlock.Stat> stats = new ArrayList<>();
        stats.add(new SelfBlock.Stat(m.plain("self-card-size"), loader.width() + "×" + loader.length(), ""));
        stats.add(new SelfBlock.Stat(m.plain("self-card-chunks"), String.valueOf(loader.area()), ""));
        if (plugin.loaders().shed(loader)) {
            stats.add(new SelfBlock.Stat(m.plain("self-card-back"), clock(plugin.shedder().expectedBack(loader)), "warn"));
        }
        if (loader.dummy() && plugin.dummies().enabled()) {
            boolean present = plugin.dummies().dummyOf(loader) != null;
            stats.add(new SelfBlock.Stat(m.plain("self-card-dummy"), m.plain(present ? "self-dummy-present" : "self-dummy-waiting"),
                    present ? "good" : ""));
        }
        return stats;
    }

    /** On the card: the busiest value as a share of its lock limit (100 % = locked), the warning marked on the bar. */
    private static List<SelfBlock.Meter> load(MRChunks plugin, Loader loader) {
        Activity a = plugin.settings().lag().enabled() ? plugin.lag().activity(loader) : null;
        if (a == null || !loader.active()) {
            return List.of();
        }
        LagLimits limits = plugin.settings().lag().limitsFor(loader.area());
        Metric worst = a.worst();
        double value = a.value(worst);
        double warn = limits.warn(worst);
        double lock = limits.lock(worst);
        if (warn <= 0 && lock <= 0) {
            return List.of();
        }
        // One scale for text and bar: share of the lock limit (100 % = locked), the line marks the warning.
        double full = lock > 0 ? lock : warn;
        double share = value / full;
        return List.of(new SelfBlock.Meter(plugin.messages().plain("self-load"),
                plugin.messages().plain("self-load-value", "percent", Math.round(share * 100)),
                share, lock > 0 && warn > 0 ? warn / lock : -1, "", meterTone(value, warn, lock)));
    }

    /** Opened card: every measured value against its warning and lock limit, like the team sees it in MRadmin. */
    private static SelfBlock limits(MRChunks plugin, Loader loader) {
        Messages m = plugin.messages();
        Activity a = plugin.settings().lag().enabled() ? plugin.lag().activity(loader) : null;
        if (!plugin.settings().lag().enabled()) {
            return new SelfBlock.Text(m.plain("self-limits-off"), "info");
        }
        if (a == null) {
            return new SelfBlock.Text(m.plain("self-limits-none"), "info");
        }
        LagLimits limits = plugin.settings().lag().limitsFor(loader.area());
        List<SelfBlock.Meter> meters = new ArrayList<>();
        for (Metric metric : Metric.values()) {
            double warn = limits.warn(metric);
            double lock = limits.lock(metric);
            if (warn <= 0 && lock <= 0) {
                continue;
            }
            double value = a.value(metric);
            double full = lock > 0 ? lock : warn;
            // "12.4 / 40": the value against the limit the bar runs up to.
            meters.add(new SelfBlock.Meter(m.plain("lag-metric-" + metric.key()), LagMonitor.format(value) + " / " + LagMonitor.format(full),
                    value / full, lock > 0 && warn > 0 ? warn / lock : -1,
                    m.plain("self-limits-hint", "warn", LagMonitor.format(warn), "lock", LagMonitor.format(lock)),
                    meterTone(value, warn, lock)));
        }
        String text = m.plain("self-limits-measured", "ago", DurationUtil.format(System.currentTimeMillis() - a.time()),
                "loaded", a.loadedChunks(), "total", a.totalChunks());
        return new SelfBlock.Meters(m.plain("self-limits"), text, meters);
    }

    private static String meterTone(double value, double warn, double lock) {
        return lock > 0 && value >= lock ? "bad" : warn > 0 && value >= warn ? "warn" : "good";
    }

    /** squaremap centred on the loader (?world=...&zoom=...&x=...&z=...). */
    private static String mapAt(String mapUrl, Loader loader) {
        World world = loader.world();
        String id = world == null ? loader.worldName() : world.getKey().asString().replace(':', '_');
        String base = mapUrl.contains("?") ? mapUrl + "&" : mapUrl.endsWith("/") ? mapUrl + "?" : mapUrl + "/?";
        return base + "world=" + id + "&zoom=4&x=" + loader.x() + "&z=" + loader.z();
    }

    /**
     * Invites as one tile: "invite a player" on top, the invited players with "remove" below. {@code loader} null =
     * for all loaders (under "Your chunk loaders"), otherwise for this one loader (in its opened card).
     */
    private static SelfBlock.Group invites(MRChunks plugin, UUID owner, Loader loader) {
        Messages m = plugin.messages();
        List<SelfBlock.Person> people = new ArrayList<>();
        String id = loader == null ? "all" : String.valueOf(loader.id());
        plugin.loaders().members(owner, loader).forEach((member, until) -> people.add(person(plugin, member, until, id)));
        SelfBlock.People list = new SelfBlock.People(m.plain("self-members-list", "count", people.size()), "", people,
                m.plain(loader == null ? "self-members-empty" : "self-members-loader-empty"));
        return new SelfBlock.Group(m.plain(loader == null ? "self-members" : "self-members-loader"),
                m.plain(loader == null ? "self-members-text" : "self-members-loader-text"),
                List.of(inviteForm(plugin, loader), list));
    }

    private static SelfBlock.Person person(MRChunks plugin, UUID member, long until, String id) {
        Messages m = plugin.messages();
        String time = until == Long.MAX_VALUE ? m.plain("self-permanent") : m.plain("self-until", "date", Texts.date(m, until));
        boolean online = Bukkit.getPlayer(member) != null;
        return new SelfBlock.Person(member, LoaderManager.name(member), time,
                online ? m.plain("self-online") : "", online ? "good" : "",
                List.of(new SelfBlock.Button(m.plain("self-uninvite"), "uninvite",
                        Map.of("id", id, "player", member.toString()), "bad", null, null)));
    }

    /** Invite: player and duration (invites.durations); {@code loader} null = for all loaders, otherwise for this one. */
    private static SelfBlock.Form inviteForm(MRChunks plugin, Loader loader) {
        Messages m = plugin.messages();
        List<SelfBlock.Option> durations = new ArrayList<>();
        for (long duration : plugin.settings().invites().durations()) {
            durations.add(new SelfBlock.Option(String.valueOf(duration), Texts.duration(plugin, duration)));
        }
        return new SelfBlock.Form(m.plain("self-invite-title"), m.plain("self-invite-text"), List.of(
                new SelfBlock.Field("player", m.plain("self-invite-player"), "text", "", List.of(), 16),
                new SelfBlock.Field("duration", m.plain("self-invite-duration"), "select", durations.getFirst().value(),
                        durations, 0)),
                m.plain("self-invite"), "invite", Map.of("scope", loader == null ? "all" : String.valueOf(loader.id())));
    }

    private static String state(MRChunks plugin, Loader loader, LoaderStatus status) {
        Messages m = plugin.messages();
        return switch (status) {
            case LOCKED -> m.plain("self-state-locked");
            case INACTIVE -> m.plain("self-state-off");
            case SHED -> m.plain("self-state-shed");
            case PAUSED -> m.plain(plugin.loaders().adminPause() != null ? "self-state-admin-paused" : "self-state-paused");
            case WARN -> m.plain("self-state-warn");
            case OK -> m.plain("self-state-on");
        };
    }

    private static String tone(LoaderStatus status) {
        return switch (status) {
            case LOCKED -> "bad";
            case WARN, PAUSED, SHED -> "warn";
            case INACTIVE -> "";
            case OK -> "good";
        };
    }

    // -------------------------------------------------------------- actions

    /** @return null if done, otherwise the reason in the viewer's language */
    static String action(MRChunks plugin, SelfViewer viewer, String action, Map<String, String> args) {
        if (!allowed(viewer)) {
            return plugin.messages().in(viewer.lang(), () -> plugin.messages().plain("self-err-no-permission"));
        }
        return plugin.messages().in(viewer.lang(), () -> run(plugin, viewer, action, args));
    }

    private static String run(MRChunks plugin, SelfViewer viewer, String action, Map<String, String> args) {
        Messages m = plugin.messages();
        LoaderManager loaders = plugin.loaders();
        UUID owner = loaders.main(viewer.player());
        if (loaders.groupBan(viewer.player()) != null) {
            return m.plain("self-err-banned");
        }
        String by = viewer.name() + " (web)";
        if (action.equals("invite")) {
            // From the form: player, scope ("all" or a loader id), duration (one of invites.durations).
            String scope = args.getOrDefault("scope", "all");
            Loader target = "all".equals(scope) ? null : loader(loaders, scope);
            if (!"all".equals(scope) && (target == null || !target.owner().equals(owner))) {
                return m.plain("self-err-not-found");
            }
            return invite(plugin, viewer, owner, target, args.get("player"), parseDuration(plugin, args.get("duration")));
        }
        if (action.equals("uninvite")) {
            return uninvite(plugin, viewer, owner, args);
        }
        Loader loader = loader(loaders, args.get("id"));
        if (action.equals("shared")) {
            if (loader == null || !loaders.isMember(viewer.player(), loader) || loader.owner().equals(owner)) {
                return m.plain("self-err-not-found");
            }
            return toggle(plugin, loader, by);
        }
        if (loader == null || !loader.owner().equals(owner)) {
            return m.plain("self-err-not-found");
        }
        return switch (action) {
            case "toggle" -> toggle(plugin, loader, by);
            case "rename" -> rename(plugin, loader, args.get("value"), by);
            case "dummy" -> dummy(plugin, viewer, loader, by);
            case "delete" -> {
                loaders.delete(loader);
                plugin.getLogger().info(by + " deleted " + Texts.consoleName(plugin, loader) + ".");
                plugin.discord().log("loader-deleted", plugin.adminActions().discordTags(loader));
                yield null;
            }
            default -> m.plain("self-err-not-found");
        };
    }

    private static Loader loader(LoaderManager loaders, String id) {
        try {
            return id == null ? null : loaders.byId(Integer.parseInt(id));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String toggle(MRChunks plugin, Loader loader, String by) {
        Messages m = plugin.messages();
        LoaderManager loaders = plugin.loaders();
        boolean on = !loader.active();
        if (on && loaders.lagLocked(loader)) {
            return m.plain("self-err-locked");
        }
        OfflinePlayer owner = Bukkit.getOfflinePlayer(loader.owner());
        if (on && !loaders.fitsBudget(owner, loader.area())) {
            return m.plain("self-err-budget", "chunks", loader.area(), "free", Math.max(0, loaders.freeBudget(owner)));
        }
        loaders.setActive(loader, on);
        String state = m.server(() -> Texts.state(plugin, on));
        plugin.discord().log("loader-toggled", plugin.adminActions().discordTags(loader, "admin", by, "state", state));
        return null;
    }

    private static String rename(MRChunks plugin, Loader loader, String raw, String by) {
        String clean = raw == null ? "" : raw.replaceAll("\\p{Cntrl}", "").replaceAll("\\s+", " ").strip();
        int max = plugin.settings().gui().maxNameLength();
        if (clean.codePointCount(0, clean.length()) > max) {
            clean = clean.substring(0, clean.offsetByCodePoints(0, max)).strip();
        }
        String old = Texts.name(plugin, loader);
        plugin.loaders().rename(loader, clean.isEmpty() ? null : clean);
        plugin.discord().log("loader-renamed", plugin.adminActions().discordTags(loader, "old", old, "admin", by));
        return null;
    }

    private static String dummy(MRChunks plugin, SelfViewer viewer, Loader loader, String by) {
        Messages m = plugin.messages();
        LoaderManager loaders = plugin.loaders();
        if (!plugin.dummies().enabled()) {
            return m.plain("self-err-dummies-off");
        }
        boolean on = !loader.dummy();
        if (on && !Dummies.fits(loader)) {
            return m.plain("self-err-dummy-square");
        }
        OfflinePlayer owner = Bukkit.getOfflinePlayer(viewer.player());
        if (on && !loaders.canAddDummy(owner)) {
            return m.plain("self-err-dummy-limit", "dummies", loaders.dummyCount(loaders.main(viewer.player())),
                    "limit", Texts.limit(loaders.limit(owner, LoaderManager.LimitKind.DUMMY)));
        }
        loaders.setDummy(loader, on);
        String state = m.server(() -> Texts.state(plugin, on));
        plugin.discord().log("dummy-toggled", plugin.adminActions().discordTags(loader, "admin", by, "state", state));
        return null;
    }

    private static String invite(MRChunks plugin, SelfViewer viewer, UUID owner, Loader loader, String name, long duration) {
        Messages m = plugin.messages();
        LoaderManager loaders = plugin.loaders();
        if (!plugin.settings().invites().enabled() || !viewer.has(INVITE)) {
            return m.plain("self-err-invites-off");
        }
        String clean = name == null ? "" : name.strip();
        OfflinePlayer target = clean.matches("[A-Za-z0-9_]{1,16}") ? Bukkit.getOfflinePlayerIfCached(clean) : null;
        if (target == null) {
            Player online = clean.isEmpty() ? null : Bukkit.getPlayerExact(clean);
            target = online;
        }
        if (target == null || (target.getPlayer() != null && Dummies.isDummy(target.getPlayer()))) {
            return m.plain("self-err-player", "player", clean);
        }
        if (loaders.sameGroup(target.getUniqueId(), owner)) {
            return m.plain("self-err-invite-self");
        }
        Map<UUID, Long> members = loaders.members(owner, loader);
        int max = plugin.settings().invites().maxMembers();
        if (!members.containsKey(target.getUniqueId()) && members.size() >= max) {
            return m.plain("self-err-invite-full", "max", max);
        }
        loaders.invite(owner, loader, target.getUniqueId(), duration);
        Player online = target.getPlayer();
        if (online != null) {
            long until = loaders.members(owner, loader).getOrDefault(target.getUniqueId(), Long.MAX_VALUE);
            m.send(online, "invite-notify", Messages.tag("owner", LoaderManager.name(owner)),
                    Messages.lazy("scope", () -> loader != null ? Texts.name(plugin, loader)
                            : m.plain("invite-scope-all-of", "owner", LoaderManager.name(owner))),
                    Messages.lazy("duration", () -> Texts.until(plugin, until)));
        }
        plugin.discord().log("loader-invite", "owner", LoaderManager.name(owner), "player", LoaderManager.name(target.getUniqueId()),
                "scope", m.server(() -> loader != null ? Texts.name(plugin, loader)
                        : m.plain("invite-scope-all-of", "owner", LoaderManager.name(owner))),
                "time", m.server(() -> Texts.duration(plugin, duration)), "admin", viewer.name() + " (web)");
        return null;
    }

    private static String uninvite(MRChunks plugin, SelfViewer viewer, UUID owner, Map<String, String> args) {
        Messages m = plugin.messages();
        LoaderManager loaders = plugin.loaders();
        UUID member;
        try {
            member = UUID.fromString(args.getOrDefault("player", ""));
        } catch (IllegalArgumentException e) {
            return m.plain("self-err-not-found");
        }
        Loader loader = "all".equals(args.get("id")) ? null : loader(loaders, args.get("id"));
        if (loader != null && !loader.owner().equals(owner)) {
            return m.plain("self-err-not-found");
        }
        if (!"all".equals(args.get("id")) && loader == null) {
            return m.plain("self-err-not-found");
        }
        loaders.uninvite(owner, loader, member);
        plugin.discord().log("loader-uninvite", "owner", LoaderManager.name(owner), "player", LoaderManager.name(member),
                "scope", m.server(() -> loader != null ? Texts.name(plugin, loader)
                        : m.plain("invite-scope-all-of", "owner", LoaderManager.name(owner))),
                "admin", viewer.name() + " (web)");
        return null;
    }

    /** One of invites.durations (ms or permanent) - anything else is refused as permanent would be too generous. */
    private static long parseDuration(MRChunks plugin, String raw) {
        try {
            long value = Long.parseLong(raw);
            if (plugin.settings().invites().durations().contains(value)) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // falls through
        }
        return plugin.settings().invites().durations().getFirst();
    }
}
