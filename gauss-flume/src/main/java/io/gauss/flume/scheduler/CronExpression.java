package io.gauss.flume.scheduler;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * A parsed, immutable 5-field cron expression with {@link #matches} and
 * {@link #nextFire} capabilities (Flume module, HU-013).
 *
 * <p>Supported field syntax (5 space-separated fields:
 * {@code minute hour day-of-month month day-of-week}):
 * <ul>
 *   <li>{@code *}          — every value</li>
 *   <li>{@code 5}          — exact value</li>
 *   <li>{@code 1-5}        — inclusive range</li>
 *   <li>{@code 1,3,5}      — comma-separated list</li>
 *   <li>{@code *}{@code /2} — step over full range</li>
 *   <li>{@code 0/15}       — step starting at offset</li>
 * </ul>
 *
 * <p>Day-of-week uses 0–7 where both 0 and 7 represent Sunday.
 *
 * <pre>{@code
 * CronExpression expr = CronExpression.parse("0 2 * * *");
 * LocalDateTime next = expr.nextFire(LocalDateTime.now());
 * }</pre>
 */
public final class CronExpression {

    private static final int FIELDS = 5;

    private final String expression;
    private final int[]  minutes;   // 0-59
    private final int[]  hours;     // 0-23
    private final int[]  days;      // 1-31
    private final int[]  months;    // 1-12
    private final int[]  weekdays;  // 0-7 (0==7==Sunday)

    // -------------------------------------------------------------------------

    private CronExpression(String expression,
                            int[] minutes, int[] hours,
                            int[] days, int[] months, int[] weekdays) {
        this.expression = expression;
        this.minutes    = minutes;
        this.hours      = hours;
        this.days       = days;
        this.months     = months;
        this.weekdays   = weekdays;
    }

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    /**
     * Parses and validates a 5-field cron expression.
     *
     * @param expression the cron string
     * @return a parsed {@code CronExpression}
     * @throws IllegalArgumentException if the format is invalid
     */
    public static CronExpression parse(String expression) {
        Objects.requireNonNull(expression, "expression must not be null");
        String trimmed = expression.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Cron expression must not be blank");
        }
        String[] fields = trimmed.split("\\s+");
        if (fields.length != FIELDS) {
            throw new IllegalArgumentException(
                    "Cron expression must have exactly 5 fields but got " + fields.length
                    + ": '" + expression + "'");
        }
        return new CronExpression(
                trimmed,
                parseField(fields[0], 0, 59,  "minute"),
                parseField(fields[1], 0, 23,  "hour"),
                parseField(fields[2], 1, 31,  "day-of-month"),
                parseField(fields[3], 1, 12,  "month"),
                parseField(fields[4], 0, 7,   "day-of-week")
        );
    }

    /**
     * Validates that {@code expression} is a syntactically correct 5-field
     * cron expression, throwing {@link IllegalArgumentException} if not.
     */
    public static void validate(String expression) {
        parse(expression);  // parse does all the work; discard result
    }

    // -------------------------------------------------------------------------
    // Matching and scheduling
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if this expression matches the given date/time.
     * The seconds component of {@code dt} is ignored.
     */
    public boolean matches(LocalDateTime dt) {
        int dow = dt.getDayOfWeek().getValue() % 7;  // ISO: Mon=1..Sun=7 -> 0..6
        return contains(minutes,  dt.getMinute())
            && contains(hours,    dt.getHour())
            && contains(days,     dt.getDayOfMonth())
            && contains(months,   dt.getMonthValue())
            && (contains(weekdays, dow) || contains(weekdays, dow == 0 ? 7 : dow));
    }

    /**
     * Returns the next {@link LocalDateTime} on or after {@code from + 1 minute}
     * that satisfies this expression.
     *
     * @param from   the starting point (exclusive)
     * @return the next fire time
     * @throws IllegalStateException if no fire time is found within one calendar year
     */
    public LocalDateTime nextFire(LocalDateTime from) {
        LocalDateTime candidate = from.plusMinutes(1).truncatedTo(ChronoUnit.MINUTES);
        for (int i = 0; i < 525_600; i++) {   // max 1 year of minutes
            if (matches(candidate)) return candidate;
            candidate = candidate.plusMinutes(1);
        }
        throw new IllegalStateException(
                "No next fire time found within 1 year for expression: " + expression);
    }

    /** Returns the original cron string. */
    public String expression() {
        return expression;
    }

    @Override
    public String toString() {
        return "CronExpression[" + expression + "]";
    }

    // -------------------------------------------------------------------------
    // Field parsing
    // -------------------------------------------------------------------------

    private static int[] parseField(String field, int min, int max, String fieldName) {
        try {
            if ("*".equals(field)) {
                return range(min, max);
            }
            if (field.startsWith("*/")) {
                int step = Integer.parseInt(field.substring(2));
                validateStep(step, field);
                return step(min, max, min, step);
            }
            if (field.contains("/")) {
                String[] parts = field.split("/", 2);
                int start = Integer.parseInt(parts[0]);
                int step  = Integer.parseInt(parts[1]);
                validateStep(step, field);
                validateRange(start, min, max, field);
                return step(min, max, start, step);
            }
            if (field.contains(",")) {
                return Arrays.stream(field.split(","))
                        .mapToInt(s -> {
                            int v = Integer.parseInt(s.trim());
                            validateRange(v, min, max, field);
                            return v;
                        })
                        .sorted()
                        .distinct()
                        .toArray();
            }
            if (field.contains("-")) {
                String[] parts = field.split("-", 2);
                int lo = Integer.parseInt(parts[0]);
                int hi = Integer.parseInt(parts[1]);
                validateRange(lo, min, max, field);
                validateRange(hi, min, max, field);
                if (lo > hi) throw new IllegalArgumentException(
                        "Range start > end in '" + field + "'");
                return range(lo, hi);
            }
            // Single literal
            int v = Integer.parseInt(field);
            validateRange(v, min, max, field);
            return new int[]{v};

        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid cron field '" + field + "' for " + fieldName + ": " + e.getMessage(), e);
        }
    }

    private static int[] range(int lo, int hi) {
        int[] arr = new int[hi - lo + 1];
        for (int i = 0; i < arr.length; i++) arr[i] = lo + i;
        return arr;
    }

    private static int[] step(int min, int max, int start, int step) {
        List<Integer> vals = new ArrayList<>();
        for (int v = start; v <= max; v += step) {
            if (v >= min) vals.add(v);
        }
        return vals.stream().mapToInt(Integer::intValue).toArray();
    }

    private static void validateStep(int step, String field) {
        if (step <= 0) throw new IllegalArgumentException(
                "Step must be positive in '" + field + "'");
    }

    private static void validateRange(int v, int min, int max, String field) {
        if (v < min || v > max) throw new IllegalArgumentException(
                "Value " + v + " out of range [" + min + "," + max + "] in '" + field + "'");
    }

    private static boolean contains(int[] arr, int val) {
        for (int v : arr) if (v == val) return true;
        return false;
    }
}
