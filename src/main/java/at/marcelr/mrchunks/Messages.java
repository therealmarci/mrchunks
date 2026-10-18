package at.marcelr.mrchunks;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * All texts of the plugin, read from {@code lang/<language>.yml}. The bundled languages are copied into the plugin
 * folder; texts missing from a file (e.g. after an update) are added from the bundled defaults.
 */
public final class Messages {

    /** Languages shipped with the plugin; the first one is the fallback. */
    public static final List<String> BUNDLED = List.of("en", "de");
    private static final Map<String, String> ALIASES = Map.of(
            "eng", "en", "english", "en", "englisch", "en",
            "deu", "de", "ger", "de", "german", "de", "deutsch", "de");

    private final MiniMessage mini = MiniMessage.miniMessage();
    private volatile YamlConfiguration lang = new YamlConfiguration();
    private String prefix = "";
    private String language = BUNDLED.getFirst();

    /** Loads the language file for the given tag (e.g. "de", "en", "eng", "de_DE"). */
    public void load(JavaPlugin plugin, String tag) {
        File dir = new File(plugin.getDataFolder(), "lang");
        for (String code : BUNDLED) {
            if (!new File(dir, code + ".yml").exists()) {
                plugin.saveResource("lang/" + code + ".yml", false);
            }
        }
        String code = resolve(dir, tag);
        File file = new File(dir, code + ".yml");
        if (!file.exists()) {
            plugin.getLogger().warning("Sprachdatei lang/" + code + ".yml nicht gefunden, verwende "
                    + BUNDLED.getFirst() + ".");
            code = BUNDLED.getFirst();
            file = new File(dir, code + ".yml");
        }

        YamlConfiguration loaded = new YamlConfiguration();
        try {
            loaded.loadFromString(ConfigUpdater.stripBom(Files.readString(file.toPath(), StandardCharsets.UTF_8)));
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().log(Level.SEVERE, "lang/" + code + ".yml ist fehlerhaft, verwende Standardtexte: "
                    + e.getMessage());
        }
        YamlConfiguration defaults = bundled(plugin, BUNDLED.contains(code) ? code : BUNDLED.getFirst());
        int added = 0;
        for (String key : defaults.getKeys(true)) {
            if (!defaults.isConfigurationSection(key) && !loaded.contains(key, true)) {
                loaded.set(key, defaults.get(key));
                added++;
            }
        }
        if (added > 0) {
            try {
                loaded.options().width(10_000);
                Files.writeString(file.toPath(), ConfigUpdater.BOM + loaded.saveToString(), StandardCharsets.UTF_8);
                plugin.getLogger().info("lang/" + code + ".yml: " + added + " neue Texte ergänzt.");
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "lang/" + code + ".yml konnte nicht ergänzt werden: " + e.getMessage());
            }
        }
        language = code;
        lang = loaded;
        prefix = loaded.getString("prefix", "");
    }

    /** Finds the file for a tag: exact name first, then aliases (eng, deutsch ...), then the part before _ or -. */
    private static String resolve(File dir, String tag) {
        String code = tag == null || tag.isBlank() ? BUNDLED.getFirst() : tag.trim().toLowerCase(Locale.ROOT);
        if (new File(dir, code + ".yml").exists()) {
            return code;
        }
        if (ALIASES.containsKey(code)) {
            return ALIASES.get(code);
        }
        String base = code.split("[_-]", 2)[0];
        return ALIASES.getOrDefault(base, base);
    }

    /** The bundled language file, used to fill in missing texts. */
    public static YamlConfiguration bundled(JavaPlugin plugin, String code) {
        YamlConfiguration config = new YamlConfiguration();
        try (InputStream in = plugin.getResource("lang/" + code + ".yml")) {
            if (in != null) {
                config.loadFromString(ConfigUpdater.stripBom(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
            }
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().log(Level.WARNING, "Mitgelieferte Sprachdatei " + code + " fehlerhaft: " + e.getMessage());
        }
        return config;
    }

    /** The active language code, e.g. "de". */
    public String language() {
        return language;
    }

    /** @return the raw message, or null if it is configured as empty */
    private String raw(String key) {
        String value = lang.getString(key);
        if (value == null) {
            return "<red>Missing text: " + key;
        }
        return value.isEmpty() ? null : value;
    }

    public String plain(String key) {
        String value = raw(key);
        return value == null ? "" : value;
    }

    /** Raw text with simple {@code <name>} replacements, e.g. for Discord or console output. */
    public String plain(String key, Object... placeholders) {
        String text = plain(key);
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            text = text.replace("<" + placeholders[i] + ">", String.valueOf(placeholders[i + 1]));
        }
        return text;
    }

    /** A list of texts (e.g. item lore); a single text becomes a one-line list. */
    public List<String> list(String key) {
        if (lang.isList(key)) {
            return lang.getStringList(key);
        }
        String value = lang.getString(key);
        return value == null || value.isEmpty() ? List.of() : List.of(value.split("\n"));
    }

    /** Raw text or "" if it is missing or empty (no "missing text" marker). */
    public String optional(String key) {
        return lang.getString(key, "");
    }

    private TagResolver resolvers(TagResolver... resolvers) {
        return TagResolver.resolver(TagResolver.resolver(resolvers), Placeholder.parsed("prefix", prefix));
    }

    public Component get(String key, TagResolver... resolvers) {
        String value = raw(key);
        if (value == null) {
            return Component.empty();
        }
        return mini.deserialize(value, resolvers(resolvers));
    }

    /** Like {@link #get} but without the default italic style Minecraft applies to item names and lore. */
    public Component item(String key, TagResolver... resolvers) {
        return get(key, resolvers).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** Item lore from a string list (or a single string with line breaks). */
    public List<Component> lore(String key, TagResolver... resolvers) {
        List<String> lines;
        if (lang.isList(key)) {
            lines = lang.getStringList(key);
        } else {
            String value = raw(key);
            lines = value == null ? List.of() : List.of(value.split("\n"));
        }
        TagResolver resolver = resolvers(resolvers);
        List<Component> result = new ArrayList<>();
        for (String line : lines) {
            result.add(mini.deserialize(line, resolver).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        }
        return result;
    }

    public void send(CommandSender target, String key, TagResolver... resolvers) {
        if (raw(key) != null) {
            target.sendMessage(get(key, resolvers));
        }
    }

    public void broadcast(World world, String key, TagResolver... resolvers) {
        if (raw(key) == null) {
            return;
        }
        Component message = get(key, resolvers);
        for (Player player : world.getPlayers()) {
            player.sendMessage(message);
        }
    }

    /** Formats a number of nights, e.g. "1 night" / "3 nights", using nights-singular / nights-plural. */
    public String nights(int count) {
        String pattern = lang.getString(count == 1 ? "nights-singular" : "nights-plural");
        if (pattern == null) {
            pattern = count == 1 ? "1 night" : "<count> nights";
        }
        return pattern.replace("<count>", String.valueOf(count));
    }

    /** Plain-text placeholder (cannot inject formatting). */
    public static TagResolver tag(String name, Object value) {
        return Placeholder.unparsed(name, String.valueOf(value));
    }

    /** Placeholder with a formatted component as value. */
    public static TagResolver tag(String name, Component value) {
        return Placeholder.component(name, value);
    }
}
