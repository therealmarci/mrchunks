package at.marcelr.mrchunks.util;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Which language file fits a language tag - a player's Minecraft language ("de_at") or a config value ("deutsch").
 * Must treat languages like the client mod does: it has de_de, de_at, de_ch and en_us, and Minecraft falls back to
 * en_us for everything else.
 */
public final class LanguageChoice {

    private static final Map<String, String> ALIASES = Map.of(
            "eng", "en", "english", "en", "englisch", "en",
            "deu", "de", "ger", "de", "german", "de", "deutsch", "de");

    private LanguageChoice() {
    }

    /**
     * Finds a language for a tag: exact name ("de_at"), aliases (eng, deutsch ...), the part before _ or - ("de").
     *
     * @param available codes of the language files
     * @param otherwise result if none fits
     */
    public static String resolve(Set<String> available, String tag, String otherwise) {
        if (tag == null || tag.isBlank()) {
            return otherwise;
        }
        String code = tag.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (available.contains(code)) {
            return code;
        }
        String alias = ALIASES.get(code);
        if (alias != null && available.contains(alias)) {
            return alias;
        }
        String base = code.split("_", 2)[0];
        base = ALIASES.getOrDefault(base, base);
        return available.contains(base) ? base : otherwise;
    }
}
