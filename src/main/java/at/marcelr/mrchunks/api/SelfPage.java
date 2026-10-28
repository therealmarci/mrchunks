package at.marcelr.mrchunks.api;

import at.marcelr.mradmin.api.SelfArea;
import at.marcelr.mradmin.api.SelfBlock;
import at.marcelr.mradmin.api.SelfViewer;
import at.marcelr.mrchunks.Loader;
import at.marcelr.mrchunks.LoaderManager;
import at.marcelr.mrchunks.LoaderStatus;
import at.marcelr.mrchunks.MRChunks;
import at.marcelr.mrchunks.Messages;
import at.marcelr.mrchunks.Settings;
import at.marcelr.mrchunks.Texts;
import at.marcelr.mrchunks.dummy.Dummies;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * MRchunks on the player's own page in MRadmin's web interface (/web): their loaders with status, budget and a map,
 * switching on and off, renaming, dummies, deleting and invites - everything they may do in game except changing the
 * size. Loaders shared with them can be switched on and off. Every action checks ownership, permissions and the same
 * rules as in game again. Only loaded while MRadmin runs (like {@link MRadminModule}).
 */
final class SelfPage {

    static final String AREA = "loaders";
    private static final String USE = "mrchunks.use";
    private static final String INVITE = "mrchunks.invite";
    private static final String MAP = "mrchunks.map";
    private static final String EXEMPT = "mrchunks.exempt";
    static final Set<String> PERMISSIONS = Set.of(USE, INVITE, MAP, EXEMPT);

    private static final String LOCKED_COLOR = "#E74C3C";
    private static final String WARN_COLOR = "#E67E22";
    private static final String OFF_COLOR = "#8A958E";
    private static final String ON_COLOR = "#2ECC71";

    private SelfPage() {
    }

    private static boolean allowed(SelfViewer viewer) {
        return viewer.has(USE) && !viewer.has(EXEMPT);
    }

    // ---------------------------------------------------------------- page

