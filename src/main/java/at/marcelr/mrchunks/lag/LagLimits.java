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

    /** Each limit at least the one of {@code floor} (0 = not checked stays 0). */
    public LagLimits atLeast(LagLimits floor) {
        Map<Metric, Double> w = new EnumMap<>(Metric.class);
        Map<Metric, Double> l = new EnumMap<>(Metric.class);
        for (Metric metric : Metric.values()) {
            w.put(metric, warn(metric) > 0 ? Math.max(warn(metric), floor.warn(metric)) : 0);
            l.put(metric, lock(metric) > 0 ? Math.max(lock(metric), floor.lock(metric)) : 0);
        }
        return new LagLimits(w, l);
    }

    /** Whole numbers (a checked limit stays at least 1). */
    public LagLimits rounded() {
        Map<Metric, Double> w = new EnumMap<>(Metric.class);
        Map<Metric, Double> l = new EnumMap<>(Metric.class);
        for (Metric metric : Metric.values()) {
            w.put(metric, round(warn(metric)));
            l.put(metric, round(lock(metric)));
        }
        return new LagLimits(w, l);
    }

    private static double round(double value) {
        return value <= 0 ? 0 : Math.max(1, Math.round(value));
    }

    /** Fixed limits for small loaders (up to 3x3, see lag-protection.small-loaders): higher than 9/49 of the defaults. */
    public static LagLimits smallDefaults() {
        Map<Metric, Double> warn = new EnumMap<>(Metric.class);
        Map<Metric, Double> lock = new EnumMap<>(Metric.class);
        put(warn, lock, Metric.ENTITIES, 100, 250);
        put(warn, lock, Metric.ITEMS, 80, 300);
        put(warn, lock, Metric.REDSTONE, 150, 600);
        put(warn, lock, Metric.HOPPERS, 100, 400);
        put(warn, lock, Metric.PISTONS, 15, 50);
        put(warn, lock, Metric.SPAWNS, 8, 25);
        return new LagLimits(Map.copyOf(warn), Map.copyOf(lock));
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
