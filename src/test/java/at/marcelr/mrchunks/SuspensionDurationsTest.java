package at.marcelr.mrchunks;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SuspensionDurationsTest {

    private static final long HOUR = 3_600_000L;
    private static final Logger LOG = Logger.getAnonymousLogger();

    private static List<Long> parse(String... values) {
        return Settings.suspensionDurations(List.of(values), LOG);
    }

    @Test
    void sortedWithoutDuplicates() {
        assertEquals(List.of(HOUR, 6 * HOUR, 24 * HOUR), parse("24h", "1h", "6h", "1h"));
    }

    @Test
    void neverLongerThan24Hours() {
        assertEquals(List.of(12 * HOUR), parse("25h", "12h", "permanent", "7d"));
    }

    @Test
    void nothingValidFallsBackTo24Hours() {
        assertEquals(List.of(24 * HOUR), parse("nonsense"));
        assertEquals(List.of(24 * HOUR), parse());
    }

    @Test
    void atMostSevenForTheMenu() {
        assertEquals(7, parse("1h", "2h", "3h", "4h", "5h", "6h", "7h", "8h").size());
    }
}
