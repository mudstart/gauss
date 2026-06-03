package io.gauss.vigil.rollback;

/**
 * Immutable configuration extracted from an
 * {@link io.gauss.core.annotation.AutoRollback} annotation (HU-054).
 *
 * @param metricName      name of the metric to monitor (e.g., {@code "error_rate"})
 * @param threshold       value above which rollback is triggered
 * @param windowMinutes   sliding-window duration in minutes
 * @param maxPerHour      maximum automatic rollbacks allowed per hour
 */
public record RollbackPolicy(
        String metricName,
        double threshold,
        int    windowMinutes,
        int    maxPerHour
) {

    public static RollbackPolicy of(String metricName, double threshold) {
        return new RollbackPolicy(metricName, threshold, 10, 3);
    }

    public static RollbackPolicy of(String metricName, double threshold,
                                     int windowMinutes, int maxPerHour) {
        return new RollbackPolicy(metricName, threshold, windowMinutes, maxPerHour);
    }
}
