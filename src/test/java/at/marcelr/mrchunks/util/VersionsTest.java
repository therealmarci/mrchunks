package at.marcelr.mrchunks.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionsTest {

    @ParameterizedTest(name = "{0} > {1}")
    @CsvSource({
            "0.9.3, 0.9.2",
            "0.10.0, 0.9.2",       // numeric, not alphabetic
            "1.0.0, 0.9.4",
            "1.0.0, 1.0.0-rc.1",   // release beats its pre-release
            "1.0.0-rc.2, 1.0.0-rc.1",
            "1.0.0-rc.10, 1.0.0-rc.2",
            "1.0.0-rc.1, 1.0.0-rc",
            "1.0.0-rc.1, 1.0.0-beta.5",
            "1.0.1, 1.0",
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
    })
    void equal(String a, String b) {
        assertEquals(0, Versions.compare(a, b));
    }

    @Test
    void prereleases() {
        assertTrue(Versions.isPrerelease("0.9.4"));
        assertTrue(Versions.isPrerelease("v0.1.0"));
        assertTrue(Versions.isPrerelease("1.0.0-rc.1"));
        assertFalse(Versions.isPrerelease("1.0.0"));
        assertFalse(Versions.isPrerelease("v2.3.1"));
    }
}
