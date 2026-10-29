package at.marcelr.mrchunks.map;

import at.marcelr.mrchunks.MRChunks;
import at.marcelr.mrchunks.Settings;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Keeps the recommended settings in squaremap's config.yml up to date - checked on every start. MRchunks remembers
 * which values it wrote (squaremap-setup.yml): a value that is missing or still the one MRchunks wrote gets the
 * current recommendation, a value the server owner changed stays. Values MRchunks does not recommend any more are
 * removed again if nobody changed them. Only touches the file; needs no squaremap classes.
 */
final class SquaremapSetup {

    /** Format of squaremap-setup.yml; 1 = only "applied-version" (written values unknown). */
    static final int VERSION = 2;

    /** The main dimensions get a readable name - other worlds (e.g. MRpvp's arena world) are left alone. */
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";
    private static final String END = "minecraft:the_end";

    private final MRChunks plugin;
    private final File squaremapConfig;
    private final File marker;

    SquaremapSetup(MRChunks plugin) {
        this.plugin = plugin;
        this.squaremapConfig = new File(plugin.getDataFolder().getParentFile(), "squaremap/config.yml");
        this.marker = new File(plugin.getDataFolder(), "squaremap-setup.yml");
    }

    /** One recommended value and how it is described in the log and to the admin. */
    private record Want(Object value, Change label) {
    }

    /** What MRchunks recommends right now, by path in squaremap's config. */
    private Map<String, Want> wanted() {
        Settings.SquaremapSettings settings = plugin.settings().squaremap();
        Map<String, Want> want = new LinkedHashMap<>();
        want.put("world-settings.default.map.render.background.interval-seconds",
                new Want(5, new Change("map-setup-change-interval")));
        want.put("world-settings.default.player-tracker.enabled", new Want(false, new Change("map-setup-change-tracker")));
        want.put("settings.ui.sidebar.pinned", new Want("pinned", new Change("map-setup-change-sidebar")));
        for (World world : Bukkit.getWorlds()) {
            String key = world.getKey().asString();
            String base = "world-settings." + key.replace(".", "____dot____") + ".map.";
            String name = switch (key) {
                case OVERWORLD -> settings.overworldName();
                case NETHER -> settings.netherName();
                case END -> settings.endName();
                default -> null;
            };
            if (name != null && !name.isBlank()) {
                want.put(base + "display-name",
                        new Want(name, new Change("map-setup-change-name", "world", world.getName(), "name", name)));
            }
            if (key.equals(NETHER)) {
                // Without this the nether map only shows the bedrock roof.
                want.put(base + "iterate-up", new Want(true, new Change("map-setup-change-nether", "world", world.getName())));
                want.put(base + "max-height", new Want(127, null));
            }
        }
        return want;
    }

    /**
     * What version 1 wrote (once, without remembering it): the same defaults, a name for every world by its
     * environment - also for other worlds of that kind, which is why those names are taken back now.
     */
    private Map<String, Object> legacyWritten() {
        Settings.SquaremapSettings settings = plugin.settings().squaremap();
        Map<String, Object> written = new LinkedHashMap<>();
        written.put("world-settings.default.map.render.background.interval-seconds", 5);
        written.put("world-settings.default.player-tracker.enabled", false);
        written.put("settings.ui.sidebar.pinned", "pinned");
        for (World world : Bukkit.getWorlds()) {
            String base = "world-settings." + world.getKey().asString() + ".map.";
            String name = switch (world.getEnvironment()) {
                case NORMAL -> settings.overworldName();
                case NETHER -> settings.netherName();
                case THE_END -> settings.endName();
                default -> null;
            };
            if (name != null && !name.isBlank()) {
                written.put(base + "display-name", name);
            }
            if (world.getEnvironment() == World.Environment.NETHER) {
                written.put(base + "iterate-up", true);
                written.put(base + "max-height", 127);
            }
        }
        return written;
    }

    private Map<String, Object> written() {
        YamlConfiguration done = YamlConfiguration.loadConfiguration(marker);
        int version = done.getInt("applied-version", 0);
        if (version == 0) {
            return Map.of();
        }
        if (version == 1) {
            return legacyWritten();
        }
        Map<String, Object> written = new LinkedHashMap<>();
        // A list instead of keys: squaremap paths contain dots and colons.
        for (Map<?, ?> entry : done.getMapList("written")) {
            if (entry.get("path") instanceof String path) {
                written.put(path, entry.get("value"));
            }
        }
        return written;
    }

    /**
     * Brings squaremap's config up to date and remembers what was written.
     * @param force also overwrite values the server owner changed (/mrchunks map setup)
     * @return the changes made (for the log / the admin), empty if nothing changed or squaremap has no config yet
     */
    List<Change> apply(boolean force) throws IOException {
        List<Change> changes = new ArrayList<>();
        if (!squaremapConfig.exists()) {
            return changes;
        }
        YamlConfiguration config = new YamlConfiguration();
        config.options().parseComments(true);
        config.options().width(10_000);
        try {
            config.load(squaremapConfig);
        } catch (InvalidConfigurationException e) {
            throw new IOException("squaremap/config.yml is invalid: " + e.getMessage(), e);
        }
        Map<String, Object> before = written();
        // Never set up before: squaremap's own defaults are in the file - those are not changes by hand.
        boolean first = YamlConfiguration.loadConfiguration(marker).getInt("applied-version", 0) == 0;
        Map<String, Object> now = new LinkedHashMap<>();
        boolean dirty = false;

        for (Map.Entry<String, Want> e : wanted().entrySet()) {
            String path = e.getKey();
            Want want = e.getValue();
            Object current = config.get(path);
            if (same(want.value(), current)) {
                now.put(path, want.value());
                continue;
            }
            boolean ours = current == null || (before.containsKey(path) && same(before.get(path), current));
            if (!ours && !force && !first) {
                continue; // changed by hand - stays
            }
            config.set(path, want.value());
            now.put(path, want.value());
            dirty = true;
            if (want.label() != null) {
                changes.add(want.label());
            }
        }
        // No longer recommended (e.g. a name version 1 gave every world): remove it, unless it was changed by hand.
        for (Map.Entry<String, Object> e : before.entrySet()) {
            String path = e.getKey();
            if (!now.containsKey(path) && config.get(path) != null && same(e.getValue(), config.get(path))) {
                config.set(path, null);
                dirty = true;
                changes.add(new Change("map-setup-change-removed", "setting", path.replace("world-settings.", "")));
            }
        }

        if (dirty) {
            config.save(squaremapConfig);
        }
        YamlConfiguration done = new YamlConfiguration();
        done.options().setHeader(List.of("What MRchunks wrote into squaremap/config.yml - values changed there by hand stay."));
        done.set("applied-version", VERSION);
        done.set("applied-at", System.currentTimeMillis());
        List<Map<String, Object>> list = new ArrayList<>();
        now.forEach((path, value) -> list.add(Map.of("path", path, "value", value)));
        done.set("written", list);
        done.save(marker);
        return changes;
    }

    /** YAML numbers come back as Integer, Long or Double - compare by text. */
    private static boolean same(Object a, Object b) {
        return Objects.equals(a, b) || (a != null && b != null && String.valueOf(a).equals(String.valueOf(b)));
    }

    /** One applied setting: language key (map-setup-change-*) and its placeholders as name/value pairs. */
    public record Change(String key, Object... placeholders) {
    }
}
