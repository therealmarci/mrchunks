package at.marcelr.mrchunks.lag;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveWatchTest {

    @Test
    void aLoaderDropsOutWhenNobodyAsksAnyMore() {
        LiveWatch watch = new LiveWatch();
        watch.watch(1, 0, 5);
        watch.watch(2, 3_000, 5);
        assertEquals(List.of(1), watch.expire(LiveWatch.IDLE_MILLIS));
        assertEquals(Set.of(2), watch.loaders());
    }

    @Test
    void askingAgainKeepsItWatched() {
        LiveWatch watch = new LiveWatch();
        watch.watch(1, 0, 5);
        watch.watch(1, 4_000, 5);
        assertEquals(List.of(), watch.expire(LiveWatch.IDLE_MILLIS));
    }

    @Test
    void theLimitOnlyStopsNewLoaders() {
        LiveWatch watch = new LiveWatch();
        assertTrue(watch.watch(1, 0, 2));
        assertTrue(watch.watch(2, 0, 2));
        assertFalse(watch.watch(3, 0, 2));
        assertFalse(watch.contains(3));
        assertTrue(watch.watch(1, 1, 2));
    }

    @Test
    void severalViewersCountOnce() {
        LiveWatch watch = new LiveWatch();
        watch.watch(1, 0, 5);
        watch.watch(1, 0, 5);
        assertEquals(1, watch.size());
    }
}
