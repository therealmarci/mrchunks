package at.marcelr.mrchunks.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses and formats durations like "1d2h30m10s". */
public final class DurationUtil {

    private static final Pattern PART = Pattern.compile("(\\d+)([wdhms])");
    private static final Pattern FULL = Pattern.compile("(\\d+[wdhms])+");

    private DurationUtil() {
    }

    /** @return duration in milliseconds, or -1 if the input is invalid */
    public static long parse(String input) {
        if (input == null) {
            return -1;
        }
        String s = input.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        if (s.isEmpty()) {
            return -1;
        }
        try {
            if (s.chars().allMatch(Character::isDigit)) {
                return Math.multiplyExact(Long.parseLong(s), 60_000L);
            }
            if (!FULL.matcher(s).matches()) {
                return -1;
            }
            long total = 0;
            Matcher m = PART.matcher(s);
            while (m.find()) {
                long value = Long.parseLong(m.group(1));
                long unit = switch (m.group(2)) {
                    case "w" -> 604_800_000L;
                    case "d" -> 86_400_000L;
                    case "h" -> 3_600_000L;
                    case "m" -> 60_000L;
                    default -> 1_000L;
                };
                total = Math.addExact(total, Math.multiplyExact(value, unit));
            }
            return total;
        } catch (ArithmeticException | NumberFormatException e) {
            return -1;
        }
    }

    public static String format(long millis) {
        long seconds = Math.max(0, (millis + 999) / 1000);
        long d = seconds / 86_400;
        long h = seconds % 86_400 / 3_600;
        long m = seconds % 3_600 / 60;
        long s = seconds % 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("d ");
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (s > 0 || sb.isEmpty()) sb.append(s).append("s");
        return sb.toString().trim();
    }
}
