package at.marcelr.mrchunks.lag;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;

/**
 * The measurements of every loader over the last {@link #MAX_AGE} ms and when it was off, for the charts in MRadmin's
 * web interface. With MRadmin every round is also saved in its storage ({@link #encode}) and read back on start
 * ({@link #restore}), so the charts survive a restart. No Bukkit types, so it can be tested on its own.
 */
public final class ActivityHistory {

    /** What the longest chart shows: 12 hours. */
    public static final long MAX_AGE = 12 * 3_600_000L;
    private static final String OFF = "off";
    private static final String ON = "on";
    private static final float[] NO_VALUES = new float[0];

    /**
     * One entry: a measurement or the peaks of several ones (value per {@link Metric#ordinal()}), or a phase: without
     * values the loader is off (switched off, disabled by lag, locked with nobody loading the area), with empty values
     * it went on again.
     */
    public record Point(long time, float[] values) {

        public boolean off() {
            return values == null;
        }

        public boolean measured() {
            return values != null && values.length > 0;
        }

        public double value(Metric metric) {
            return measured() ? values[metric.ordinal()] : 0;
        }
    }

    /** A stretch of time in which a loader was off (ms). */
    public record OffPhase(long from, long to) {
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

    /** The loader is off at this time. @return the new point */
    public Point addOff(int loader, long time) {
        Point point = new Point(time, null);
        add(loader, point);
        return point;
    }

    /** The loader went on at this time, the next measurement follows later. @return the new point */
    public Point addOn(int loader, long time) {
        Point point = new Point(time, NO_VALUES);
        add(loader, point);
        return point;
    }

    /** The newest real measurement of a loader, or null. */
    public Point last(int loader) {
        Deque<Point> points = loaders.get(loader);
        if (points != null) {
            for (var it = points.descendingIterator(); it.hasNext(); ) {
                Point point = it.next();
                if (point.measured()) {
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
     * One measuring round as a line of text: {@code 4:12,3,0.5,0,0,1;35:off;36:on} - loader id, then the values in the
     * order of {@link Metric} or the phase. New metrics only go to the end of the enum, so old lines stay readable.
     */
    public static String encode(Map<Integer, Point> round) {
        StringBuilder line = new StringBuilder();
        round.forEach((loader, point) -> {
            if (!line.isEmpty()) {
                line.append(';');
            }
            line.append(loader).append(':');
            if (!point.measured()) {
                line.append(point.off() ? OFF : ON);
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
                    String rest = part.substring(colon + 1);
                    if (rest.equals(OFF)) {
                        addOff(loader, time);
                    } else if (rest.equals(ON)) {
                        addOn(loader, time);
                    } else {
                        String[] saved = rest.split(",");
                        float[] values = new float[metrics];
                        for (int i = 0; i < Math.min(metrics, saved.length); i++) {
                            values[i] = Float.parseFloat(saved[i]);
                        }
                        add(loader, new Point(time, values));
                    }
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
     * wide time slices, each with the highest value per metric - a peak must stay visible against the limits.
     */
    public List<Point> points(int loader, long from, long to, int maxPoints) {
        List<Point> recent = new ArrayList<>();
        for (Point point : loaders.getOrDefault(loader, new ArrayDeque<>())) {
            if (point.time() >= from && point.measured()) {
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

    /**
     * When the loader was off between {@code from} and {@code to}, oldest first. From every entry, not from the combined
     * points, so a short phase stays visible on a long chart. A phase ends with the next entry that is not "off".
     */
    public List<OffPhase> offPhases(int loader, long from, long to) {
        List<OffPhase> phases = new ArrayList<>();
        long start = -1;
        for (Point point : loaders.getOrDefault(loader, new ArrayDeque<>())) {
            if (point.off()) {
                start = start < 0 ? point.time() : start;
            } else if (start >= 0) {
                if (point.time() > from) {
                    phases.add(new OffPhase(Math.max(start, from), point.time()));
                }
                start = -1;
            }
        }
        if (start >= 0) {
            phases.add(new OffPhase(Math.max(start, from), to));
        }
        return phases;
    }
}
