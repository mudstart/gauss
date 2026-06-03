package io.gauss.vigil.health;

import java.time.Instant;
import java.util.List;

/**
 * Aggregated health report for the whole Gauss application (HU-035).
 *
 * <p>The {@code overall} status is computed as:
 * <ul>
 *   <li>{@link HealthStatus#DOWN} if any component is DOWN</li>
 *   <li>{@link HealthStatus#DEGRADED} if any component is DEGRADED or UNKNOWN</li>
 *   <li>{@link HealthStatus#UP} otherwise</li>
 * </ul>
 *
 * @param overall    aggregate status
 * @param components per-component health entries
 * @param checkedAt  time the report was generated
 */
public record GaussHealthReport(
        HealthStatus           overall,
        List<ComponentHealth>  components,
        Instant                checkedAt
) {

    /**
     * Derives the overall status from a list of component health entries.
     */
    public static HealthStatus aggregate(List<ComponentHealth> components) {
        if (components.stream().anyMatch(c -> c.status() == HealthStatus.DOWN)) {
            return HealthStatus.DOWN;
        }
        if (components.stream().anyMatch(c ->
                c.status() == HealthStatus.DEGRADED || c.status() == HealthStatus.UNKNOWN)) {
            return HealthStatus.DEGRADED;
        }
        return HealthStatus.UP;
    }
}
