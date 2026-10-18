package at.marcelr.mrchunks.lag;

import java.util.EnumMap;
import java.util.Map;

/**
 * Result of one measurement of a loader's area.
 *
 * @param values    value per metric (count or events per second)
 * @param warnRatio highest value / warn limit over all metrics (&ge; 1 = conspicuous)
 * @param lockRatio highest value / lock limit over all metrics (&ge; 1 = over the lock limit)
 * @param worst     the metric closest to (or furthest over) its warn limit
 * @param time      when it was measured (ms)
 */
public record Activity(Map<Metric, Double> values, double warnRatio, double lockRatio, Metric worst, long time) {

    public double value(Metric metric) {
        return values.getOrDefault(metric, 0.0);
    }

    public boolean conspicuous() {
        return warnRatio >= 1;
    }

    /** Builds the ratios from the values and the configured limits (a limit of 0 switches a metric off). */
    public static Activity of(Map<Metric, Double> values, LagLimits limits, long time) {
        Map<Metric, Double> copy = new EnumMap<>(Metric.class);
        copy.putAll(values);
        double warn = 0;
        double lock = 0;
        Metric worst = Metric.ENTITIES;
        for (Metric metric : Metric.values()) {
            double value = copy.getOrDefault(metric, 0.0);
            double w = ratio(value, limits.warn(metric));
            if (w > warn) {
                warn = w;
                worst = metric;
            }
            lock = Math.max(lock, ratio(value, limits.lock(metric)));
        }
        return new Activity(Map.copyOf(copy), warn, lock, worst, time);
    }

    private static double ratio(double value, double limit) {
        return limit <= 0 ? 0 : value / limit;
    }
}
