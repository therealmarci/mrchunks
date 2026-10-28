package at.marcelr.mrchunks.lag;

import java.util.EnumMap;
import java.util.Map;

/** Warn and lock limit per metric; 0 = this metric is not checked. */
public record LagLimits(Map<Metric, Double> warn, Map<Metric, Double> lock) {

    public double warn(Metric metric) {
        return warn.getOrDefault(metric, 0.0);
    }

    public double lock(Metric metric) {
        return lock.getOrDefault(metric, 0.0);
    }

    /** All limits times {@code factor} (0 stays 0 = not checked). */
    public LagLimits scaled(double factor) {
        Map<Metric, Double> w = new EnumMap<>(Metric.class);
        Map<Metric, Double> l = new EnumMap<>(Metric.class);
        for (Metric metric : Metric.values()) {
            w.put(metric, warn(metric) * factor);
            l.put(metric, lock(metric) * factor);
        }
        return new LagLimits(w, l);
    }

    /** Defaults for a 7x7 area (49 chunks, see lag-protection.limits-per-chunks); deliberately generous - farms are fine, runaway machines are not. */
    public static LagLimits defaults() {
        Map<Metric, Double> warn = new EnumMap<>(Metric.class);
        Map<Metric, Double> lock = new EnumMap<>(Metric.class);
        put(warn, lock, Metric.ENTITIES, 300, 800);
        put(warn, lock, Metric.ITEMS, 200, 1000);
        put(warn, lock, Metric.REDSTONE, 500, 2000);
        put(warn, lock, Metric.HOPPERS, 300, 1200);
        put(warn, lock, Metric.PISTONS, 40, 150);
        put(warn, lock, Metric.SPAWNS, 20, 80);
        return new LagLimits(Map.copyOf(warn), Map.copyOf(lock));
    }

    private static void put(Map<Metric, Double> warn, Map<Metric, Double> lock, Metric metric, double w, double l) {
        warn.put(metric, w);
        lock.put(metric, l);
    }
}
