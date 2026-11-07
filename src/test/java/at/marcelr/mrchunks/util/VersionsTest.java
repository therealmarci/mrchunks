package at.marcelr.mrchunks.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Identical in all MRtechnology plugins (only the package differs). */
class VersionsTest {

    @ParameterizedTest(name = "{0} > {1}")
    @CsvSource({
            // old scheme (SemVer)
            "0.9.3, 0.9.2",
            "0.10.0, 0.9.2",       // numeric, not alphabetic
            "1.0.0, 0.9.4",
            "1.0.0, 1.0.0-rc.1",   // release beats its pre-release
            "1.0.0-rc.2, 1.0.0-rc.1",
            "1.0.0-rc.10, 1.0.0-rc.2",
            "1.0.0-rc.1, 1.0.0-rc",
            "1.0.0-rc.1, 1.0.0-beta.5",
            "1.0.1, 1.0",
            // new scheme: MAJOR, MINOR, PATCH, then the build - the channel does not count
            "mc26.3-STA-1.4.0-bu030, mc26.3-BET-1.4.0-bu029",
            "mc26.3-BET-1.4.1-bu001, mc26.3-STA-1.4.0-bu030",
            "mc26.3-BET-1.4.0-bu002, mc26.3-BET-1.4.0-bu001",
            "mc26.3-BET-1.4.0-bu010, mc26.3-BET-1.4.0-bu009",
            "mc26.3-STA-1.10.0-bu001, mc26.3-STA-1.9.5-bu040",
            "mc26.3-STA-2.0.0-bu001, mc26.3-BET-1.99.0-bu100",
            "mc26.3-BET-0.10.0-bu001, mc26.3-BET-0.9.15-bu003",
            // transition: new releases after the last old ones
            "mc26.3-BET-1.4.0-bu001, mc26.3-v1.3.1",
            "mc26.3-STA-1.3.1-bu001, mc26.3-v1.3.1",  // old = build 0
            "mc26.3-v1.4.0, mc26.3-BET-1.3.9-bu050",
            "mc26.3-BET-0.9.15-bu001, mc26.3-v0.9.14",
            "mc26.3-STA-1.0.0-bu001, mc26.3-v1.0.0-rc.1",
            "STA-1.4.0-bu001+mc26.3, 1.3.1+mc26.3",   // plugin.yml form
    })
    void newer(String newer, String older) {
        assertTrue(Versions.compare(newer, older) > 0, newer + " should be newer than " + older);
        assertTrue(Versions.compare(older, newer) < 0, older + " should be older than " + newer);
    }

    @ParameterizedTest(name = "{0} == {1}")
    @CsvSource({
            "0.9.2, 0.9.2",
            "v0.9.2, 0.9.2",       // Git tag prefix
            "1.0, 1.0.0",
            "1.0.0+5.abc123, 1.0.0", // build metadata is ignored
            "mc26.3-v1.2.0, 1.2.0",  // Minecraft prefix is ignored
            "mc26.3-v1.2.0, mc26.4-v1.2.0",
            "mc26.3-STA-1.4.0-bu030, STA-1.4.0-bu30",
            "mc26.3-sta-1.4.0-BU030, mc26.3-STA-1.4.0-bu030", // case does not matter
            "STA-1.4.0-bu001+mc26.3, mc26.3-STA-1.4.0-bu001",
            "STA-1.4.0-bu001+dev.abc123, STA-1.4.0-bu001",
            "plugin-mc26.3-BET-0.2.0-bu001, BET-0.2.0-bu001+mc26.3",
    })
    void equal(String a, String b) {
        assertEquals(0, Versions.compare(a, b));
    }

    @Test
    void channels() {
        // old scheme: 0.x and suffixes are beta
        assertTrue(Versions.isPrerelease("0.9.4"));
        assertTrue(Versions.isPrerelease("v0.1.0"));
        assertTrue(Versions.isPrerelease("1.0.0-rc.1"));
        assertFalse(Versions.isPrerelease("1.0.0"));
        assertFalse(Versions.isPrerelease("v2.3.1"));
        assertTrue(Versions.isPrerelease("mc26.3-v1.1.0-rc.1"));
        assertFalse(Versions.isPrerelease("mc26.3-v1.1.0"));
        // new scheme: the label decides
        assertEquals(Versions.Channel.STABLE, Versions.channel("mc26.3-STA-1.4.0-bu030"));
        assertEquals(Versions.Channel.BETA, Versions.channel("mc26.3-BET-1.4.0-bu029"));
        assertEquals(Versions.Channel.BETA, Versions.channel("plugin-mc26.3-bet-0.2.0-bu001"));
        assertTrue(Versions.isScheme("STA-1.4.0-bu001+mc26.3"));
        assertFalse(Versions.isScheme("mc26.3-v1.4.0"));
    }

