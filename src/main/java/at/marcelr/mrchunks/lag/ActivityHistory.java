package at.marcelr.mrchunks.lag;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;

/**
 * The measurements of every loader over the last {@link #MAX_AGE} ms, for the charts in MRadmin's web interface.
 * Only in memory: a restart starts with empty charts. No Bukkit types, so it can be tested on its own.
 */
public final class ActivityHistory {

    /** What the longest chart shows: 12 hours. */
    public static final long MAX_AGE = 12 * 3_600_000L;

    /** One measurement, or the peaks of several ones: value per {@link Metric#ordinal()}. */
    public record Point(long time, float[] values) {

        public double value(Metric metric) {
            return values[metric.ordinal()];
        }
    }

    private final Map<Integer, Deque<Point>> loaders = new HashMap<>();

    public void add(int loader, Activity activity) {
        float[] values = new float[Metric.values().length];
        for (Metric metric : Metric.values()) {
            values[metric.ordinal()] = (float) activity.value(metric);
        }
        Deque<Point> points = loaders.computeIfAbsent(loader, k -> new ArrayDeque<>());
        points.addLast(new Point(activity.time(), values));
        while (points.peekFirst().time() < activity.time() - MAX_AGE) {
            points.removeFirst();
        }
    }

    /** Forgets every loader the test does not accept (deleted ones). */
    public void retain(IntPredicate exists) {
        loaders.keySet().removeIf(id -> !exists.test(id));
    }

    /**
     * The measurements since {@code from}, at most {@code maxPoints}: if there are more, they are combined into evenly
     * wide time slices, each with the highest value per metric - a peak must stay visible against the limits.
     */
    public List<Point> points(int loader, long from, long to, int maxPoints) {
        List<Point> recent = new ArrayList<>();
        for (Point point : loaders.getOrDefault(loader, new ArrayDeque<>())) {
            if (point.time() >= from) {
                recent.add(point);
            }
        }
        if (recent.size() <= maxPoints) {
            return recent;
        }
        double slice = (double) Math.max(1, to - from) / maxPoints;
        List<Point> result = new ArrayList<>(maxPoints);
        long current = -1;
        long time = 0;
        float[] peaks = null;
        for (Point point : recent) {
            long index = (long) ((point.time() - from) / slice);
            if (index != current) {
                if (peaks != null) {
                    result.add(new Point(time, peaks));
                }
                current = index;
                peaks = point.values().clone();
            } else {
                for (int i = 0; i < peaks.length; i++) {
                    peaks[i] = Math.max(peaks[i], point.values()[i]);
                }
            }
            time = point.time();
        }
        result.add(new Point(time, peaks));
        return result;
    }
}
