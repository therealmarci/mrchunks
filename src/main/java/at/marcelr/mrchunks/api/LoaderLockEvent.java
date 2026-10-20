package at.marcelr.mrchunks.api;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.NonNull;

import java.util.Map;

/**
 * Fired after a chunk loader was locked because of lag, or after its lock was lifted. Other plugins (MRadmin opens a
 * ticket for the owner) can listen without depending on MRchunks: everything is in {@link #data()} as plain values.
 * <p>
 * Keys: {@code type} ("lock" / "unlock"), {@code loader} (id, String), {@code number} (Integer), {@code owner} (UUID),
 * {@code owner-name}, {@code name} (display name of the loader), {@code world}, {@code x}, {@code y}, {@code z}
 * (Integer), {@code by} (who lifted the lock, unlock only), {@code details} (the lock as readable text in MRchunks'
 * language, several lines, lock only).
 */
public final class LoaderLockEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Map<String, Object> data;

    public LoaderLockEvent(Map<String, Object> data) {
        this.data = Map.copyOf(data);
    }

    public Map<String, Object> data() {
        return data;
    }

    @Override
    public @NonNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
