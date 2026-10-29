package at.marcelr.mrchunks;

import at.marcelr.mrchunks.lag.LagLimits;
import at.marcelr.mrchunks.lag.Metric;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoaderAreaTest {

    private static final UUID WORLD = UUID.randomUUID();

    /** A loader whose block is in chunk 0/0. */
    private static Loader loader(int north, int south, int west, int east) {
        Loader loader = new Loader(1, UUID.randomUUID(), WORLD, "world", 8, 64, 8, 0, 1, null, true);
        loader.setReach(north, south, west, east);
        return loader;
    }

    @Test
    void areaIsWidthTimesLength() {
        Loader loader = loader(1, 2, 0, 4);
        assertEquals(5, loader.width());
        assertEquals(4, loader.length());
        assertEquals(20, loader.area());
        assertEquals(-1, loader.minChunkZ());
        assertEquals(2, loader.maxChunkZ());
        assertEquals(0, loader.minChunkX());
        assertEquals(4, loader.maxChunkX());
        assertEquals(1, loader(0, 0, 0, 0).area());
        assertEquals(49, loader(3, 3, 3, 3).area());
    }

    @Test
    void centredOnlyWithTheSameReachEverywhere() {
        assertTrue(loader(2, 2, 2, 2).centred());
        assertFalse(loader(2, 2, 2, 3).centred());
        assertFalse(loader(0, 4, 2, 2).centred());
    }

    @Test
    void coversItsRectangle() {
        Loader loader = loader(1, 0, 0, 2);
        assertTrue(loader.covers(0, -1));
        assertTrue(loader.covers(2, 0));
        assertFalse(loader.covers(-1, 0));
        assertFalse(loader.covers(0, 1));
    }

    @Test
    void overlapsWhenRectanglesShareAChunk() {
        Loader a = loader(0, 0, 0, 3); // chunks x 0..3, z 0
        Loader b = new Loader(2, UUID.randomUUID(), WORLD, "world", 16 * 5 + 1, 64, 1, 0, 1, null, true);
        b.setReach(0, 0, 2, 0); // chunks x 3..5, z 0
        assertTrue(a.overlaps(b));
        assertTrue(b.overlaps(a));
        b.setReach(0, 0, 1, 0); // chunks x 4..5
        assertFalse(a.overlaps(b));
        Loader other = new Loader(3, UUID.randomUUID(), UUID.randomUUID(), "nether", 8, 64, 8, 0, 1, null, true);
        assertFalse(a.overlaps(other));
    }

    @Test
    void limitsScaleWithTheArea() {
        LagLimits per49 = LagLimits.defaults();
        LagLimits nine = per49.scaled(9 / 49.0);
        assertEquals(400 * 9 / 49.0, nine.lock(Metric.ENTITIES), 1e-9);
        // Three 7x7 loaders together get three times the limits of one.
        assertEquals(3 * per49.warn(Metric.HOPPERS), per49.scaled(147 / 49.0).warn(Metric.HOPPERS), 1e-9);
        // Splitting the same chunks into small loaders never allows more in total.
        double small = 0;
        for (int i = 0; i < 16; i++) {
            small += per49.scaled(9 / 49.0).lock(Metric.ENTITIES);
        }
        assertEquals(per49.scaled(144 / 49.0).lock(Metric.ENTITIES), small, 1e-9);
    }
}
