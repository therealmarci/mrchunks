package at.marcelr.mrchunks.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Version helpers - identical in all MRtechnology plugins (only the package differs; check with
 * {@code ##OTHER/Scripts/check-versions.sh}).
 *
 * <p>Published versions since the new scheme: {@code mc26.3-STA-1.4.0-bu030} (stable) and
 * {@code mc26.3-BET-1.4.1-bu001} (beta) - Minecraft version, channel, {@code MAJOR.MINOR.PATCH} and a build number
 * that counts the releases of one {@code MAJOR.MINOR.PATCH}. A stable release closes its version, the next beta
 * gets the next patch. Repositories with plugin and mod put {@code plugin-} / {@code mod-} in front.
 *
 * <p>Older releases look like {@code mc26.3-v1.2.0} (Semantic Versioning); 0.x and versions with a suffix
 * ({@code 1.0.0-rc.1}) count as beta. Both formats are read and compared with each other; an old version counts as
 * build 0 of its number. The order is MAJOR, MINOR, PATCH, BUILD - the channel only decides who gets a release.
 * Build metadata after "+" ({@code 1.2.0+mc26.3} in the plugin.yml) is ignored.
 */
public final class Versions {

    public enum Channel { STABLE, BETA }

    private static final Pattern COMPONENT = Pattern.compile("^(plugin|mod)-", Pattern.CASE_INSENSITIVE);
    private static final Pattern MC_PREFIX = Pattern.compile("^[mM][cC]([0-9][0-9.]*)-");
    private static final Pattern MC_SUFFIX = Pattern.compile("\\+(?:.*\\.)?[mM][cC]([0-9][0-9.]*)$");
    private static final Pattern SCHEME = Pattern.compile(
            "^(STA|BET)-([0-9]+)(?:\\.([0-9]+))?(?:\\.([0-9]+))?-BU([0-9]+)$", Pattern.CASE_INSENSITIVE);

    private Versions() {
    }

    /** A version split into its parts; {@code pre} is the old SemVer suffix ("rc.1"), {@code build} 0 for old ones. */
    private record Parsed(boolean scheme, Channel channel, String core, int build, String pre) {
    }

    /**
     * The Minecraft version of a version string: "mc26.3-STA-1.4.0-bu001", "mc26.3-v1.2.0" (tag) or "1.2.0+mc26.3"
     * (plugin.yml) -&gt; "26.3". Null for plain versions ("v1.0.0").
     */
    public static String minecraft(String version) {
        Matcher m = MC_PREFIX.matcher(withoutComponent(version));
        if (m.find()) {
            return m.group(1);
        }
        Matcher suffix = MC_SUFFIX.matcher(version.trim());
        return suffix.find() ? suffix.group(1) : null;
    }

    /**
     * The plugin version without component, Minecraft prefix, "v" and build metadata:
     * "mc26.3-sta-1.4.0-bu30" -&gt; "STA-1.4.0-bu030", "mc26.3-v1.2.0" -&gt; "1.2.0".
     */
    public static String plugin(String version) {
        String v = normalize(version);
        Matcher m = SCHEME.matcher(v);
        return m.matches() ? format(channelOf(m.group(1)), core(m), Integer.parseInt(m.group(5))) : v;
    }

    /** "STA-1.4.0-bu030" from its parts (the build has at least three digits). */
    public static String format(Channel channel, String number, int build) {
        return (channel == Channel.STABLE ? "STA-" : "BET-") + number + "-bu" + String.format(Locale.ROOT, "%03d", build);
    }

    /**
     * The published version: {@code full("26.3", "STA-1.4.0-bu001")} -&gt; "mc26.3-STA-1.4.0-bu001",
     * old ones {@code full("26.3", "1.2.0")} -&gt; "mc26.3-v1.2.0".
     */
    public static String full(String minecraft, String plugin) {
        String p = plugin(plugin);
        return "mc" + minecraft + "-" + (isScheme(p) ? p : "v" + p);
    }

    /** Whether the version uses the new scheme (STA/BET + build). */
    public static boolean isScheme(String version) {
        return parse(version).scheme();
    }

    /** STABLE or BETA. Old versions: 0.x and versions with a suffix are beta. */
    public static Channel channel(String version) {
        return parse(version).channel();
    }

    /** Whether the version is a beta (old name, kept for the callers). */
    public static boolean isPrerelease(String version) {
        return channel(version) == Channel.BETA;
    }

    /** "1.4.0" of "mc26.3-BET-1.4.0-bu002" or "v1.4.0-rc.1". */
    public static String number(String version) {
        return parse(version).core();
    }

    /** The build number, 0 for old versions. */
    public static int build(String version) {
        return parse(version).build();
    }

    /** MAJOR, MINOR, PATCH as numbers; missing or unreadable parts are 0. */
    public static int[] parts(String version) {
        String[] ids = number(version).split("\\.");
        int[] result = new int[3];
        for (int i = 0; i < 3 && i < ids.length; i++) {
            result[i] = isNumber(ids[i]) && ids[i].length() < 10 ? Integer.parseInt(ids[i]) : 0;
        }
        return result;
    }

    /**
     * Whether a server with {@code installed} should also be offered betas. Betas only when switched on - except
     * for 0.x, which only exists as beta. Whoever switches betas off stays on their beta until a newer stable comes
     * (never downgraded, because config and language migrations cannot be undone).
     */
    public static boolean wantsBeta(String installed, boolean betaEnabled) {
        return betaEnabled || installed != null && parts(installed)[0] == 0;
    }

    /**
     * Compares two versions: MAJOR, MINOR, PATCH numerically ("0.10.0" &gt; "0.9.2"), then the build
     * ("BET-1.4.0-bu029" &lt; "STA-1.4.0-bu030", an old "1.4.0" is build 0). Old SemVer suffixes: a release is newer
     * than a pre-release ("1.0.0" &gt; "1.0.0-rc.1"), suffixes are compared per dot-separated identifier ("rc.10" &gt;
     * "rc.2").
     *
     * @return negative, zero or positive like {@link Comparable#compareTo}
     */
    public static int compare(String a, String b) {
        Parsed x = parse(a);
        Parsed y = parse(b);
        int result = compareIdentifiers(x.core(), y.core(), true);
        if (result != 0) {
            return result;
        }
        result = Integer.compare(x.build(), y.build());
        if (result != 0) {
            return result;
        }
        boolean preX = x.pre() != null;
        boolean preY = y.pre() != null;
        if (preX != preY) {
            return preX ? -1 : 1;
        }
        return preX ? compareIdentifiers(x.pre(), y.pre(), false) : 0;
    }

    /** "plugin" or "mod" for a release tag with that prefix, otherwise null. */
    public static String component(String tag) {
        Matcher m = COMPONENT.matcher(tag.trim());
        return m.find() ? m.group(1).toLowerCase(Locale.ROOT) : null;
    }

    // ------------------------------------------------------------------ internals

    private static Parsed parse(String version) {
        String v = normalize(version);
        Matcher m = SCHEME.matcher(v);
        if (m.matches()) {
            return new Parsed(true, channelOf(m.group(1)), core(m), Integer.parseInt(m.group(5)), null);
        }
        String[] split = v.split("-", 2);
        String pre = split.length > 1 ? split[1] : null;
        boolean beta = pre != null || split[0].equals("0") || split[0].startsWith("0.");
        return new Parsed(false, beta ? Channel.BETA : Channel.STABLE, split[0], 0, pre);
    }

    private static String core(Matcher m) {
        return Integer.parseInt(m.group(2)) + "." + (m.group(3) == null ? 0 : Integer.parseInt(m.group(3)))
                + "." + (m.group(4) == null ? 0 : Integer.parseInt(m.group(4)));
    }

    private static Channel channelOf(String code) {
        return code.equalsIgnoreCase("STA") ? Channel.STABLE : Channel.BETA;
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

    private static String withoutComponent(String version) {
        return COMPONENT.matcher(version.trim()).replaceFirst("");
    }

    private static String normalize(String version) {
        String v = MC_PREFIX.matcher(withoutComponent(version)).replaceFirst("");
        int plus = v.indexOf('+');
        if (plus >= 0) {
            v = v.substring(0, plus);
        }
        if (!SCHEME.matcher(v).matches() && (v.startsWith("v") || v.startsWith("V"))) {
            v = v.substring(1);
        }
        return v;
    }
}
