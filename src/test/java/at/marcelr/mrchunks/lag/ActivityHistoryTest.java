package at.marcelr.mrchunks.lag;

import at.marcelr.mrchunks.lag.ActivityHistory.Phase;
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
        round.put(4, history.addPhase(4, START, Phase.OFF));

        ActivityHistory restored = new ActivityHistory();
        restored.restore(new TreeMap<>(Map.of(START - STEP, "4:9,0,0,0,0,0", START, ActivityHistory.encode(round))));

        assertEquals(List.of(new ActivityHistory.Span(START, START + STEP, Phase.OFF)), restored.phases(4, 0, START + STEP));
        assertEquals(9, restored.last(4).value(Metric.ENTITIES));
    }

    @Test
    void aShortPhaseStaysWithItsExactTimes() {
        ActivityHistory history = new ActivityHistory();
        for (int i = 0; i < 1000; i++) {
            history.add(1, activity(START + i * STEP, 5));
            if (i == 500) {
                // Off and on again between two rounds: no round ever saw it.
                history.addPhase(1, START + i * STEP + 1000, Phase.OFF);
                history.addPhase(1, START + i * STEP + 3000, Phase.ON);
            }
        }
        long end = START + 999 * STEP;
        assertEquals(List.of(new ActivityHistory.Span(START + 500 * STEP + 1000, START + 500 * STEP + 3000, Phase.OFF)),
                history.phases(1, START, end));
        assertTrue(history.points(1, START, end, 10).stream().allMatch(ActivityHistory.Point::measured));
    }

    @Test
    void aPhaseIsCutToTheRangeAndRunsUntilNowWhileOff() {
        ActivityHistory history = new ActivityHistory();
        history.add(1, activity(START, 5));
        history.addPhase(1, START + STEP, Phase.OFF);
        history.addPhase(1, START + 2 * STEP, Phase.OFF);
        long now = START + 10 * STEP;
        assertEquals(List.of(new ActivityHistory.Span(START + 5 * STEP, now, Phase.OFF)), history.phases(1, START + 5 * STEP, now));
    }

    @Test
    void aPhaseBeforeTheRangeIsLeftOut() {
        ActivityHistory history = new ActivityHistory();
        history.addPhase(1, START, Phase.OFF);
        history.addPhase(1, START + STEP, Phase.ON);
        assertEquals(List.of(), history.phases(1, START + 2 * STEP, START + 3 * STEP));
    }

    @Test
    void anOnEntryIsNoMeasurement() {
        ActivityHistory history = new ActivityHistory();
        history.add(1, activity(START, 5));
        history.addPhase(1, START + STEP, Phase.ON);
        assertEquals(5, history.last(1).value(Metric.ENTITIES));
        assertEquals(1, history.points(1, 0, START + STEP, 300).size());
    }

    @Test
    void phasesKeepTheirKindAndALockedLoaderKeepsItsMeasurements() {
        ActivityHistory history = new ActivityHistory();
        Map<Integer, ActivityHistory.Point> round = new LinkedHashMap<>();
        round.put(4, history.add(4, activity(START, 8), Phase.LOCKED));

        ActivityHistory restored = new ActivityHistory();
        restored.restore(new TreeMap<>(Map.of(START - STEP, "4:shed", START, ActivityHistory.encode(round),
                START + STEP, "4:on")));

        assertEquals(List.of(new ActivityHistory.Span(START - STEP, START, Phase.SHED),
                new ActivityHistory.Span(START, START + STEP, Phase.LOCKED)), restored.phases(4, 0, START + 2 * STEP));
        assertEquals(8, restored.last(4).value(Metric.ENTITIES));
    }

    /** Times of the points, in units of STEP after START. */
    private static List<ActivityHistory.Point> at(int... steps) {
        List<ActivityHistory.Point> points = new java.util.ArrayList<>();
        for (int step : steps) {
            points.add(new ActivityHistory().add(1, activity(START + step * STEP, step)));
        }
        return points;
    }

    private static List<Long> steps(List<ActivityHistory.Point> points) {
        return points.stream().map(p -> (p.time() - START) / STEP).toList();
    }

    @Test
    void liveMeasurementsReplaceTheNormalOnesOnlyWhereTheyExist() {
        // Live view around 5-7 and 20-21: the normal points between stay.
        List<ActivityHistory.Point> merged = ActivityHistory.merged(at(0, 3, 6, 9, 12, 15, 18, 21, 24),
                at(5, 6, 7, 20, 21), STEP);
        assertEquals(List.of(0L, 3L, 5L, 6L, 7L, 9L, 12L, 15L, 18L, 20L, 21L, 24L), steps(merged));
    }

    @Test
    void theLastNormalPointBeforeTheLiveViewStays() {
        // Opened shortly after a normal measurement: no gap between it and the first live one. Inside the stretch a
        // normal measurement drops out.
        assertEquals(List.of(0L, 8L, 9L, 10L, 11L, 12L), steps(ActivityHistory.merged(at(0, 10), at(8, 9, 10, 11, 12), STEP)));
        assertEquals(List.of(0L, 8L, 10L, 12L), steps(ActivityHistory.merged(at(0, 9), at(8, 10, 12), 2 * STEP)));
        assertEquals(List.of(0L, 1L, 2L, 9L), steps(ActivityHistory.merged(at(0, 9), at(1, 2), STEP)));
    }

    @Test
    void withoutLiveMeasurementsNothingChanges() {
        assertEquals(List.of(0L, 3L), steps(ActivityHistory.merged(at(0, 3), List.of(), STEP)));
        assertEquals(List.of(1L, 2L), steps(ActivityHistory.merged(List.of(), at(1, 2), STEP)));
    }

    @Test
    void liveEntriesAreDroppedEvenIfNoNewOnesCome() {
        ActivityHistory live = new ActivityHistory(10 * STEP);
        live.add(1, activity(START, 1));
        live.trim(START + 5 * STEP);
        assertEquals(1, live.points(1, 0, START, 300).size());
        live.trim(START + 11 * STEP);
        assertEquals(0, live.points(1, 0, START, 300).size());
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
