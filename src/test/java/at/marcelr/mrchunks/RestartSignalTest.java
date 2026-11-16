package at.marcelr.mrchunks;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** When a loader gives its redstone signal before restarts. */
class RestartSignalTest {

    private static Loader loader(RestartSignal.Mode mode, int minutes) {
        Loader loader = new Loader(1, UUID.randomUUID(), UUID.randomUUID(), "world", 0, 64, 0, 0, 1, null, true);
        loader.setSignalMode(mode);
        loader.setSignalMinutes(minutes);
        return loader;
    }

    @Test
    void offGivesNothing() {
        assertFalse(RestartSignal.wants(loader(RestartSignal.Mode.NONE, 5), 1_000));
        assertFalse(RestartSignal.wants(loader(RestartSignal.Mode.NONE, 5), -1));
    }

    @Test
    void onBeforeRestartsOnlyInTheLastMinutes() {
        Loader l = loader(RestartSignal.Mode.ON, 5);
        assertFalse(RestartSignal.wants(l, -1), "no restart known");
        assertFalse(RestartSignal.wants(l, 6 * 60_000L));
        assertTrue(RestartSignal.wants(l, 5 * 60_000L));
        assertTrue(RestartSignal.wants(l, 0));
    }

    @Test
    void offBeforeRestartsIsTheOpposite() {
        Loader l = loader(RestartSignal.Mode.OFF, 10);
        assertTrue(RestartSignal.wants(l, -1), "no restart known: signal on");
        assertTrue(RestartSignal.wants(l, 11 * 60_000L));
        assertFalse(RestartSignal.wants(l, 10 * 60_000L));
    }
}
