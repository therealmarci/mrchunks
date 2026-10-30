package at.marcelr.mrchunks;

import at.marcelr.mrchunks.util.LanguageChoice;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
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
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * All texts of the plugin, read from {@code lang/<language>.yml}. Every player gets the language of their game
 * (de -> de.yml, no file -> fallback) unless the config fixes one; the console is always English and texts without a
 * player recipient (Discord ...) use the server language. The bundled languages are copied into the plugin folder;
 * texts missing from a file are added from the bundled defaults.
 */
public final class Messages {

    /** Languages shipped with the plugin; the first one is the fallback. */
    public static final List<String> BUNDLED = List.of("en", "de");

    /** Version of the bundled language files (lang-version in de.yml/en.yml). */
    private final int langVersion;
    /** Texts whose default changed, per lang-version; older bundled-language files get them renewed. */
    private final Map<Integer, Set<String>> changedTexts;

    private final MiniMessage mini = MiniMessage.miniMessage();

    /**
     * @param langVersion  lang-version of the bundled files; raise it whenever an existing default text changes
     * @param changedTexts per lang-version the keys whose default changed (new keys are added anyway)
     */
    public Messages(int langVersion, Map<Integer, Set<String>> changedTexts) {
        this.langVersion = langVersion;
        this.changedTexts = new TreeMap<>(changedTexts);
    }
    /** Texts of the recipient whose message is being built right now (see {@link #as}), or null. */
    private static final ThreadLocal<YamlConfiguration> CURRENT = new ThreadLocal<>();
    /** All language files of the lang folder by code ("de", "en", "fr" ...). */
    private volatile Map<String, YamlConfiguration> languages = Map.of();
    /** Code of the fixed language, or null if every player gets the language of their game. */
    private volatile String fixed;
    /** Language for players whose game language has no file. */
    private volatile String fallbackCode = BUNDLED.getFirst();
    /** Language of texts without a player recipient (Discord ...). */
    private volatile String serverCode = BUNDLED.getFirst();
    /** The English texts - the console is always English, whatever language players get. */
    private volatile YamlConfiguration english = new YamlConfiguration();

