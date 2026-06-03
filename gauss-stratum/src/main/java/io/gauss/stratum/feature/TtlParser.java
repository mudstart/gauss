package io.gauss.stratum.feature;

import java.time.Duration;

/**
 * Parses the ISO-8601-like TTL strings used in
 * {@link io.gauss.core.annotation.Feature#ttl()} (e.g. {@code "1h"}, {@code "30m"},
 * {@code "7d"}, {@code "60s"}).
 */
public final class TtlParser {

    private TtlParser() {}

    /**
     * Parses a TTL string into a {@link Duration}.
     *
     * <p>Supported units:
     * <ul>
     *   <li>{@code d} — days</li>
     *   <li>{@code h} — hours</li>
     *   <li>{@code m} — minutes</li>
     *   <li>{@code s} — seconds</li>
     * </ul>
     *
     * @param ttl the TTL string (e.g. {@code "1h"}, {@code "30m"})
     * @return the parsed duration
     * @throws IllegalArgumentException if the format is not recognised
     */
    public static Duration parse(String ttl) {
        if (ttl == null || ttl.isBlank()) {
            throw new IllegalArgumentException("TTL must not be blank");
        }
        String s    = ttl.trim();
        char   unit = s.charAt(s.length() - 1);
        String num  = s.substring(0, s.length() - 1);
        try {
            long value = Long.parseLong(num);
            return switch (unit) {
                case 'd' -> Duration.ofDays(value);
                case 'h' -> Duration.ofHours(value);
                case 'm' -> Duration.ofMinutes(value);
                case 's' -> Duration.ofSeconds(value);
                default  -> throw new IllegalArgumentException(
                        "Unknown TTL unit '" + unit + "' in '" + ttl
                                + "'. Supported: d, h, m, s");
            };
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid TTL format '" + ttl + "': number part '" + num + "' is not numeric");
        }
    }
}