    static List<SelfArea> areas(MRChunks plugin, SelfViewer viewer) {
        if (!allowed(viewer)) {
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

        List<SelfBlock.Stat> stats = new ArrayList<>();
        stats.add(new SelfBlock.Stat(m.plain("self-loaders"), own.size() + " / " + loaders.maxLoaders(), ""));
        stats.add(new SelfBlock.Stat(m.plain("self-active"), String.valueOf(active), active > 0 ? "good" : ""));
        stats.add(new SelfBlock.Stat(m.plain("self-budget"), m.plain("self-budget-value", "used", loaders.usedBudget(owner),
                "budget", Texts.limit(budget)), free <= 0 && budget.value() != Integer.MAX_VALUE ? "warn" : ""));
        if (plugin.dummies().enabled()) {
            stats.add(new SelfBlock.Stat(m.plain("self-dummies"), loaders.dummyCount(owner) + " / "
                    + Texts.limit(loaders.limit(player, LoaderManager.LimitKind.DUMMY)), ""));
        }
        List<SelfBlock> blocks = new ArrayList<>();
        LoaderManager.Ban ban = loaders.groupBan(viewer.player());
        if (ban != null) {
            blocks.add(new SelfBlock.Text(m.plain("self-banned", "duration", Texts.banDuration(plugin, ban),
                    "reason", ban.reason()), "bad"));
        }
        blocks.add(new SelfBlock.Stats(stats));
        blocks.add(loaderTable(plugin, viewer, own, ban == null));
        blocks.add(new SelfBlock.Text(m.plain("self-size-hint"), "info"));
        if (plugin.settings().invites().enabled() && viewer.has(INVITE) && ban == null) {
            blocks.add(memberTable(plugin, owner, own));
            List<SelfBlock.Button> invite = new ArrayList<>();
            for (long duration : plugin.settings().invites().durations()) {
                invite.add(new SelfBlock.Button(m.plain("self-invite-all", "duration", Texts.duration(plugin, duration)),
                        "invite", Map.of("duration", String.valueOf(duration)), "", null,
                        new SelfBlock.Input(m.plain("self-invite-label"), "", 16)));
            }
            blocks.add(new SelfBlock.Buttons(invite));
        }
        List<Loader> shared = loaders.sharedWith(viewer.player());
        if (!shared.isEmpty()) {
            blocks.add(sharedTable(plugin, shared));
        }
        blocks.add(map(plugin, viewer, own));

        List<SelfBlock> summary = List.of(new SelfBlock.Stats(List.of(stats.get(1), stats.get(2))));
        return new SelfArea(AREA, m.plain("self-title"), "🧱", summary, blocks);
    }

    private static SelfBlock.Table loaderTable(MRChunks plugin, SelfViewer viewer, List<Loader> own, boolean actions) {
        Messages m = plugin.messages();
        List<SelfBlock.Row> rows = new ArrayList<>();
        for (Loader loader : own) {
            LoaderStatus status = LoaderStatus.of(plugin, loader);
            String id = String.valueOf(loader.id());
            List<SelfBlock.Button> buttons = new ArrayList<>();
            if (actions) {
                if (status != LoaderStatus.LOCKED) {
                    buttons.add(new SelfBlock.Button(m.plain(loader.active() ? "self-off" : "self-on"), "toggle",
                            Map.of("id", id), loader.active() ? "" : "good", null, null));
                }
                buttons.add(new SelfBlock.Button(m.plain("self-rename"), "rename", Map.of("id", id), "", null,
                        new SelfBlock.Input(m.plain("self-rename-label"), loader.name() == null ? "" : loader.name(),
                                plugin.settings().gui().maxNameLength())));
                if (plugin.dummies().enabled() && (loader.dummy() || Dummies.fits(loader))) {
                    buttons.add(new SelfBlock.Button(m.plain(loader.dummy() ? "self-dummy-off" : "self-dummy-on"), "dummy",
                            Map.of("id", id), "", null, null));
                }
                if (plugin.settings().invites().enabled() && viewer.has(INVITE)) {
                    buttons.add(new SelfBlock.Button(m.plain("self-invite"), "invite", Map.of("id", id), "", null,
                            new SelfBlock.Input(m.plain("self-invite-label"), "", 16)));
                }
                buttons.add(new SelfBlock.Button(m.plain("self-delete"), "delete", Map.of("id", id), "bad",
                        m.plain("self-delete-confirm", "name", Texts.name(plugin, loader)), null));
            }
            rows.add(new SelfBlock.Row(List.of(Texts.name(plugin, loader),
                    loader.worldName() + " " + loader.x() + " " + loader.y() + " " + loader.z(),
                    m.plain("self-size-value", "width", loader.width(), "length", loader.length(), "chunks", loader.area()),
                    state(plugin, loader, status)), tone(status), buttons));
        }
        return new SelfBlock.Table(m.plain("self-table"), List.of(m.plain("self-col-name"), m.plain("self-col-place"),
                m.plain("self-col-size"), m.plain("self-col-state")), rows, m.plain("self-empty"));
    }

    private static SelfBlock.Table memberTable(MRChunks plugin, UUID owner, List<Loader> own) {
        Messages m = plugin.messages();
        LoaderManager loaders = plugin.loaders();
        List<SelfBlock.Row> rows = new ArrayList<>();
        loaders.members(owner, null).forEach((member, until) -> rows.add(memberRow(plugin, member, until,
                m.plain("self-scope-all"), "all")));
        for (Loader loader : own) {
            loaders.members(owner, loader).forEach((member, until) -> rows.add(memberRow(plugin, member, until,
                    Texts.name(plugin, loader), String.valueOf(loader.id()))));
        }
        return new SelfBlock.Table(m.plain("self-members"), List.of(m.plain("self-col-player"), m.plain("self-col-scope"),
                m.plain("self-col-until")), rows, m.plain("self-members-empty"));
    }

    private static SelfBlock.Row memberRow(MRChunks plugin, UUID member, long until, String scope, String id) {
        Messages m = plugin.messages();
        String time = until == Long.MAX_VALUE ? m.plain("self-permanent") : Texts.date(m, until);
        return new SelfBlock.Row(List.of(LoaderManager.name(member), scope, time), "",
                List.of(new SelfBlock.Button(m.plain("self-uninvite"), "uninvite",
                        Map.of("id", id, "player", member.toString()), "", null, null)));
    }

    private static SelfBlock.Table sharedTable(MRChunks plugin, List<Loader> shared) {
        Messages m = plugin.messages();
        List<SelfBlock.Row> rows = new ArrayList<>();
        for (Loader loader : shared) {
            LoaderStatus status = LoaderStatus.of(plugin, loader);
            List<SelfBlock.Button> buttons = status == LoaderStatus.LOCKED ? List.of()
                    : List.of(new SelfBlock.Button(m.plain(loader.active() ? "self-off" : "self-on"), "shared",
                    Map.of("id", String.valueOf(loader.id())), loader.active() ? "" : "good", null, null));
            rows.add(new SelfBlock.Row(List.of(Texts.name(plugin, loader), LoaderManager.name(loader.owner()),
                    state(plugin, loader, status)), tone(status), buttons));
        }
        return new SelfBlock.Table(m.plain("self-shared"), List.of(m.plain("self-col-name"), m.plain("self-col-owner"),
                m.plain("self-col-state")), rows, "");
    }

    private static SelfBlock.AreaMap map(MRChunks plugin, SelfViewer viewer, List<Loader> own) {
        Messages m = plugin.messages();
        List<SelfBlock.Rect> rects = new ArrayList<>();
        for (Loader loader : own) {
            LoaderStatus status = LoaderStatus.of(plugin, loader);
            String color = switch (status) {
                case LOCKED -> LOCKED_COLOR;
                case WARN -> WARN_COLOR;
                case INACTIVE -> OFF_COLOR;
                default -> ON_COLOR;
            };
            rects.add(new SelfBlock.Rect(loader.worldName(), loader.minChunkX() << 4, loader.minChunkZ() << 4,
                    (loader.maxChunkX() + 1) << 4, (loader.maxChunkZ() + 1) << 4, Texts.name(plugin, loader), color, null));
        }
        String url = viewer.has(MAP) ? plugin.maps().mapUrl() : "";
        return new SelfBlock.AreaMap(m.plain("self-map"), rects, url.isEmpty() ? null : url, m.plain("self-map-open"));
    }

    private static String state(MRChunks plugin, Loader loader, LoaderStatus status) {
        Messages m = plugin.messages();
        return switch (status) {
            case LOCKED -> m.plain("self-state-locked");
            case INACTIVE -> m.plain("self-state-off");
            case PAUSED -> m.plain("self-state-paused");
            case WARN -> m.plain("self-state-warn");
            case OK -> m.plain("self-state-on") + (plugin.dummies().dummyOf(loader) != null ? " · " + m.plain("self-state-dummy") : "");
        };
    }

    private static String tone(LoaderStatus status) {
        return switch (status) {
            case LOCKED -> "bad";
            case WARN, PAUSED -> "warn";
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
        if (action.equals("invite") && "all".equals(args.getOrDefault("id", "all")) && args.containsKey("duration")) {
            return invite(plugin, viewer, owner, null, args.get("value"), parseDuration(plugin, args.get("duration")));
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
            case "invite" -> invite(plugin, viewer, owner, loader, args.get("value"), Settings.PERMANENT);
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
