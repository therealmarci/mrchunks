package at.marcelr.mrchunks;

import at.marcelr.mrchunks.util.DurationUtil;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
        GuiSettings gui
) {

    /** Marker for a permanent ban. */
    public static final long PERMANENT = -1L;

    public enum LoadMethod { TICKET, FORCELOAD }

    /** What happens when a new loader's area overlaps existing ones (loaders.overlap). */
    public enum OverlapMode { ALLOW, WARN, DENY_OWN, DENY_ALL }

    public record LoaderSettings(int radius, int defaultLimit, int defaultActiveLimit, Set<String> disabledWorlds,
                                 LoadMethod loadMethod, OverlapMode overlap,
                                 boolean restoreMissingBlocks, int checkIntervalSeconds) {

        /** Edge length of the loaded square in chunks, e.g. 7 for radius 3. */
        public int size() {
            return radius * 2 + 1;
        }

        public int chunkCount() {
            return size() * size();
        }
    }

    public record ItemSettings(Material material, String name, List<String> lore, boolean glint) {
    }

    /** Glowing lines: block and glow colour while active / inactive, bar thickness, line heights around the viewer. */
    public record BorderSettings(boolean showOnOpen, int durationSeconds, Material block, Material inactiveBlock,
                                 org.bukkit.Color color, org.bukkit.Color inactiveColor, double thickness,
                                 int below, int above, int lineSpacing) {
    }

    /** {@code color == null} means one colour per owner. */
    public record SquaremapSettings(boolean enabled, boolean autoConfigure, String overworldName, String netherName,
                                    String endName, boolean hidePlayers, String layerName, boolean defaultHidden, boolean showInactive,
                                    java.awt.Color color, java.awt.Color inactiveColor, double fillOpacity,
                                    boolean showIcon, String hoverTooltip, String clickTooltip, int updateSeconds,
                                    String address, boolean reverseProxy, boolean detectPublicIp, GridSettings grid) {
    }

    public record GridSettings(boolean enabled, boolean defaultVisible, String color, double opacity,
                               int minChunkPixels, boolean regionLines, String regionColor, boolean labels,
                               String label) {
    }

    public record UpdateSettings(boolean enabled, String source, boolean includePrereleases,
                                 int intervalHours, boolean notifyOnJoin) {
    }

    /** Durations in ms; ban durations may contain {@link #PERMANENT}. */
    public record GuiSettings(int refreshTicks, int confirmSeconds, List<Long> banDurations, String banReason,
                              boolean sounds, int maxNameLength) {
    }

    /** Texts (item, map, ban reason) come from the language file, everything else from config.yml. */
    public static Settings from(FileConfiguration c, Messages texts, Logger log) {
        LoaderSettings loaders = new LoaderSettings(
                Math.clamp(c.getInt("loaders.radius", 3), 0, 10),
                Math.max(0, c.getInt("loaders.default-limit", 3)),
                Math.max(0, c.getInt("loaders.default-active-limit", 3)),
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
        ItemSettings item = new ItemSettings(material,
                texts.optional("item-name"),
                texts.list("item-lore"),
                c.getBoolean("item.glint", true));

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

        String rawColor = c.getString("squaremap.color", "owner");
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
                rawColor.equalsIgnoreCase("owner") ? null : parseColor(rawColor, "squaremap.color", log),
                parseColor(c.getString("squaremap.inactive-color", "#8A958E"), "squaremap.inactive-color", log),
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
                        texts.optional("map-grid-label")));

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

        return new Settings(loaders, item, border, squaremap, update, gui);
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

    /** Exactly three quick-select ban durations; invalid entries fall back to the defaults. */
    private static List<Long> banDurations(List<String> raw, Logger log) {
        List<String> defaults = List.of("1d", "7d", "permanent");
        List<Long> result = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            String value = i < raw.size() ? raw.get(i) : defaults.get(i);
            long parsed = parseBanDuration(value);
            if (parsed == 0) {
                log.warning("Invalid duration '" + value + "' in gui.ban-durations, using " + defaults.get(i) + ".");
                parsed = parseBanDuration(defaults.get(i));
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
