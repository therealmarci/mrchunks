package at.marcelr.mrchunks;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * Brings an existing config.yml up to the layout of the bundled one: the bundled file (with its comments and order)
 * is taken as template and every value the server owner had set is carried over. The old file is kept as backup.
 * <p>
 * Files are always written as UTF-8 <b>with BOM</b>, so editors like Notepad++, WinSCP or FileZilla
 * recognise the encoding and show umlauts correctly.
 */
public final class ConfigUpdater {

    public static final int CURRENT_VERSION = 16;
    public static final String BOM = "\uFEFF";
    /** Texts whose content changed with new features; the new default replaces the old value. */
    private static final Set<String> REPLACED_KEYS = Set.of();
    /** Keys that were renamed: old path -> new path (the value is carried over). */
    private static final Map<String, String> RENAMED_KEYS = Map.of(
            // v3
            "squaremap.public-url", "squaremap.address");

    private ConfigUpdater() {
    }

    public static void update(MRChunks plugin) {
        File file = new File(plugin.getDataFolder(), "config.yml");
        if (!file.exists()) {
            plugin.saveDefaultConfig();
            return;
        }
        try {
            String current = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            YamlConfiguration existing = new YamlConfiguration();
            existing.loadFromString(stripBom(current));
            int version = existing.getInt("config-version", 1);
            if (version >= CURRENT_VERSION) {
                if (!current.startsWith(BOM)) {
                    // Same content, only add the BOM so editors detect UTF-8.
                    Files.writeString(file.toPath(), BOM + current, StandardCharsets.UTF_8);
                }
                return;
            }

            YamlConfiguration template = new YamlConfiguration();
            try (InputStream in = plugin.getResource("config.yml")) {
                if (in == null) {
                    return;
                }
                template.loadFromString(stripBom(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
            }

            int carried = 0;
            for (String oldKey : existing.getKeys(true)) {
                String key = RENAMED_KEYS.getOrDefault(oldKey, oldKey);
                if (existing.isConfigurationSection(oldKey) || key.equals("config-version")
                        || REPLACED_KEYS.contains(key) || !template.contains(key)) {
                    continue;
                }
                template.set(key, existing.get(oldKey));
                carried++;
            }
            // v10: every player gets the language of their game; a fixed language so far stays for Discord & co.
            if (version < 10) {
                String old = existing.getString("language", "auto").trim();
                if (!old.isEmpty() && !old.equalsIgnoreCase("auto")) {
                    template.set("server-language", old);
                }
                template.set("language", "auto");
            }
            // v12: chunk budget instead of fixed limits. Existing loaders keep their size (old radius = default-reach),
            // nobody gets less: the budget is the old active limit in loaders of the old size, and the lag limits keep
            // applying to the old area.
            if (version < 12 && existing.contains("loaders.radius")) {
                int radius = Math.clamp(existing.getInt("loaders.radius", 3), 0, 10);
                int area = (2 * radius + 1) * (2 * radius + 1);
                template.set("loaders.default-reach", radius);
                template.set("loaders.max-reach", Math.max(radius, template.getInt("loaders.max-reach", 7)));
                template.set("loaders.default-budget", Math.max(0, existing.getInt("loaders.default-active-limit", 3)) * area);
                template.set("lag-protection.limits-per-chunks", area);
            }
            // v16: the map colours show the state (active green, conspicuous and paused yellow) - only the old defaults
            // are replaced, own colours stay.
            if (version < 16) {
                replaceDefault(existing, template, "squaremap.color", "owner", "status");
                replaceDefault(existing, template, "squaremap.paused-color", "#F39C12", "#F1C40F");
                replaceDefault(existing, template, "squaremap.warn-color", "#E67E22", "#F1C40F");
            }
            template.set("config-version", CURRENT_VERSION);
            // Keep long messages on one line instead of folding them.
            template.options().width(10_000);

            File backup = new File(plugin.getDataFolder(), "config.v" + version + ".backup.yml");
            Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(file.toPath(), BOM + template.saveToString(), StandardCharsets.UTF_8);
            plugin.getLogger().info("Updated config.yml from version " + version + " to " + CURRENT_VERSION + " ("
                    + carried + " settings carried over, backup: " + backup.getName() + ").");
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().log(Level.WARNING, "Could not update config.yml: " + e.getMessage());
        }
    }

    /** Sets {@code value} if the old file still had the old default {@code old} (any other value was carried over). */
    private static void replaceDefault(YamlConfiguration existing, YamlConfiguration template, String key, String old,
                                       String value) {
        if (old.equalsIgnoreCase(existing.getString(key, old).trim())) {
            template.set(key, value);
        }
    }

    /** Loads config.yml (tolerating a BOM) with the bundled config as defaults for missing keys. */
    public static YamlConfiguration load(MRChunks plugin) {
        YamlConfiguration config = new YamlConfiguration();
        File file = new File(plugin.getDataFolder(), "config.yml");
        try {
            if (file.exists()) {
                config.loadFromString(stripBom(Files.readString(file.toPath(), StandardCharsets.UTF_8)));
            }
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().log(Level.SEVERE, "config.yml is invalid, using defaults: " + e.getMessage());
        }
        try (InputStream in = plugin.getResource("config.yml")) {
            if (in != null) {
                YamlConfiguration defaults = new YamlConfiguration();
                defaults.loadFromString(stripBom(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
                config.setDefaults(defaults);
                // Fill in values missing from the file. Bukkit ignores the defaults whenever code passes its own
                // fallback (getString(path, "...")), so missing keys would otherwise get those fallbacks.
                for (String key : defaults.getKeys(true)) {
                    if (!defaults.isConfigurationSection(key) && !config.isSet(key)) {
                        config.set(key, defaults.get(key));
                    }
                }
            }
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().log(Level.WARNING, "Could not read the bundled config: " + e.getMessage());
        }
        return config;
    }

    public static String stripBom(String text) {
        return text.startsWith(BOM) ? text.substring(1) : text;
    }
}