    @Test
    void wantsBeta() {
        assertFalse(Versions.wantsBeta("STA-1.4.0-bu030", false));
        assertTrue(Versions.wantsBeta("STA-1.4.0-bu030", true));
        // switching betas off never downgrades: whoever runs a beta just waits for the next stable
        assertFalse(Versions.wantsBeta("BET-1.4.1-bu001", false));
        // 0.x only exists as beta
        assertTrue(Versions.wantsBeta("BET-0.9.15-bu001", false));
        assertTrue(Versions.wantsBeta("0.9.14+mc26.3", false));
        assertFalse(Versions.wantsBeta("1.3.1+mc26.3", false));
        assertFalse(Versions.wantsBeta(null, false));
    }

    @Test
    void parts() {
        assertEquals("STA-1.4.0-bu030", Versions.plugin("mc26.3-sta-1.4.0-bu30"));
        assertEquals("BET-1.4.1-bu001", Versions.plugin("BET-1.4.1-bu1+mc26.3"));
        assertEquals("1.4.0", Versions.number("mc26.3-BET-1.4.0-bu002"));
        assertEquals("1.4.0", Versions.number("v1.4.0-rc.1"));
        assertEquals(2, Versions.build("mc26.3-BET-1.4.0-bu002"));
        assertEquals(0, Versions.build("mc26.3-v1.4.0"));
        assertArrayEquals(new int[]{1, 4, 2}, Versions.parts("mc26.3-STA-1.4.2-bu001"));
        assertArrayEquals(new int[]{1, 3, 0}, Versions.parts("1.3"));
        assertEquals("STA-1.4.0-bu030", Versions.format(Versions.Channel.STABLE, "1.4.0", 30));
        assertEquals("BET-1.4.1-bu1000", Versions.format(Versions.Channel.BETA, "1.4.1", 1000));
    }

    @Test
    void minecraftPrefix() {
        assertEquals("26.3", Versions.minecraft("mc26.3-v1.2.0"));
        assertEquals("26.3.1", Versions.minecraft("MC26.3.1-v1.2.0-rc.1"));
        assertNull(Versions.minecraft("v1.0.0"));
        assertEquals("26.3", Versions.minecraft("1.2.0+mc26.3"));
        assertEquals("1.2.0", Versions.plugin("1.2.0+mc26.3"));
        assertEquals(0, Versions.compare("1.2.0+mc26.3", "mc26.3-v1.2.0"));
        assertEquals("1.2.0-rc.1", Versions.plugin("mc26.3-v1.2.0-rc.1"));
        assertEquals("mc26.3-v1.2.0", Versions.full("26.3", "1.2.0"));
        assertTrue(Versions.compare("mc26.3-v1.10.0", "mc26.3-v1.9.3") > 0);
        // new scheme
        assertEquals("26.3", Versions.minecraft("mc26.3-STA-1.4.0-bu001"));
        assertEquals("26.4", Versions.minecraft("plugin-mc26.4-BET-0.1.0-bu001"));
        assertEquals("26.3", Versions.minecraft("STA-1.4.0-bu001+mc26.3"));
        assertEquals("mc26.3-STA-1.4.0-bu001", Versions.full("26.3", "STA-1.4.0-bu001"));
        assertEquals("mc26.3-BET-1.4.0-bu002", Versions.full("26.3", "BET-1.4.0-bu2+mc26.3"));
    }

    @Test
    void separateReleasesOfPluginAndMod() {
        assertEquals("plugin", Versions.component("plugin-mc26.3-v0.2.0"));
        assertEquals("mod", Versions.component("mod-mc26.3-v0.1.0"));
        assertNull(Versions.component("mc26.3-v0.1.0"));
        assertEquals("mod", Versions.component("MOD-mc26.3-BET-0.2.0-bu001"));
        assertNull(Versions.component("mc26.3-STA-1.4.0-bu001"));
        assertEquals("26.3", Versions.minecraft("mod-mc26.3-v0.1.0"));
        assertEquals("0.2.0", Versions.plugin("plugin-mc26.3-v0.2.0"));
        assertTrue(Versions.compare("mod-mc26.3-v0.1.1", "0.1.0+mc26.3") > 0);
        assertEquals(0, Versions.compare("plugin-mc26.3-v0.2.0", "0.2.0+mc26.3"));
        assertEquals("BET-0.2.0-bu001", Versions.plugin("plugin-mc26.3-BET-0.2.0-bu001"));
    }
}
