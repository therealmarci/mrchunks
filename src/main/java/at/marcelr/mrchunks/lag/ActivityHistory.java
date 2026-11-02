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
 * With MRadmin every round is also saved in its storage ({@link #encode}) and read back on start ({@link #restore}),
 * so the charts survive a restart. No Bukkit types, so it can be tested on its own.
 */
public final class ActivityHistory {

    /** What the longest chart shows: 12 hours. */
    public static final long MAX_AGE = 12 * 3_600_000L;
    private static final String OFF = "off";

    /**
     * One measurement, or the peaks of several ones: value per {@link Metric#ordinal()}. Without values the loader was
     * off at that time (switched off, disabled by lag, locked with nobody loading the area).
     */
    public record Point(long time, float[] values) {

        public boolean off() {
            return values == null;
        }

        public double value(Metric metric) {
            return off() ? 0 : values[metric.ordinal()];
        }
    }

    private final Map<Integer, Deque<Point>> loaders = new HashMap<>();

    /** @return the new point */
    public Point add(int loader, Activity activity) {
        float[] values = new float[Metric.values().length];
        for (Metric metric : Metric.values()) {
            values[metric.ordinal()] = (float) activity.value(metric);
        }
        Point point = new Point(activity.time(), values);
        add(loader, point);
        return point;
    }

    /** The loader was off in this measuring round. @return the new point */
    public Point addOff(int loader, long time) {
        Point point = new Point(time, null);
        add(loader, point);
        return point;
    }

    /** The newest real measurement of a loader, or null. */
    public Point last(int loader) {
        Deque<Point> points = loaders.get(loader);
        if (points != null) {
            for (var it = points.descendingIterator(); it.hasNext(); ) {
                Point point = it.next();
                if (!point.off()) {
                    return point;
                }
            }
        }
        return null;
    }

    private void add(int loader, Point point) {
        Deque<Point> points = loaders.computeIfAbsent(loader, k -> new ArrayDeque<>());
        points.addLast(point);
        while (points.peekFirst().time() < point.time() - MAX_AGE) {
            points.removeFirst();
        }
    }

    /**
     * One measuring round as a line of text: {@code 4:12,3,0.5,0,0,1;35:off} - loader id, then the values in the order
     * of {@link Metric} or "off". New metrics only go to the end of the enum, so old lines stay readable.
     */
    public static String encode(Map<Integer, Point> round) {
        StringBuilder line = new StringBuilder();
        round.forEach((loader, point) -> {
            if (!line.isEmpty()) {
                line.append(';');
            }
            line.append(loader).append(':');
            if (point.off()) {
                line.append(OFF);
                return;
            }
            for (int i = 0; i < point.values().length; i++) {
                float value = Math.round(point.values()[i] * 10) / 10f;
                line.append(i == 0 ? "" : ",").append(value == (long) value ? String.valueOf((long) value) : String.valueOf(value));
            }
        });
        return line.toString();
    }

    /** Reads saved rounds (time -> line, oldest first) into memory; broken parts are skipped. */
    public void restore(Map<Long, String> rounds) {
        int metrics = Metric.values().length;
        rounds.forEach((time, line) -> {
            for (String part : line.split(";")) {
                int colon = part.indexOf(':');
                if (colon <= 0) {
                    continue;
                }
                try {
                    int loader = Integer.parseInt(part.substring(0, colon));
                    if (part.substring(colon + 1).equals(OFF)) {
                        add(loader, new Point(time, null));
                        continue;
                    }
                    String[] saved = part.substring(colon + 1).split(",");
                    float[] values = new float[metrics];
                    for (int i = 0; i < Math.min(metrics, saved.length); i++) {
                        values[i] = Float.parseFloat(saved[i]);
                    }
                    add(loader, new Point(time, values));
                } catch (NumberFormatException e) {
                    // cut off by a crash
                }
            }
        });
    }

    /** Forgets every loader the test does not accept (deleted ones). */
    public void retain(IntPredicate exists) {
        loaders.keySet().removeIf(id -> !exists.test(id));
    }

    /**
     * The measurements since {@code from}, at most {@code maxPoints}: if there are more, they are combined into evenly
     * wide time slices, each with the highest value per metric - a peak must stay visible against the limits. A slice
     * only counts as off if the loader was off all of it.
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
                if (current >= 0) {
                    result.add(new Point(time, peaks));
                }
                current = index;
                peaks = null;
            }
            if (!point.off()) {
                if (peaks == null) {
                    peaks = point.values().clone();
                } else {
                    for (int i = 0; i < peaks.length; i++) {
                        peaks[i] = Math.max(peaks[i], point.values()[i]);
                    }
                }
            }
            time = point.time();
        }
        result.add(new Point(time, peaks));
        return result;
    }
}
