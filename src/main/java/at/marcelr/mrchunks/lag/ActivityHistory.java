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

    /** What a loader was doing: its state as the charts show it. */
    public enum Phase {
        /** Switched on and holding its chunks. */
        ON,
        /** Switched off. */
        OFF,
        /** Switched on, but disabled by lag (staged shutdown). */
        SHED,
        /** Locked. */
        LOCKED;

        String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /**
     * One entry: the loader's phase at that time, with a measurement or the peaks of several ones (value per
     * {@link Metric#ordinal()}) or, when there is nothing to measure or it only changed, without values.
     */
    public record Point(long time, float[] values, Phase phase) {

        public boolean measured() {
            return values != null;
        }

        public double value(Metric metric) {
            return measured() ? values[metric.ordinal()] : 0;
        }
    }

    /** A stretch of time (ms) in which a loader was not {@link Phase#ON}. */
    public record Span(long from, long to, Phase phase) {
    }

    private final Map<Integer, Deque<Point>> loaders = new HashMap<>();
    private final long maxAge;

    public ActivityHistory() {
        this(MAX_AGE);
    }

    /** @param maxAge how long entries stay (ms) */
    public ActivityHistory(long maxAge) {
        this.maxAge = maxAge;
    }

    /** @return the new point */
    public Point add(int loader, Activity activity) {
        return add(loader, activity, Phase.ON);
    }

    /** A locked loader is measured too, while someone keeps its area loaded. @return the new point */
    public Point add(int loader, Activity activity, Phase phase) {
        float[] values = new float[Metric.values().length];
        for (Metric metric : Metric.values()) {
            values[metric.ordinal()] = (float) activity.value(metric);
        }
        Point point = new Point(activity.time(), values, phase);
        add(loader, point);
        return point;
    }

    /** The phase of the loader at this time, without a measurement. @return the new point */
    public Point addPhase(int loader, long time, Phase phase) {
        Point point = new Point(time, null, phase);
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
        while (points.peekFirst().time() < point.time() - maxAge) {
            points.removeFirst();
        }
    }

    /** Drops what is too old also for loaders that get no new entries. */
    public void trim(long now) {
        loaders.values().removeIf(points -> {
            while (!points.isEmpty() && points.peekFirst().time() < now - maxAge) {
                points.removeFirst();
            }
            return points.isEmpty();
        });
    }

    /**
     * The normal measurements with the finer live ones (both oldest first). A normal one only drops out inside a
     * stretch of live ones (two of them at most {@code stepMillis} apart around it). The last one before a stretch and
     * the first one after it stay, so the line runs on without a gap.
     */
    public static List<Point> merged(List<Point> normal, List<Point> live, long stepMillis) {
        List<Point> result = new ArrayList<>(normal.size() + live.size());
        int next = 0;
        for (Point point : normal) {
            while (next < live.size() && live.get(next).time() < point.time()) {
                result.add(live.get(next++));
            }
            boolean sameTime = next < live.size() && live.get(next).time() == point.time();
            boolean inside = next > 0 && next < live.size() && live.get(next).time() - live.get(next - 1).time() <= stepMillis;
            if (!sameTime && !inside) {
                result.add(point);
            }
        }
        result.addAll(live.subList(next, live.size()));
        return result;
    }

    /**
     * One measuring round as a line of text: {@code 4:12,3,0.5,0,0,1;35:off;36:locked,12,3,0.5,0,0,1} - loader id, then
     * the phase unless it is on with values, then the values in the order of {@link Metric}. New metrics only go to the
     * end of the enum, so old lines stay readable.
     */
    public static String encode(Map<Integer, Point> round) {
        StringBuilder line = new StringBuilder();
        round.forEach((loader, point) -> {
            if (!line.isEmpty()) {
                line.append(';');
            }
            line.append(loader).append(':');
            if (!point.measured()) {
                line.append(point.phase().key());
                return;
            }
            if (point.phase() != Phase.ON) {
                line.append(point.phase().key()).append(',');
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
                    String[] saved = part.substring(colon + 1).split(",");
                    Phase phase = Phase.ON;
                    int first = 0;
                    for (Phase known : Phase.values()) {
                        if (saved[0].equals(known.key())) {
                            phase = known;
                            first = 1;
                        }
                    }
                    if (first == saved.length) {
                        addPhase(loader, time, phase);
                        continue;
                    }
                    float[] values = new float[metrics];
                    for (int i = first; i < saved.length && i - first < metrics; i++) {
                        values[i - first] = Float.parseFloat(saved[i]);
                    }
                    add(loader, new Point(time, values, phase));
                } catch (NumberFormatException e) {
                    // cut off by a crash
                }
            }
        });
    }

    /**
     * Coarser points for longer ranges: one per {@code stepMillis} of the clock, with the highest value per metric - a
     * peak must stay visible against the limits. The slices do not depend on the range shown, so the points stay where
     * they are while a chart is scrolled.
     */
    public static List<Point> combined(List<Point> points, long stepMillis) {
        if (stepMillis <= 0) {
            return points;
        }
        List<Point> result = new ArrayList<>();
        long current = Long.MIN_VALUE;
        long time = 0;
        float[] peaks = null;
        for (Point point : points) {
            long slice = point.time() / stepMillis;
            if (slice != current) {
                if (peaks != null) {
                    result.add(new Point(time, peaks, Phase.ON));
                }
                current = slice;
                peaks = point.values().clone();
            } else {
                for (int i = 0; i < peaks.length; i++) {
                    peaks[i] = Math.max(peaks[i], point.values()[i]);
                }
            }
            time = point.time();
        }
        if (peaks != null) {
            result.add(new Point(time, peaks, Phase.ON));
        }
        return result;
    }

    /** When the first entry of a loader was made (ms), or 0 if it has none. */
    public long oldest(int loader) {
        Deque<Point> points = loaders.get(loader);
        return points == null || points.isEmpty() ? 0 : points.peekFirst().time();
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
                    result.add(new Point(time, peaks, Phase.ON));
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
        result.add(new Point(time, peaks, Phase.ON));
        return result;
    }

    /**
     * When the loader was not on between {@code from} and {@code to}, oldest first. From every entry, not from the
     * combined points, so a short phase stays visible on a long chart. A phase ends with the next entry of another one.
     */
    public List<Span> phases(int loader, long from, long to) {
        List<Span> spans = new ArrayList<>();
        Phase current = Phase.ON;
        long start = 0;
        for (Point point : loaders.getOrDefault(loader, new ArrayDeque<>())) {
            if (point.phase() == current) {
                continue;
            }
            if (current != Phase.ON && point.time() > from) {
                spans.add(new Span(Math.max(start, from), point.time(), current));
            }
            current = point.phase();
            start = point.time();
        }
        if (current != Phase.ON) {
            spans.add(new Span(Math.max(start, from), to, current));
        }
        return spans;
    }
}
