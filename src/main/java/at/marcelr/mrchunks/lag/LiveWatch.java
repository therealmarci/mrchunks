package at.marcelr.mrchunks.lag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which loaders someone is looking at in the web interface right now. Measuring them faster is only worth it while
 * someone looks: whoever asks keeps a loader in here, without a request for {@link #IDLE_MILLIS} it drops out. However
 * many people look at a loader, it counts once. No Bukkit types, so it can be tested on its own.
 */
final class LiveWatch {

    /** A page in a tab in the background stops asking; this long later the loader is not measured any more. */
    static final long IDLE_MILLIS = 5_000L;

    /** loader id -> last request */
    private final Map<Integer, Long> watched = new LinkedHashMap<>();

    /** @return whether the loader is watched now; false if {@code max} other loaders already are */
    boolean watch(int loader, long now, int max) {
        if (!watched.containsKey(loader) && watched.size() >= max) {
            return false;
        }
        watched.put(loader, now);
        return true;
    }

    /** Removes the loaders nobody asked about for a while. @return their ids */
    List<Integer> expire(long now) {
        List<Integer> idle = new ArrayList<>();
        watched.forEach((loader, seen) -> {
            if (now - seen >= IDLE_MILLIS) {
                idle.add(loader);
            }
        });
        idle.forEach(watched::remove);
        return idle;
    }

    Set<Integer> loaders() {
        return Set.copyOf(watched.keySet());
    }

    boolean contains(int loader) {
        return watched.containsKey(loader);
    }

    int size() {
        return watched.size();
    }

    void clear() {
        watched.clear();
    }
}