    /**
     * Loads all language files of the lang folder.
     *
     * @param setting  "auto" = every player gets the language of their game (Minecraft setting), otherwise a fixed
     *                 language for everyone ("de", "en", "deutsch" ...)
     * @param fallback language for players whose game language has no file
     * @param server   language of texts without a player recipient (Discord ...)
     */
    public void load(JavaPlugin plugin, String setting, String fallback, String server) {
        File dir = new File(plugin.getDataFolder(), "lang");
        for (String code : BUNDLED) {
            if (!new File(dir, code + ".yml").exists()) {
                plugin.saveResource("lang/" + code + ".yml", false);
            }
        }
        Map<String, YamlConfiguration> loaded = new TreeMap<>();
        File[] files = dir.listFiles((parent, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        for (File file : files == null ? new File[0] : files) {
            String code = file.getName().substring(0, file.getName().length() - 4).toLowerCase(Locale.ROOT);
            loaded.put(code, loadFile(plugin, file, code));
        }
        for (String code : BUNDLED) {
            loaded.computeIfAbsent(code, missing -> bundled(plugin, missing));
        }
        languages = Map.copyOf(loaded);
        english = loaded.get("en");

        fallbackCode = LanguageChoice.resolve(loaded.keySet(), fallback, BUNDLED.getFirst());
        serverCode = LanguageChoice.resolve(loaded.keySet(), server, fallbackCode);
        boolean auto = setting == null || setting.isBlank() || setting.trim().equalsIgnoreCase("auto");
        fixed = auto ? null : LanguageChoice.resolve(loaded.keySet(), setting, fallbackCode);
    }

    /** Reads one language file and brings it up to date with the bundled defaults (saved if something changed). */
    private YamlConfiguration loadFile(JavaPlugin plugin, File file, String code) {
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
        if (BUNDLED.contains(code) && fileVersion < langVersion) {
            for (Map.Entry<Integer, Set<String>> update : changedTexts.entrySet()) {
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
        if (fileVersion < langVersion) {
            loaded.set("lang-version", langVersion);
        }
        if (added > 0 || fileVersion < langVersion) {
            try {
                loaded.options().width(10_000);
                Files.writeString(file.toPath(), ConfigUpdater.BOM + loaded.saveToString(), StandardCharsets.UTF_8);
                plugin.getLogger().info("lang/" + code + ".yml: added " + added + " new texts, updated " + replaced
                        + " changed texts (lang-version " + fileVersion + " -> " + langVersion + ").");
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Could not update lang/" + code + ".yml: " + e.getMessage());
            }
        }
        return loaded;
    }

    /**
     * The language a player gets: the fixed one from the config, or the language of their game ("de_at" -> de.yml,
     * a language without file -> the fallback).
     */
    public String languageOf(Player player) {
        if (fixed != null) {
            return fixed;
        }
        return LanguageChoice.resolve(languages.keySet(), player.locale().toString(), fallbackCode);
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

    /** Texts for the current recipient (see {@link #as}); outside of that the fixed or the server language. */
    private YamlConfiguration texts() {
        YamlConfiguration current = CURRENT.get();
        if (current != null) {
            return current;
        }
        YamlConfiguration texts = languages.get(fixed != null ? fixed : serverCode);
        return texts != null ? texts : english;
    }

    /**
     * Runs {@code action} with the texts of {@code recipient}: their own language for a player, English for the
     * console (and command blocks ...). Everything that builds texts for someone runs inside this - not just the final
     * message - so menus, item names and placeholders are all in the same language.
     */
    public <T> T as(CommandSender recipient, Supplier<T> action) {
        YamlConfiguration target = recipient instanceof Player player
                ? languages.getOrDefault(languageOf(player), english)
                : english;
        YamlConfiguration previous = CURRENT.get();
        CURRENT.set(target);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    /** Like {@link #as(CommandSender, Supplier)} for actions without result. */
    public void run(CommandSender recipient, Runnable action) {
        as(recipient, () -> {
            action.run();
            return null;
        });
    }

    /**
     * Runs {@code action} with the server language - for texts without a player recipient (Discord ...) that are
     * built while a command or menu of some player is running.
     */
    public <T> T server(Supplier<T> action) {
        YamlConfiguration target = languages.getOrDefault(fixed != null ? fixed : serverCode, english);
        YamlConfiguration previous = CURRENT.get();
        CURRENT.set(target);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    /**
     * Runs {@code action} with the texts of one language ("de", "en" ...) - e.g. the language a player chose on their
     * page in MRadmin's web interface. Unknown codes get English.
     */
    public <T> T in(String code, Supplier<T> action) {
        YamlConfiguration target = languages.getOrDefault(code, english);
        YamlConfiguration previous = CURRENT.get();
        CURRENT.set(target);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    /** Runs {@code action} once per recipient, each time in the recipient's language (see {@link #as}). */
    public void each(Iterable<? extends CommandSender> recipients, Consumer<CommandSender> action) {
        for (CommandSender recipient : recipients) {
            run(recipient, () -> action.accept(recipient));
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
        run(target, () -> {
            if (raw(key) != null) {
                Component message = get(key, resolvers);
                target.sendMessage(target instanceof org.bukkit.entity.Player ? message : unfoldHovers(message));
            }
        });
    }

    /** The console cannot hover: the text of each hover is appended in brackets instead (nothing gets lost). */
    static Component unfoldHovers(Component component) {
        List<Component> children = new ArrayList<>();
        for (Component child : component.children()) {
            children.add(unfoldHovers(child));
        }
        Component result = component.children(children);
        HoverEvent<?> hover = result.hoverEvent();
        if (hover != null && hover.action() == HoverEvent.Action.SHOW_TEXT) {
            result = result.hoverEvent(null).append(Component.text(" (")).append((Component) hover.value())
                    .append(Component.text(")"));
        }
        return result;
    }

    /** Sends the message to everyone in the world, each player in their own language. */
    public void broadcast(World world, String key, TagResolver... resolvers) {
        each(world.getPlayers(), player -> send(player, key, resolvers));
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

    /**
     * Placeholder whose value (text or component) is only built when the message is built - so a placeholder made
     * for one player and sent to another is still in the recipient's language.
     */
    public static TagResolver lazy(String name, Supplier<?> value) {
        return TagResolver.resolver(name, (arguments, context) -> {
            Object result = value.get();
            return Tag.selfClosingInserting(result instanceof Component component ? component
                    : Component.text(String.valueOf(result)));
        });
    }
}
