package at.marcelr.mrchunks.lag;

/**
 * What is measured in the area of a loader. Counts ({@link #rate} false) are taken at every sample, rates are events
 * per second over the last sample interval.
 */
public enum Metric {
    /** Entities (mobs, animals, minecarts, armor stands ... - not players), dropped items included. */
    ENTITIES("entities", false),
    /** Dropped items lying on the ground. */
    ITEMS("items", false),
    /** Redstone signal changes per second. */
    REDSTONE("redstone", true),
    /** Items moved by hoppers, droppers, hopper minecarts ... per second. */
    HOPPERS("hoppers", true),
    /** Piston extends and retracts per second. */
    PISTONS("pistons", true),
    /** New mobs and items per second. */
    SPAWNS("spawns", true);

    private final String key;
    private final boolean rate;

    Metric(String key, boolean rate) {
        this.key = key;
        this.rate = rate;
    }

    /** Key in config.yml (lag-protection.limits.&lt;key&gt;) and in the language file (lag-metric-&lt;key&gt;). */
    public String key() {
        return key;
    }

    public boolean rate() {
        return rate;
    }

    public static Metric byKey(String key) {
        for (Metric metric : values()) {
            if (metric.key.equalsIgnoreCase(key)) {
                return metric;
            }
        }
        return null;
    }
}
