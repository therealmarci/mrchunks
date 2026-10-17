package at.marcelr.mrchunks.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DurationUtilTest {

    @ParameterizedTest(name = "{0} = {1} ms")
    @CsvSource({
            "15m, 900000",
            "1h30m, 5400000",
            "2d, 172800000",
            "1w, 604800000",
            "90s, 90000",
            "1d12h, 129600000",
            "15, 900000",          // plain number = minutes
            "' 1H 30M ', 5400000", // spaces and upper case are fine
    })
    void parse(String input, long expected) {
        assertEquals(expected, DurationUtil.parse(input));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "1x", "m15", "1h-5m", "99999999999999999999d"})
    void parseInvalid(String input) {
        assertEquals(-1, DurationUtil.parse(input));
    }

    @Test
    void parseNull() {
        assertEquals(-1, DurationUtil.parse(null));
    }

    @ParameterizedTest(name = "{0} ms = {1}")
    @CsvSource({
            "0, 0s",
            "999, 1s",             // rounded up so a cooldown never shows 0s while still running
            "900000, 15m",
            "5400000, 1h 30m",
            "90061000, 1d 1h 1m 1s",
    })
    void format(long millis, String expected) {
        assertEquals(expected, DurationUtil.format(millis));
    }
}
