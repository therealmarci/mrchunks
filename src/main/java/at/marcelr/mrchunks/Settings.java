package at.marcelr.mrchunks;

import at.marcelr.mrchunks.lag.LagLimits;
import at.marcelr.mrchunks.lag.Metric;
import at.marcelr.mrchunks.util.DurationUtil;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/** All values from config.yml, parsed and validated once per (re)load. */
public record Settings(
        LoaderSettings loaders,
        ItemSettings item,
        BorderSettings border,
        SquaremapSettings squaremap,
        UpdateSettings update,
        GuiSettings gui,
        InviteSettings invites,
        LagSettings lag,
        PauseSettings pause,
        ShedSettings shed,
        DummySettings dummies,
        LinkSettings links
) {

    /**
     * Dummy players: a fake player sits on a loader and counts as a player nearby (mob AI, spawning, random ticks).
     * {@code defaultLimit} = dummies per player, {@code seat} = sitting on the block instead of standing,
     * {@code ownerSkin} = the owner's skin instead of the default one.
     */
    public record DummySettings(boolean enabled, int defaultLimit, boolean seat, boolean ownerSkin) {
    }

    /** Linked accounts: second accounts per main account and how long a link request stays open (seconds). */
    public record LinkSettings(boolean enabled, int maxAccounts, int requestSeconds) {
    }

    /**
     * Server pause: all loaders release their chunks once the server's TPS (1-minute average) has been below
     * {@code belowTps} for {@code pauseAfterSeconds}, and load them again once it has been at least {@code resumeTps}
     * for {@code resumeAfterSeconds}. The on/off state of the loaders is not touched.
     */
    public record PauseSettings(boolean enabled, double belowTps, double resumeTps, int pauseAfterSeconds,
                                int resumeAfterSeconds, boolean notifyOwners) {
    }

    /**
     * Staged shutdown: the average tick time over {@code averageSeconds} is compared with {@code warnMspt} (warning)
     * and {@code shutdownMspt} (one loader after the other is disabled by lag, every {@code stepSeconds}). Warnings
     * and shutdowns last at least {@code minMinutes}; more than {@code maxShutdowns} shutdowns of one loader within
     * {@code windowMinutes} lock it. From {@code hardCapMspt} (average over {@code hardCapSeconds}, 0 = off) all
     * loaders are disabled at once.
     */
    public record ShedSettings(boolean enabled, int averageSeconds, double warnMspt, double shutdownMspt,
                               int stepSeconds, int minMinutes, int maxShutdowns, int windowMinutes,
                               double hardCapMspt, int hardCapSeconds) {
    }

    /**
     * Lag protection: every {@code sampleSeconds} the area of each active loader is measured. A loader is locked when
     * a metric is over its lock limit {@code lockAfterSamples} times in a row (and, with {@code onlyWhenServerLags},
     * only while the server's average tick time is at least {@code serverLagMspt}). From {@code forceLockMultiplier}
     * times a lock limit on (0 = off) the server state does not matter. Durations in ms. Limits: see {@link #limitsFor}.
     */
    public record LagSettings(boolean enabled, int sampleSeconds, int lockAfterSamples, boolean onlyWhenServerLags,
                              double serverLagMspt, double forceLockMultiplier, LagLimits limits, int limitsPerChunks,
                              LagLimits smallLimits, int smallChunks) {

        /**
         * The limits for an area of {@code chunks} chunks (a loader, or all active loaders of an owner together), in
         * whole numbers: up to {@code smallChunks} (3x3) the fixed {@code smallLimits}; above, the configured ones
         * scaled from {@code limitsPerChunks} chunks to the area - so the same budget allows the same load - but never
         * below the small ones.
         */
        public LagLimits limitsFor(int chunks) {
            if (chunks <= smallChunks) {
                return smallLimits.rounded();
            }
            return limits.scaled((double) chunks / limitsPerChunks).atLeast(smallLimits).rounded();
        }
    }

    /** Marker for a permanent ban. */
    public static final long PERMANENT = -1L;

    public enum LoadMethod { TICKET, FORCELOAD }

    /** What happens when a new loader's area overlaps existing ones (loaders.overlap). */
    public enum OverlapMode { ALLOW, WARN, DENY_OWN, DENY_ALL }

    /**
     * @param defaultBudget chunks a player may keep loaded with all their active loaders together
     * @param defaultReach  reach of a new loader in every direction (3 = 7x7)
     * @param maxReach      highest reach per direction a loader can be enlarged to
     * @param maxLoaders    loaders a player may place (the same for everyone)
     */
    public record LoaderSettings(int defaultBudget, int defaultReach, int maxReach, int maxLoaders,
                                 Set<String> disabledWorlds, LoadMethod loadMethod, OverlapMode overlap,
                                 boolean restoreMissingBlocks, int checkIntervalSeconds) {

        /** Edge length of a new loader's square in chunks, e.g. 7 for reach 3. */
        public int defaultSize() {
            return defaultReach * 2 + 1;
        }

        public int defaultArea() {
            return defaultSize() * defaultSize();
        }
    }

    /** @param blockChoices blocks the owner may choose for the world besides {@code material} (empty = no choice) */
    public record ItemSettings(Material material, String name, List<String> lore, boolean glint,
                               List<Material> blockChoices) {
    }

    /** Glowing lines: block and glow colour while active / inactive, bar thickness, line heights around the viewer. */
    public record BorderSettings(boolean showOnOpen, int durationSeconds, Material block, Material inactiveBlock,
                                 org.bukkit.Color color, org.bukkit.Color inactiveColor, double thickness,
                                 int below, int above, int lineSpacing) {
    }

    /** {@code color == null} means one colour per owner. */
    public record SquaremapSettings(boolean enabled, boolean autoConfigure, String overworldName, String netherName,
                                    String endName, boolean hidePlayers, String layerName, boolean defaultHidden, boolean showInactive,
                                    java.awt.Color color, java.awt.Color inactiveColor, java.awt.Color pausedColor, double fillOpacity,
                                    boolean showIcon, String hoverTooltip, String clickTooltip, int updateSeconds,
                                    String address, boolean reverseProxy, boolean detectPublicIp, GridSettings grid,
                                    StatusSettings status) {
    }

    /**
     * Extras of the chunk loader layer: own colours for locked and conspicuous loaders ({@code colors}), one layer
     * each for active, switched-off and locked loaders ({@code splitLayers}, names from the language file), a dummy
     * icon on loaders with a dummy, and the texts of the click tooltip for these states.
     */
    public record StatusSettings(boolean colors, java.awt.Color lockedColor, java.awt.Color warnColor,
                                 boolean splitLayers, String inactiveLayerName, String lockedLayerName,
                                 boolean dummyIcon, String tooltipLocked, String tooltipWarn, String tooltipDummy) {
    }

    public record GridSettings(boolean enabled, boolean defaultVisible, String color, double opacity,
                               int minChunkPixels, boolean regionLines, String regionColor, boolean labels,
                               String label) {
    }

    public record UpdateSettings(boolean enabled, String source, boolean includePrereleases,
                                 int intervalHours, boolean notifyOnJoin) {
    }

    /** Invites: quick-select durations (ms or {@link #PERMANENT}) and the maximum invited players per list. */
    public record InviteSettings(boolean enabled, List<Long> durations, int maxMembers) {
    }

    /** Durations in ms; ban durations may contain {@link #PERMANENT}. */
    public record GuiSettings(int refreshTicks, int confirmSeconds, List<Long> banDurations, String banReason,
                              boolean sounds, int maxNameLength) {
    }

    /** Texts (item, map, ban reason) come from the language file, everything else from config.yml. */
    public static Settings from(FileConfiguration c, Messages texts, Logger log) {
        int maxReach = Math.clamp(c.getInt("loaders.max-reach", 7), 0, 32);
        LoaderSettings loaders = new LoaderSettings(
                Math.max(0, c.getInt("loaders.default-budget", 147)),
                Math.clamp(c.getInt("loaders.default-reach", 3), 0, maxReach),
                maxReach,
                Math.clamp(c.getInt("loaders.max-loaders", 30), 0, 1000),
                c.getStringList("loaders.disabled-worlds").stream()
                        .map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet()),
                parseEnum(c.getString("loaders.load-method"), LoadMethod.TICKET, "loaders.load-method", log),
                parseEnum(c.getString("loaders.overlap"), OverlapMode.WARN, "loaders.overlap", log),
                c.getBoolean("loaders.restore-missing-blocks", true),
                Math.max(5, c.getInt("loaders.check-interval-seconds", 60)));

        Material material = Material.matchMaterial(c.getString("item.material", "EMERALD_BLOCK"));
        if (material == null || !material.isBlock() || !material.isSolid()) {
            log.warning("item.material '" + c.getString("item.material") + "' is not a solid block, using EMERALD_BLOCK.");
            material = Material.EMERALD_BLOCK;
        }
        List<Material> blockChoices = new ArrayList<>();
        for (String raw : c.getStringList("item.block-choices")) {
            Material choice = Material.matchMaterial(raw);
            // A plain full block: nothing that falls, holds items (chests, hoppers ...) or is only part of a block.
            if (choice == null || !choice.isBlock() || !choice.isSolid() || choice.hasGravity() || !fullBlock(choice)
                    || choice.createBlockData().createBlockState() instanceof org.bukkit.block.TileState) {
                log.warning("item.block-choices: '" + raw + "' is not a solid block without gravity or function, skipped.");
            } else if (choice != material && !blockChoices.contains(choice) && blockChoices.size() < 44) {
                blockChoices.add(choice);
            }
        }
        ItemSettings item = new ItemSettings(material,
                texts.optional("item-name"),
                texts.list("item-lore"),
                c.getBoolean("item.glint", true),
                List.copyOf(blockChoices));

        BorderSettings border = new BorderSettings(
                c.getBoolean("border.show-on-open", false),
                Math.clamp(c.getInt("border.duration-seconds", 60), 1, 600),
                blockMaterial(c.getString("border.block"), Material.LIME_CONCRETE, "border.block", log),
                blockMaterial(c.getString("border.inactive-block"), Material.LIGHT_GRAY_CONCRETE, "border.inactive-block", log),
                bukkitColor(c.getString("border.color"), 0x4CFF7A, "border.color", log),
                bukkitColor(c.getString("border.inactive-color"), 0xA0A8A4, "border.inactive-color", log),
                Math.clamp(c.getDouble("border.thickness", 0.08), 0.02, 1.0),
                Math.clamp(c.getInt("border.below", 1), 0, 32),
                Math.clamp(c.getInt("border.above", 5), 0, 64),
                Math.clamp(c.getInt("border.line-spacing", 3), 1, 16));

        String rawColor = c.getString("squaremap.color", "status");
        SquaremapSettings squaremap = new SquaremapSettings(
                c.getBoolean("squaremap.enabled", true),
                c.getBoolean("squaremap.auto-configure", true),
                texts.optional("map-dimension-overworld"),
                texts.optional("map-dimension-nether"),
                texts.optional("map-dimension-end"),
                c.getBoolean("squaremap.hide-players", true),
                texts.optional("map-layer-name"),
                c.getBoolean("squaremap.default-hidden", false),
                c.getBoolean("squaremap.show-inactive", true),
                rawColor.equalsIgnoreCase("owner") ? null : rawColor.equalsIgnoreCase("status")
                        ? parseColor(c.getString("squaremap.active-color", "#2ECC71"), "squaremap.active-color", log)
                        : parseColor(rawColor, "squaremap.color", log),
                parseColor(c.getString("squaremap.inactive-color", "#8A958E"), "squaremap.inactive-color", log),
                parseColor(c.getString("squaremap.paused-color", "#F1C40F"), "squaremap.paused-color", log),
                Math.clamp(c.getDouble("squaremap.fill-opacity", 0.25), 0.0, 1.0),
                c.getBoolean("squaremap.show-icon", true),
                texts.optional("map-hover-tooltip"),
                texts.optional("map-click-tooltip"),
                Math.clamp(c.getInt("squaremap.update-seconds", 60), 5, 3600),
                c.getString("squaremap.address", "").trim(),
                c.getBoolean("squaremap.reverse-proxy", false),
                c.getBoolean("squaremap.detect-public-ip", true),
                new GridSettings(
                        c.getBoolean("squaremap.chunk-grid.enabled", true),
                        c.getBoolean("squaremap.chunk-grid.default-visible", true),
                        hexColor(c.getString("squaremap.chunk-grid.color", "#FFFFFF"), "#FFFFFF"),
                        Math.clamp(c.getDouble("squaremap.chunk-grid.opacity", 0.35), 0.0, 1.0),
                        Math.clamp(c.getInt("squaremap.chunk-grid.min-chunk-pixels", 8), 2, 64),
                        c.getBoolean("squaremap.chunk-grid.region-lines", true),
                        hexColor(c.getString("squaremap.chunk-grid.region-color", "#FFD54F"), "#FFD54F"),
                        c.getBoolean("squaremap.chunk-grid.labels", true),
                        texts.optional("map-grid-label")),
                new StatusSettings(
                        c.getBoolean("squaremap.status-colors", true),
                        parseColor(c.getString("squaremap.locked-color", "#E74C3C"), "squaremap.locked-color", log),
                        parseColor(c.getString("squaremap.warn-color", "#F1C40F"), "squaremap.warn-color", log),
                        c.getBoolean("squaremap.split-layers", true),
                        texts.optional("map-layer-inactive"),
                        texts.optional("map-layer-locked"),
                        c.getBoolean("squaremap.show-dummy-icon", true),
                        texts.optional("map-tooltip-locked"),
                        texts.optional("map-tooltip-warn"),
                        texts.optional("map-tooltip-dummy")));

        String source = c.getString("update-checker.source", "github").toLowerCase(Locale.ROOT);
        if (!source.equals("modrinth") && !source.equals("github")) {
            log.warning("Invalid update-checker.source '" + source + "', using github.");
            source = "github";
        }
        UpdateSettings update = new UpdateSettings(
                c.getBoolean("update-checker.enabled", true),
                source,
                c.getBoolean("update-checker.include-prereleases", false),
                Math.max(1, c.getInt("update-checker.interval-hours", 6)),
                c.getBoolean("update-checker.notify-on-join", true));

        GuiSettings gui = new GuiSettings(
                Math.max(5, c.getInt("gui.refresh-ticks", 20)),
                Math.clamp(c.getInt("gui.confirm-seconds", 5), 1, 60),
                banDurations(c.getStringList("gui.ban-durations"), log),
                texts.optional("gui-ban-reason"),
                c.getBoolean("gui.sounds", true),
                Math.clamp(c.getInt("gui.max-name-length", 24), 1, 64));

        InviteSettings invites = new InviteSettings(
                c.getBoolean("invites.enabled", true),
                durations(c.getStringList("invites.durations"), List.of("1d", "7d", "30d", "permanent"), "invites.durations", log),
                Math.clamp(c.getInt("invites.max-members", 10), 1, 100));

        LagSettings lag = new LagSettings(
                c.getBoolean("lag-protection.enabled", true),
                Math.clamp(c.getInt("lag-protection.sample-seconds", 30), 5, 600),
                Math.clamp(c.getInt("lag-protection.lock-after-samples", 3), 1, 100),
                c.getBoolean("lag-protection.only-when-server-lags", true),
                Math.clamp(c.getDouble("lag-protection.server-lag-mspt", 40), 1, 1000),
                forceLockMultiplier(c.getDouble("lag-protection.force-lock-multiplier", 2), log),
                limits(c, "lag-protection.limits", LagLimits.defaults(), log),
                Math.clamp(c.getInt("lag-protection.limits-per-chunks", 49), 1, 10_000),
                limits(c, "lag-protection.small-loaders.limits", LagLimits.smallDefaults(), log),
                Math.clamp(c.getInt("lag-protection.small-loaders.max-chunks", 9), 0, 10_000));

        double below = Math.clamp(c.getDouble("server-pause.below-tps", 15), 1, 19.9);
        double resume = Math.clamp(c.getDouble("server-pause.resume-tps", 18), 1, 20);
        if (resume < below) {
            log.warning("server-pause: resume-tps (" + resume + ") is below below-tps (" + below + "), using resume-tps = below-tps.");
            resume = below;
        }
        PauseSettings pause = new PauseSettings(
                c.getBoolean("server-pause.enabled", true),
                below,
                resume,
                Math.clamp(c.getInt("server-pause.pause-after-seconds", 30), 0, 3600),
                Math.clamp(c.getInt("server-pause.resume-after-seconds", 60), 0, 3600),
                c.getBoolean("server-pause.notify-owners", true));

        double warnMspt = Math.clamp(c.getDouble("staged-shutdown.warn-mspt", 40), 1, 1000);
        double shutdownMspt = Math.clamp(c.getDouble("staged-shutdown.shutdown-mspt", 45), 1, 1000);
        if (shutdownMspt < warnMspt) {
            log.warning("staged-shutdown: shutdown-mspt (" + shutdownMspt + ") is below warn-mspt (" + warnMspt
                    + "), using shutdown-mspt = warn-mspt.");
            shutdownMspt = warnMspt;
        }
        int averageSeconds = Math.clamp(c.getInt("staged-shutdown.average-seconds", 60), 5, 600);
        double hardCap = Math.max(0, c.getDouble("staged-shutdown.hard-cap-mspt", 70));
        if (hardCap > 0 && hardCap < shutdownMspt) {
            log.warning("staged-shutdown: hard-cap-mspt (" + hardCap + ") is below shutdown-mspt (" + shutdownMspt
                    + "), using hard-cap-mspt = shutdown-mspt.");
            hardCap = shutdownMspt;
        }
        ShedSettings shed = new ShedSettings(
                c.getBoolean("staged-shutdown.enabled", true),
                averageSeconds,
                warnMspt,
                shutdownMspt,
                Math.clamp(c.getInt("staged-shutdown.step-seconds", 60), 5, 3600),
                Math.clamp(c.getInt("staged-shutdown.min-minutes", 5), 1, 1440),
                Math.clamp(c.getInt("staged-shutdown.max-shutdowns", 2), 0, 100),
                Math.clamp(c.getInt("staged-shutdown.window-minutes", 60), 1, 10_080),
                hardCap,
                Math.clamp(c.getInt("staged-shutdown.hard-cap-seconds", 10), 1, averageSeconds));

        DummySettings dummies = new DummySettings(
                c.getBoolean("dummies.enabled", false),
                Math.clamp(c.getInt("dummies.default-limit", 1), 0, 1000),
                c.getBoolean("dummies.sit", true),
                c.getBoolean("dummies.owner-skin", true));

        LinkSettings links = new LinkSettings(
                c.getBoolean("linked-accounts.enabled", true),
                Math.clamp(c.getInt("linked-accounts.max-accounts", 1), 1, 10),
                Math.clamp(c.getInt("linked-accounts.request-seconds", 120), 10, 3600));

        return new Settings(loaders, item, border, squaremap, update, gui, invites, lag, pause, shed, dummies, links);
    }

    /** "#RRGGBB" as written to the web page; anything else becomes the fallback (no script injection). */
    private static String hexColor(String raw, String fallback) {
        String value = raw == null ? "" : raw.trim();
        return value.matches("#[0-9a-fA-F]{6}") ? value : fallback;
    }

    private static java.awt.Color parseColor(String raw, String path, Logger log) {
        try {
            return new java.awt.Color(Integer.parseInt(raw.replace("#", "").trim(), 16));
        } catch (RuntimeException e) {
            log.warning("Invalid colour '" + raw + "' for " + path + ", using #8A958E.");
            return new java.awt.Color(0x8A958E);
        }
    }

    /** Top and bottom are whole faces (no slab, stairs, fence ...) - unlike isOccluding() also true for glowstone. */
    private static boolean fullBlock(Material material) {
        org.bukkit.block.data.BlockData data = material.createBlockData();
        return data.isFaceSturdy(org.bukkit.block.BlockFace.UP, org.bukkit.block.BlockSupport.FULL)
                && data.isFaceSturdy(org.bukkit.block.BlockFace.DOWN, org.bukkit.block.BlockSupport.FULL);
    }

    private static Material blockMaterial(String raw, Material fallback, String path, Logger log) {
        Material material = raw == null ? null : Material.matchMaterial(raw);
        if (material == null || !material.isBlock()) {
            if (raw != null && !raw.isBlank()) {
                log.warning("Invalid block '" + raw + "' for " + path + ", using " + fallback.name() + ".");
            }
            return fallback;
        }
        return material;
    }

    private static org.bukkit.Color bukkitColor(String raw, int fallback, String path, Logger log) {
        if (raw == null || raw.isBlank()) {
            return org.bukkit.Color.fromRGB(fallback);
        }
        try {
            return org.bukkit.Color.fromRGB(Integer.parseInt(raw.replace("#", "").trim(), 16) & 0xFFFFFF);
        } catch (NumberFormatException e) {
            log.warning("Invalid colour '" + raw + "' for " + path + ".");
            return org.bukkit.Color.fromRGB(fallback);
        }
    }

    /** Case-insensitive enum value; falls back to {@code fallback} with a warning. */
    private static <E extends Enum<E>> E parseEnum(String raw, E fallback, String path, Logger log) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(fallback.getDeclaringClass(), raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            log.warning("Invalid value '" + raw + "' for " + path + ", using " + fallback.name().toLowerCase(Locale.ROOT) + ".");
            return fallback;
        }
    }

    /** Warn and lock limit per metric under {@code path} (warn above lock is lowered to lock). */
    private static LagLimits limits(FileConfiguration c, String path, LagLimits defaults, Logger log) {
        Map<Metric, Double> warn = new EnumMap<>(Metric.class);
        Map<Metric, Double> lock = new EnumMap<>(Metric.class);
        for (Metric metric : Metric.values()) {
            String key = path + "." + metric.key();
            double w = Math.max(0, c.getDouble(key + ".warn", defaults.warn(metric)));
            double l = Math.max(0, c.getDouble(key + ".lock", defaults.lock(metric)));
            if (l > 0 && w > l) {
                log.warning(key + ": warn (" + w + ") is above lock (" + l + "), using warn = lock.");
                w = l;
            }
            warn.put(metric, w);
            lock.put(metric, l);
        }
        return new LagLimits(Map.copyOf(warn), Map.copyOf(lock));
    }

    /** Exactly three quick-select ban durations; invalid entries fall back to the defaults. */
    /** 0 (or less) = off; below 1 would lock under the normal lock limit, so it is raised to 1. */
    private static double forceLockMultiplier(double raw, Logger log) {
        if (raw <= 0) {
            return 0;
        }
        if (raw < 1) {
            log.warning("lag-protection.force-lock-multiplier (" + raw + ") is below 1, using 1.");
            return 1;
        }
        return Math.min(raw, 1000);
    }

    private static List<Long> banDurations(List<String> raw, Logger log) {
        List<Long> result = durations(raw, List.of("1d", "7d", "permanent"), "gui.ban-durations", log);
        return result.size() >= 3 ? result.subList(0, 3) : result;
    }

    /** Quick-select durations; invalid entries fall back to the defaults, at least the defaults' count. */
    private static List<Long> durations(List<String> raw, List<String> defaults, String path, Logger log) {
        List<Long> result = new ArrayList<>();
        int count = Math.min(7, Math.max(defaults.size(), raw.size()));
        for (int i = 0; i < count; i++) {
            String value = i < raw.size() ? raw.get(i) : defaults.get(i);
            long parsed = parseBanDuration(value);
            if (parsed == 0) {
                String fallback = defaults.get(Math.min(i, defaults.size() - 1));
                log.warning("Invalid duration '" + value + "' in " + path + ", using " + fallback + ".");
                parsed = parseBanDuration(fallback);
            }
            result.add(parsed);
        }
        return List.copyOf(result);
    }

    /** @return duration in ms, {@link #PERMANENT}, or 0 if invalid */
    public static long parseBanDuration(String value) {
        if (value.equalsIgnoreCase("permanent") || value.equalsIgnoreCase("perm") || value.equalsIgnoreCase("dauerhaft")) {
            return PERMANENT;
        }
        long parsed = DurationUtil.parse(value);
        return parsed > 0 ? parsed : 0;
    }
}
