package at.marcelr.mrchunks.lag;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActivityHistoryTest {

    private static final long START = 1_000_000_000L;
    private static final long STEP = 20_000L;

    private static Activity activity(long time, double entities) {
        return Activity.of(Map.of(Metric.ENTITIES, entities), LagLimits.defaults(), time, 1, 1);
    }

    @Test
    void onlyTheChosenRange() {
        ActivityHistory history = new ActivityHistory();
        for (int i = 0; i < 10; i++) {
            history.add(1, activity(START + i * STEP, i));
        }
        List<ActivityHistory.Point> points = history.points(1, START + 7 * STEP, START + 9 * STEP, 300);
        assertEquals(3, points.size());
        assertEquals(7, points.getFirst().value(Metric.ENTITIES));
    }

    @Test
    void nothingOlderThanTwelveHoursStays() {
        ActivityHistory history = new ActivityHistory();
        long last = START + ActivityHistory.MAX_AGE + STEP;
        history.add(1, activity(START, 1));
        history.add(1, activity(last, 2));
        assertEquals(1, history.points(1, 0, last, 300).size());
    }

    @Test
    void combinedPointsKeepThePeak() {
        ActivityHistory history = new ActivityHistory();
        for (int i = 0; i < 1000; i++) {
            history.add(1, activity(START + i * STEP, i == 500 ? 900 : 1));
        }
        List<ActivityHistory.Point> points = history.points(1, START, START + 999 * STEP, 100);
        assertTrue(points.size() <= 101, "got " + points.size());
        assertEquals(900, points.stream().mapToDouble(p -> p.value(Metric.ENTITIES)).max().orElse(0));
    }

    @Test
    void savedRoundsComeBack() {
        ActivityHistory history = new ActivityHistory();
        Activity activity = Activity.of(Map.of(Metric.ENTITIES, 120.0, Metric.REDSTONE, 12.25, Metric.SPAWNS, 0.5),
                LagLimits.defaults(), START, 1, 1);
        Map<Integer, ActivityHistory.Point> round = new LinkedHashMap<>();
        round.put(4, history.add(4, activity));
        round.put(35, history.add(35, activity(START, 7)));

        ActivityHistory restored = new ActivityHistory();
        restored.restore(Map.of(START, ActivityHistory.encode(round)));

        ActivityHistory.Point point = restored.points(4, 0, START, 300).getFirst();
        assertEquals(START, point.time());
        assertEquals(120, point.value(Metric.ENTITIES));
        assertEquals(12.3, point.value(Metric.REDSTONE), 0.01);
        assertEquals(0.5, point.value(Metric.SPAWNS));
        assertEquals(7, restored.points(35, 0, START, 300).getFirst().value(Metric.ENTITIES));
    }

    @Test
    void offRoundsComeBackAndTheLastMeasurementStays() {
        ActivityHistory history = new ActivityHistory();
        Map<Integer, ActivityHistory.Point> round = new LinkedHashMap<>();
        round.put(4, history.addOff(4, START));

        ActivityHistory restored = new ActivityHistory();
        restored.restore(new TreeMap<>(Map.of(START - STEP, "4:9,0,0,0,0,0", START, ActivityHistory.encode(round))));

        assertTrue(restored.points(4, 0, START, 300).getLast().off());
        assertEquals(9, restored.last(4).value(Metric.ENTITIES));
    }

    @Test
    void aSliceIsOnlyOffIfTheLoaderWasOffAllOfIt() {
        ActivityHistory history = new ActivityHistory();
        for (int i = 0; i < 1000; i++) {
            if (i >= 500 && i != 900) {
                history.addOff(1, START + i * STEP);
            } else {
                history.add(1, activity(START + i * STEP, 5));
            }
        }
        List<ActivityHistory.Point> points = history.points(1, START, START + 999 * STEP, 10);
        assertEquals(5, points.get(2).value(Metric.ENTITIES));
        assertTrue(points.get(7).off());
        assertEquals(5, points.get(9).value(Metric.ENTITIES));
    }

    @Test
    void brokenLinesAreSkipped() {
        ActivityHistory history = new ActivityHistory();
        history.restore(Map.of(START, "4:1,2,3;oops;x:1;35:1,nonsense"));
        assertEquals(1, history.points(4, 0, START, 300).size());
        assertEquals(0, history.points(35, 0, START, 300).size());
    }

    @Test
    void deletedLoadersAreForgotten() {
        ActivityHistory history = new ActivityHistory();
        history.add(1, activity(START, 1));
        history.add(2, activity(START, 1));
        history.retain(id -> id == 2);
        assertEquals(0, history.points(1, 0, START, 300).size());
        assertEquals(1, history.points(2, 0, START, 300).size());
    }
}
