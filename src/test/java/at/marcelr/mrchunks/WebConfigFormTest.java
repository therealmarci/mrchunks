package at.marcelr.mrchunks;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * web-config.yml (the form of config.yml in MRadmin's web interface) has to cover every setting of config.yml - and
 * only those - with a name in German and English.
 */
class WebConfigFormTest {

    private static final Set<String> TYPES = Set.of("bool", "int", "decimal", "string", "text", "secret", "choice", "color",
            "duration", "strings", "ints", "choices", "worlds", "table");

    private static YamlConfiguration load(String name) throws Exception {
        try (InputStream in = WebConfigFormTest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(in, name);
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(text.startsWith("﻿") ? text.substring(1) : text);
            return yaml;
        }
    }

    private static List<Map<?, ?>> fields() throws Exception {
        List<Map<?, ?>> fields = new ArrayList<>();
        for (Map<?, ?> section : load("web-config.yml").getMapList("sections")) {
            assertNames(section, "section " + section.get("id"));
            for (Object f : (List<?>) section.get("fields")) {
                fields.add((Map<?, ?>) f);
            }
        }
        return fields;
    }

    private static void assertNames(Map<?, ?> map, String what) {
        assertTrue(map.get("name") instanceof Map<?, ?> name && name.get("de") != null && name.get("en") != null,
                what + ": name needs de and en");
    }

    @Test
    void everySettingHasAField() throws Exception {
        YamlConfiguration config = load("config.yml");
        List<Map<?, ?>> fields = fields();
        Set<String> missing = new TreeSet<>();
        for (String key : config.getKeys(true)) {
            if (config.isConfigurationSection(key) || key.equals("config-version")) {
                continue;
            }
            boolean covered = fields.stream().anyMatch(f -> {
                String k = String.valueOf(f.get("key"));
                return k.equals(key) || ("table".equals(f.get("type")) && key.startsWith(k + "."));
            });
            if (!covered) {
                missing.add(key);
            }
        }
        assertEquals(Set.of(), missing, "settings of config.yml without a field in web-config.yml");
    }

    @Test
    void everyFieldIsASettingWithNamesAndAType() throws Exception {
        YamlConfiguration config = load("config.yml");
        Set<String> seen = new TreeSet<>();
        for (Map<?, ?> f : fields()) {
            String key = String.valueOf(f.get("key"));
            assertTrue(seen.add(key), "twice: " + key);
            assertTrue(config.contains(key), "not in config.yml: " + key);
            assertTrue(TYPES.contains(String.valueOf(f.get("type"))), key + ": unknown type " + f.get("type"));
            assertNames(f, key);
            if ("choice".equals(f.get("type"))) {
                List<?> options = (List<?>) f.get("options");
                assertFalse(options == null || options.isEmpty(), key + ": no options");
                String value = config.getString(key);
                assertTrue(options.stream().anyMatch(o -> o instanceof Map<?, ?> m && String.valueOf(m.get("value")).equals(value)),
                        key + ": default " + value + " is not an option");
            }
            if (f.get("when") instanceof String when) {
                String other = when.replace("!", "").split("=")[0];
                assertTrue(seen.contains(other) || config.contains(other), key + ": when names unknown " + other);
            }
        }
    }

    @Test
    void tablesNameTheirRows() throws Exception {
        YamlConfiguration config = load("config.yml");
        for (Map<?, ?> f : fields()) {
            if ("table".equals(f.get("type"))) {
                Map<?, ?> rows = (Map<?, ?>) f.get("rows");
                for (String row : config.getConfigurationSection(String.valueOf(f.get("key"))).getKeys(false)) {
                    assertTrue(rows != null && rows.containsKey(row), f.get("key") + ": row without a name: " + row);
                }
            }
        }
    }

    /** A comma without quotes in {de: ..., en: ...} cuts the text off and makes a key of the rest. */
    @Test
    void textsAreWhole() throws Exception {
        List<String> broken = new ArrayList<>();
        for (Map<?, ?> section : load("web-config.yml").getMapList("sections")) {
            List<Object> texts = new ArrayList<>();
            texts.add(section.get("name"));
            texts.add(section.get("help"));
            for (Object o : (List<?>) section.get("fields")) {
                Map<?, ?> f = (Map<?, ?>) o;
                texts.add(f.get("name"));
                texts.add(f.get("help"));
                texts.add(f.get("unit"));
                if (f.get("options") instanceof List<?> options) {
                    texts.addAll(options);
                }
                if (f.get("rows") instanceof Map<?, ?> rows) {
                    texts.addAll(rows.values());
                }
            }
            for (Object text : texts) {
                if (text instanceof Map<?, ?> m) {
                    for (Object key : m.keySet()) {
                        if (!Set.of("de", "en", "value", "key", "type", "name").contains(String.valueOf(key))) {
                            broken.add(section.get("id") + ": " + m);
                        }
                    }
                }
            }
        }
        assertEquals(List.of(), broken);
    }
}
