package at.marcelr.mrchunks;

import at.marcelr.mrchunks.util.DurationUtil;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static at.marcelr.mrchunks.Messages.tag;

/** Placeholders shared by all messages and menu items that show a loader or a ban. */
public final class Texts {

    private static final String DEFAULT_DATE = "dd.MM.yyyy HH:mm";

    private Texts() {
    }

    /** Date format from the language file (date-format), e.g. "dd.MM.yyyy HH:mm" or "yyyy-MM-dd HH:mm". */
    private static String date(Messages messages, long millis) {
        String pattern = messages.optional("date-format");
        DateTimeFormatter format;
        try {
            format = DateTimeFormatter.ofPattern(pattern.isBlank() ? DEFAULT_DATE : pattern);
        } catch (IllegalArgumentException e) {
            format = DateTimeFormatter.ofPattern(DEFAULT_DATE);
        }
        return format.withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(millis));
    }

    /** id, player (owner), world, x, y, z, cx, cz, size, chunks, created, state */
    public static TagResolver loader(MRChunks plugin, Loader loader) {
        Settings.LoaderSettings settings = plugin.settings().loaders();
        Messages messages = plugin.messages();
        return TagResolver.resolver(
                tag("id", loader.id()),
                tag("player", LoaderManager.name(loader.owner())),
                tag("world", loader.worldName()),
                tag("x", loader.x()),
                tag("y", loader.y()),
                tag("z", loader.z()),
                tag("cx", loader.chunkX()),
                tag("cz", loader.chunkZ()),
                tag("size", settings.size()),
                tag("chunks", settings.chunkCount()),
                tag("created", date(messages, loader.created())),
                tag("state", messages.get(loader.active() ? "gui-state-active" : "gui-state-inactive")));
    }

    /** Plain state word for chat/Discord ("aktiv" / "deaktiviert"). */
    public static String state(MRChunks plugin, boolean active) {
        return plugin.messages().plain(active ? "state-active" : "state-inactive");
    }

    /** "dauerhaft" or "noch 2h 5m". */
    public static String banDuration(MRChunks plugin, LoaderManager.Ban ban) {
        Messages messages = plugin.messages();
        if (ban == null || ban.permanent()) {
            return messages.plain("ban-duration-permanent");
        }
        return messages.plain("ban-duration-temporary").replace("<time>", DurationUtil.format(ban.remaining()));
    }

    /** Limit as text, "∞" for unlimited. */
    public static String limit(LoaderManager.Limit limit) {
        return limit.value() == Integer.MAX_VALUE ? "∞" : String.valueOf(limit.value());
    }
}
