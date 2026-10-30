package at.marcelr.mrchunks.lag;

/**
 * A loader locked because of lag. Only admins can lift it.
 *
 * @param since     when it was locked (ms)
 * @param metric    what was too high (for a lock after repeated shutdowns: the loader's worst metric then)
 * @param value     the measured value
 * @param limit     the lock limit at that time
 * @param notified  whether the owner has been told (otherwise on their next join)
 * @param shutdowns 0 = over its own limits; otherwise locked after this many shutdowns by the staged shutdown
 */
public record LagLock(long since, Metric metric, double value, double limit, boolean notified, int shutdowns) {

    public LagLock withNotified() {
        return new LagLock(since, metric, value, limit, true, shutdowns);
    }

    /** Locked because the staged shutdown had to disable it too often (not because of its own limits). */
    public boolean repeated() {
        return shutdowns > 0;
    }
}
