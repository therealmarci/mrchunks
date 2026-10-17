package at.marcelr.mrchunks.util;

/**
 * Version helpers following Semantic Versioning: {@code MAJOR.MINOR.PATCH[-prerelease][+build]}.
 * A leading "v" (as in Git tags) is ignored, build metadata after "+" too.
 */
public final class Versions {

    private Versions() {
    }

    /** 0.x versions and versions with a suffix ("1.0.0-rc.1") are pre-releases. */
    public static boolean isPrerelease(String version) {
        String v = normalize(version);
        return v.startsWith("0.") || v.contains("-");
    }

    /**
     * Compares two versions. Numbers are compared numerically ("0.10.0" &gt; "0.9.2"); with equal numbers a release
     * is newer than a pre-release ("1.0.0" &gt; "1.0.0-rc.1"), and pre-release parts are compared
     * per dot-separated identifier, numerically where possible ("rc.10" &gt; "rc.2").
     *
     * @return negative, zero or positive like {@link Comparable#compareTo}
     */
    public static int compare(String a, String b) {
        String[] partsA = normalize(a).split("-", 2);
        String[] partsB = normalize(b).split("-", 2);

        int core = compareIdentifiers(partsA[0], partsB[0], true);
        if (core != 0) {
            return core;
        }
        boolean preA = partsA.length > 1;
        boolean preB = partsB.length > 1;
        if (preA != preB) {
            return preA ? -1 : 1;
        }
        return preA ? compareIdentifiers(partsA[1], partsB[1], false) : 0;
    }

    /** Compares dot-separated identifiers; missing core numbers count as 0 ("1.0" == "1.0.0"). */
    private static int compareIdentifiers(String a, String b, boolean core) {
        String[] idsA = a.split("\\.");
        String[] idsB = b.split("\\.");
        for (int i = 0; i < Math.max(idsA.length, idsB.length); i++) {
            if (!core && (i >= idsA.length || i >= idsB.length)) {
                // "rc" < "rc.1": the shorter pre-release is older
                return Integer.compare(idsA.length, idsB.length);
            }
            String x = i < idsA.length ? idsA[i] : "0";
            String y = i < idsB.length ? idsB[i] : "0";
            boolean numX = isNumber(x);
            boolean numY = isNumber(y);
            int result;
            if (numX && numY) {
                result = Long.compare(Long.parseLong(x), Long.parseLong(y));
            } else if (numX != numY) {
                result = numX ? -1 : 1; // numeric identifiers sort before text ones
            } else {
                result = x.compareTo(y);
            }
            if (result != 0) {
                return result;
            }
        }
        return 0;
    }

    private static boolean isNumber(String value) {
        return !value.isEmpty() && value.length() < 19 && value.chars().allMatch(Character::isDigit);
    }

    private static String normalize(String version) {
        String v = version.trim();
        if (v.startsWith("v") || v.startsWith("V")) {
            v = v.substring(1);
        }
        int plus = v.indexOf('+');
        return plus >= 0 ? v.substring(0, plus) : v;
    }
}
