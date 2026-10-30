package at.marcelr.mrchunks;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** The bundled language files must offer the same texts, otherwise a language silently falls back to defaults. */
class LanguageFilesTest {

    private static final Pattern KEY = Pattern.compile("^( {0,2})([a-z0-9-]+):");
    /** YAML 1.1 reads these keys as booleans, so they would never be found. */
    private static final Set<String> BOOLEAN_WORDS = Set.of("yes", "no", "on", "off", "true", "false", "y", "n");

    @Test
    void germanAndEnglishHaveTheSameKeys() throws IOException {
        Set<String> de = keys("de");
        Set<String> en = keys("en");
        Set<String> missingInEn = new LinkedHashSet<>(de);
        missingInEn.removeAll(en);
        Set<String> missingInDe = new LinkedHashSet<>(en);
        missingInDe.removeAll(de);
        assertEquals(Set.of(), missingInEn, "fehlt in en.yml");
        assertEquals(Set.of(), missingInDe, "fehlt in de.yml");
    }

    @Test
    void noKeysThatYamlReadsAsBoolean() throws IOException {
        for (String language : Set.of("de", "en")) {
            for (String key : keys(language)) {
                String last = key.substring(key.lastIndexOf('.') + 1);
                assertFalse(BOOLEAN_WORDS.contains(last), language + ".yml: " + key);
            }
        }
    }

    /** Every hover text sits in single quotes and is closed again - an apostrophe inside would cut it off. */
    @Test
    void hoverTextsAreWellFormed() throws IOException {
        Pattern hover = Pattern.compile("<hover:show_text:'[^']*'>");
        for (String language : Set.of("de", "en")) {
            try (InputStream in = LanguageFilesTest.class.getResourceAsStream("/lang/" + language + ".yml")) {
                assertNotNull(in, "lang/" + language + ".yml");
                int number = 0;
                for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\r?\n")) {
                    number++;
                    int opened = line.split("<hover:", -1).length - 1;
                    int closed = line.split("</hover>", -1).length - 1;
                    int complete = (int) hover.matcher(line).results().count();
                    assertEquals(opened, complete, language + ".yml:" + number + " hover text not closed with '>");
                    assertEquals(opened, closed, language + ".yml:" + number + " <hover> without </hover>");
                }
            }
        }
    }

    private static Set<String> keys(String language) throws IOException {
        try (InputStream in = LanguageFilesTest.class.getResourceAsStream("/lang/" + language + ".yml")) {
            assertNotNull(in, "lang/" + language + ".yml");
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("﻿", "");
            Set<String> keys = new LinkedHashSet<>();
            String section = null;
            for (String line : text.split("\r?\n")) {
                Matcher m = KEY.matcher(line);
                if (!m.find()) {
                    continue;
                }
                if (m.group(1).isEmpty()) {
                    section = m.group(2);
                    keys.add(section);
                } else if (section != null) {
                    keys.add(section + "." + m.group(2));
                }
            }
            return keys;
        }
    }
}
