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
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;
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

    /** Version of the bundled language files (lang-version); raise it together with {@link #CHANGED_TEXTS}. */
    public static final int LANG_VERSION = 2;
    /**
     * Texts whose default changed, per lang-version. Older bundled-language files get these texts renewed;
     * new texts are added anyway. Example: v2 added the Minecraft version to the version display.
     */
    private static final Map<Integer, Set<String>> CHANGED_TEXTS = new TreeMap<>(Map.of(
            2, Set.of("version", "gui-info-name")));

    private final MiniMessage mini = MiniMessage.miniMessage();
    /** Set while texts for the console are built (see {@link #as}). */
    private static final ThreadLocal<Boolean> CONSOLE = ThreadLocal.withInitial(() -> false);
    private volatile YamlConfiguration lang = new YamlConfiguration();
    /** The bundled English texts - the console is always English, whatever language players get. */
    private volatile YamlConfiguration english = new YamlConfiguration();
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
            plugin.getLogger().warning("Language file lang/" + code + ".yml not found, using "
                    + BUNDLED.getFirst() + ".");
            code = BUNDLED.getFirst();
            file = new File(dir, code + ".yml");
        }

        YamlConfiguration loaded = new YamlConfiguration();
        try {
            loaded.loadFromString(ConfigUpdater.stripBom(Files.readString(file.toPath(), StandardCharsets.UTF_8)));
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().log(Level.SEVERE, "lang/" + code + ".yml is invalid, using default texts: "
                    + e.getMessage());
        }
        YamlConfiguration defaults = bundled(plugin, BUNDLED.contains(code) ? code : BUNDLED.getFirst());
        int fileVersion = loaded.getInt("lang-version", 1);
        int added = 0;
        int replaced = 0;
        // Texts whose default changed: renew them in the bundled languages (a custom language keeps its own).
        if (BUNDLED.contains(code) && fileVersion < LANG_VERSION) {
            for (Map.Entry<Integer, Set<String>> update : CHANGED_TEXTS.entrySet()) {
                if (update.getKey() <= fileVersion) {
                    continue;
                }
                for (String key : update.getValue()) {
                    if (defaults.contains(key)) {
                        loaded.set(key, defaults.get(key));
                        replaced++;
                    }
                }
            }
        }
        for (String key : defaults.getKeys(true)) {
            if (!defaults.isConfigurationSection(key) && !loaded.contains(key, true)) {
                loaded.set(key, defaults.get(key));
                added++;
            }
        }
        if (fileVersion < LANG_VERSION) {
            loaded.set("lang-version", LANG_VERSION);
        }
        if (added > 0 || fileVersion < LANG_VERSION) {
            try {
                loaded.options().width(10_000);
                Files.writeString(file.toPath(), ConfigUpdater.BOM + loaded.saveToString(), StandardCharsets.UTF_8);
                plugin.getLogger().info("lang/" + code + ".yml: added " + added + " new texts, updated " + replaced
                        + " changed texts (lang-version " + fileVersion + " -> " + LANG_VERSION + ").");
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Could not update lang/" + code + ".yml: " + e.getMessage());
            }
        }
        language = code;
        english = code.equals("en") ? defaults : bundled(plugin, "en");
        lang = loaded;
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
            plugin.getLogger().log(Level.WARNING, "Bundled language file " + code + " is invalid: " + e.getMessage());
        }
        return config;
    }

    /** The active language code, e.g. "de". */
    public String language() {
        return language;
    }

    /** Texts for the current recipient: English while something is sent to the console, otherwise the language file. */
    private YamlConfiguration texts() {
        return CONSOLE.get() ? english : lang;
    }

    /**
     * Runs {@code action} with English texts if {@code sender} is not a player (console, command block ...).
     * Commands use this so that every value they build - not just the final message - is English in the console.
     */
    public <T> T as(CommandSender sender, Supplier<T> action) {
        if (sender instanceof Player || CONSOLE.get()) {
            return action.get();
        }
        CONSOLE.set(true);
        try {
            return action.get();
        } finally {
            CONSOLE.set(false);
        }
    }

    /** @return the raw message, or null if it is configured as empty */
    private String raw(String key) {
        String value = texts().getString(key);
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
        if (texts().isList(key)) {
            return texts().getStringList(key);
        }
        String value = texts().getString(key);
        return value == null || value.isEmpty() ? List.of() : List.of(value.split("\n"));
    }

    /** The bundled English text with simple {@code <name>} replacements, for console output. */
    public String console(String key, Object... placeholders) {
        String text = english.getString(key, key);
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            text = text.replace("<" + placeholders[i] + ">", String.valueOf(placeholders[i + 1]));
        }
        return text;
    }

    /** Raw text or "" if it is missing or empty (no "missing text" marker). */
    public String optional(String key) {
        return texts().getString(key, "");
    }

    private TagResolver resolvers(TagResolver... resolvers) {
        return TagResolver.resolver(TagResolver.resolver(resolvers), Placeholder.parsed("prefix", texts().getString("prefix", "")));
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
        if (texts().isList(key)) {
            lines = texts().getStringList(key);
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
        as(target, () -> {
            if (raw(key) != null) {
                target.sendMessage(get(key, resolvers));
            }
            return null;
        });
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
        String pattern = texts().getString(count == 1 ? "nights-singular" : "nights-plural");
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
