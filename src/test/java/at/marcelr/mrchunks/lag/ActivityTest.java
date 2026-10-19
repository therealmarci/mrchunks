package at.marcelr.mrchunks.lag;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActivityTest {

    private static final LagLimits LIMITS = LagLimits.defaults();

    @Test
    void quietAreaIsNotConspicuous() {
        Activity a = Activity.of(Map.of(Metric.ENTITIES, 30.0, Metric.HOPPERS, 10.0), LIMITS, 0, 49, 49);
        assertFalse(a.conspicuous());
        assertTrue(a.lockRatio() < 1);
    }

    @Test
    void warnAndLockRatiosUseTheHighestMetric() {
        // entities 400/300 warn = 1.33, 400/800 lock = 0.5; redstone 2500/500 = 5, 2500/2000 = 1.25
        Activity a = Activity.of(Map.of(Metric.ENTITIES, 400.0, Metric.REDSTONE, 2500.0), LIMITS, 0, 49, 49);
        assertTrue(a.conspicuous());
        assertEquals(5.0, a.warnRatio(), 1e-9);
        assertEquals(1.25, a.lockRatio(), 1e-9);
        assertEquals(Metric.REDSTONE, a.worst());
    }

    @Test
    void limitZeroSwitchesAMetricOff() {
        LagLimits noRedstone = new LagLimits(Map.of(Metric.REDSTONE, 0.0), Map.of(Metric.REDSTONE, 0.0));
        Activity a = Activity.of(Map.of(Metric.REDSTONE, 99_999.0), noRedstone, 0, 49, 49);
        assertEquals(0, a.warnRatio());
        assertEquals(0, a.lockRatio());
    }

    @Test
    void metricKeysRoundTrip() {
        for (Metric metric : Metric.values()) {
            assertEquals(metric, Metric.byKey(metric.key()));
        }
    }
}
