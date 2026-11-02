package at.marcelr.mrchunks.lag;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

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
    void deletedLoadersAreForgotten() {
        ActivityHistory history = new ActivityHistory();
        history.add(1, activity(START, 1));
        history.add(2, activity(START, 1));
        history.retain(id -> id == 2);
        assertEquals(0, history.points(1, 0, START, 300).size());
        assertEquals(1, history.points(2, 0, START, 300).size());
    }
}
