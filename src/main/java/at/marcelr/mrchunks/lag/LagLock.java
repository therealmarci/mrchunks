package at.marcelr.mrchunks.lag;

/**
 * A loader locked because of lag. Only admins can lift it.
 *
 * @param since    when it was locked (ms)
 * @param metric   what was too high
 * @param value    the measured value
 * @param limit    the lock limit at that time
 * @param notified whether the owner has been told (otherwise on their next join)
 */
public record LagLock(long since, Metric metric, double value, double limit, boolean notified) {

    public LagLock withNotified() {
        return new LagLock(since, metric, value, limit, true);
    }
}
