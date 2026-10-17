package at.marcelr.mrchunks;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class Messages {

    private final MiniMessage mini = MiniMessage.miniMessage();
    private ConfigurationSection section;
    private String prefix = "";

    public void load(FileConfiguration config) {
        section = config.getConfigurationSection("messages");
        prefix = section == null ? "" : section.getString("prefix", "");
    }

    /** @return the raw message, or null if it is configured as empty */
    private String raw(String key) {
        String value = section == null ? null : section.getString(key);
        if (value == null) {
            return "<red>Fehlende Nachricht: " + key;
        }
        return value.isEmpty() ? null : value;
    }

    public String plain(String key) {
        String value = raw(key);
        return value == null ? "" : value;
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
        if (section != null && section.isList(key)) {
            lines = section.getStringList(key);
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

    /** Formats a number of nights, e.g. "1 Nacht" / "3 Nächte", using nights-singular / nights-plural. */
    public String nights(int count) {
        String pattern = section == null ? null
                : section.getString(count == 1 ? "nights-singular" : "nights-plural");
        if (pattern == null) {
            pattern = count == 1 ? "1 Nacht" : "<count> Nächte";
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
