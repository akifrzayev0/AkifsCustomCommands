package me.akif.customcommands.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tiny helpers for converting between epoch seconds and display
 * strings, plus a tolerant duration parser used by /cac.
 */
public final class TimeUtil {

    /**
     * Matches a single number+unit pair like {@code 24h}, {@code 30m} or
     * {@code 7d}. The unit is optional; a bare number is treated as seconds.
     */
    private static final Pattern DURATION_TOKEN = Pattern.compile("(\\d+)([smhdwSMHDW]?)");

    private TimeUtil() {
    }

    /**
     * Formats a duration in seconds as e.g. "1d 2h 3m 4s".
     */
    public static String formatSeconds(long totalSeconds) {
        if (totalSeconds <= 0L) {
            return "0s";
        }
        long days = totalSeconds / 86400L;
        long hours = (totalSeconds % 86400L) / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0 || sb.length() == 0) sb.append(seconds).append("s");
        return sb.toString().trim();
    }

    public static long nowSeconds() {
        return System.currentTimeMillis() / 1000L;
    }

    /**
     * Parses a human-friendly duration string into seconds.
     *
     * <p>Accepts:
     * <ul>
     *   <li>Plain numbers ({@code 86400} → 86400 seconds)</li>
     *   <li>Single units ({@code 24h}, {@code 1d}, {@code 30m}, {@code 45s}, {@code 1w})</li>
     *   <li>Combined units ({@code 1d12h}, {@code 2h30m}, {@code 1w2d3h})</li>
     *   <li>{@code 0} to disable the timer</li>
     * </ul>
     *
     * @throws NumberFormatException if the input is malformed.
     */
    public static long parseDurationSeconds(String input) {
        if (input == null || input.isEmpty()) {
            throw new NumberFormatException("Empty duration");
        }
        String s = input.trim().toLowerCase();

        // Allow plain signed integers for backwards compatibility.
        if (s.matches("-?\\d+")) {
            return Long.parseLong(s);
        }

        Matcher m = DURATION_TOKEN.matcher(s);
        long total = 0L;
        int cursor = 0;
        boolean any = false;
        while (m.find()) {
            if (m.start() != cursor) {
                throw new NumberFormatException("Invalid duration: " + input);
            }
            long num = Long.parseLong(m.group(1));
            String unit = m.group(2);
            long multiplier;
            switch (unit) {
                case "":
                case "s":
                    multiplier = 1L; break;
                case "m":
                    multiplier = 60L; break;
                case "h":
                    multiplier = 3600L; break;
                case "d":
                    multiplier = 86400L; break;
                case "w":
                    multiplier = 604800L; break;
                default:
                    throw new NumberFormatException("Unknown unit '" + unit + "' in: " + input);
            }
            total += num * multiplier;
            cursor = m.end();
            any = true;
        }
        if (!any || cursor != s.length()) {
            throw new NumberFormatException("Invalid duration: " + input);
        }
        return total;
    }
}
