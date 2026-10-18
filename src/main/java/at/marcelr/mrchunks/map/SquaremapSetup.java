package at.marcelr.mrchunks.map;

import at.marcelr.mrchunks.MRChunks;
import at.marcelr.mrchunks.Messages;
import at.marcelr.mrchunks.Settings;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes the recommended settings into squaremap's config.yml - once per {@link #VERSION}, so changes a server
 * owner makes afterwards are kept. Only touches the file; needs no squaremap classes.
 */
final class SquaremapSetup {

    /** Raise when the recommended settings change, so existing installs get the new ones once. */
    static final int VERSION = 1;

    private final MRChunks plugin;
    private final File squaremapConfig;
    private final File marker;

    SquaremapSetup(MRChunks plugin) {
        this.plugin = plugin;
        this.squaremapConfig = new File(plugin.getDataFolder().getParentFile(), "squaremap/config.yml");
        this.marker = new File(plugin.getDataFolder(), "squaremap-setup.yml");
    }

    /** True if the automatic setup is enabled and has not run for the current {@link #VERSION} yet. */
    boolean pending() {
        if (!plugin.settings().squaremap().autoConfigure()) {
            return false;
        }
        return YamlConfiguration.loadConfiguration(marker).getInt("applied-version", 0) < VERSION;
    }

    /**
     * Applies the settings and records that it happened.
     * @return the changes made (for the log / the admin), empty if the squaremap config does not exist yet
     */
    List<String> apply() throws IOException {
        List<String> changes = new ArrayList<>();
        if (!squaremapConfig.exists()) {
            return changes;
        }
        Settings.SquaremapSettings settings = plugin.settings().squaremap();
        YamlConfiguration config = YamlConfiguration.loadConfiguration(squaremapConfig);
        Messages texts = plugin.messages();

        set(config, changes, "world-settings.default.map.render.background.interval-seconds", 5,
                texts.plain("map-setup-change-interval"));
        set(config, changes, "world-settings.default.player-tracker.enabled", false,
                texts.plain("map-setup-change-tracker"));
        set(config, changes, "settings.ui.sidebar.pinned", "pinned",
                texts.plain("map-setup-change-sidebar"));

        for (World world : Bukkit.getWorlds()) {
            String base = "world-settings." + world.getKey().asString() + ".map.";
            String name = switch (world.getEnvironment()) {
                case NORMAL -> settings.overworldName();
                case NETHER -> settings.netherName();
                case THE_END -> settings.endName();
                default -> null;
            };
            if (name != null && !name.isBlank()) {
                set(config, changes, base + "display-name", name, texts.plain("map-setup-change-name", "world", world.getName(), "name", name));
            }
            if (world.getEnvironment() == World.Environment.NETHER) {
                // Without this the nether map only shows the bedrock roof.
                set(config, changes, base + "iterate-up", true, texts.plain("map-setup-change-nether", "world", world.getName()));
                set(config, changes, base + "max-height", 127, null);
            }
        }

        config.save(squaremapConfig);
        YamlConfiguration done = new YamlConfiguration();
        done.set("applied-version", VERSION);
        done.set("applied-at", System.currentTimeMillis());
        done.save(marker);
        return changes;
    }

    private static void set(YamlConfiguration config, List<String> changes, String path, Object value, String label) {
        if (!value.equals(config.get(path))) {
            config.set(path, value);
            if (label != null) {
                changes.add(label);
            }
        }
    }
}
