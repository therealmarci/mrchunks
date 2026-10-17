package at.marcelr.mrchunks;

import at.marcelr.mrchunks.util.DurationUtil;
import org.bukkit.Material;
import org.bukkit.Particle;
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
        WebSettings web,
        UpdateSettings update,
        GuiSettings gui
) {

    /** Marker for a permanent ban. */
    public static final long PERMANENT = -1L;

    public enum LoadMethod { TICKET, FORCELOAD }

    public record LoaderSettings(int radius, int defaultLimit, Set<String> disabledWorlds, LoadMethod loadMethod,
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

    public record BorderSettings(boolean showOnOpen, int durationSeconds, Particle particle, int spacing,
                                 int below, int above) {
    }

    public record WebSettings(boolean enabled, String bind, int port, String accessKey, boolean showOwners,
                              boolean showCoordinates, String title, int refreshSeconds) {
    }

    public record UpdateSettings(boolean enabled, String source, boolean includePrereleases,
                                 int intervalHours, boolean notifyOnJoin) {
    }

    /** Durations in ms; ban durations may contain {@link #PERMANENT}. */
    public record GuiSettings(int refreshTicks, int confirmSeconds, List<Long> banDurations, String banReason,
                              boolean sounds) {
    }

    public static Settings from(FileConfiguration c, Logger log) {
        LoaderSettings loaders = new LoaderSettings(
                Math.clamp(c.getInt("loaders.radius", 3), 0, 10),
                Math.max(0, c.getInt("loaders.default-limit", 3)),
                c.getStringList("loaders.disabled-worlds").stream()
                        .map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet()),
                parseEnum(c.getString("loaders.load-method"), LoadMethod.TICKET, "loaders.load-method", log),
                c.getBoolean("loaders.restore-missing-blocks", true),
                Math.max(5, c.getInt("loaders.check-interval-seconds", 60)));

        Material material = Material.matchMaterial(c.getString("item.material", "EMERALD_BLOCK"));
        if (material == null || !material.isBlock() || !material.isSolid()) {
            log.warning("item.material '" + c.getString("item.material") + "' ist kein fester Block, verwende EMERALD_BLOCK.");
            material = Material.EMERALD_BLOCK;
        }
        ItemSettings item = new ItemSettings(material,
                c.getString("item.name", "<green><bold>Chunkloader"),
                c.getStringList("item.lore"),
                c.getBoolean("item.glint", true));

        Particle particle = parseEnum(c.getString("border.particle"), Particle.HAPPY_VILLAGER, "border.particle", log);
        if (particle.getDataType() != Void.class) {
            // Particles like DUST need extra data and would throw when spawned without it.
            log.warning("border.particle '" + particle.name() + "' braucht Zusatzdaten und wird nicht unterstützt, verwende HAPPY_VILLAGER.");
            particle = Particle.HAPPY_VILLAGER;
        }
        BorderSettings border = new BorderSettings(
                c.getBoolean("border.show-on-open", true),
                Math.clamp(c.getInt("border.duration-seconds", 30), 1, 600),
                particle,
                Math.clamp(c.getInt("border.spacing", 2), 1, 8),
                Math.clamp(c.getInt("border.below", 2), 0, 32),
                Math.clamp(c.getInt("border.above", 6), 0, 64));

        WebSettings web = new WebSettings(
                c.getBoolean("web.enabled", false),
                c.getString("web.bind", "0.0.0.0"),
                Math.clamp(c.getInt("web.port", 8130), 1, 65535),
                c.getString("web.access-key", ""),
                c.getBoolean("web.show-owners", true),
                c.getBoolean("web.show-coordinates", true),
                c.getString("web.title", "MRchunks - Chunkloader"),
                Math.clamp(c.getInt("web.refresh-seconds", 30), 5, 3600));

        String source = c.getString("update-checker.source", "github").toLowerCase(Locale.ROOT);
        if (!source.equals("modrinth") && !source.equals("github")) {
            log.warning("Ungültige update-checker.source '" + source + "', verwende github.");
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
                c.getString("gui.ban-reason", "über das Admin-Menü"),
                c.getBoolean("gui.sounds", true));

        return new Settings(loaders, item, border, web, update, gui);
    }

    /** Case-insensitive enum value; falls back to {@code fallback} with a warning. */
    private static <E extends Enum<E>> E parseEnum(String raw, E fallback, String path, Logger log) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(fallback.getDeclaringClass(), raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warning("Ungültiger Wert '" + raw + "' für " + path + ", verwende " + fallback.name().toLowerCase(Locale.ROOT) + ".");
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
                log.warning("Ungültige Dauer '" + value + "' in gui.ban-durations, verwende " + defaults.get(i) + ".");
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
