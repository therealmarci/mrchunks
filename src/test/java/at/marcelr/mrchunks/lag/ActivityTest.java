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
        // entities 250/100 warn = 2.5, 250/250 lock = 1; redstone 3000/400 = 7.5, 3000/1500 = 2
        Activity a = Activity.of(Map.of(Metric.ENTITIES, 250.0, Metric.REDSTONE, 3000.0), LIMITS, 0, 49, 49);
        assertTrue(a.conspicuous());
        assertEquals(7.5, a.warnRatio(), 1e-9);
        assertEquals(2.0, a.lockRatio(), 1e-9);
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
