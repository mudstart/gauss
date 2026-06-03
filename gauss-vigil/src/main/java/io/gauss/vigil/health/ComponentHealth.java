package io.gauss.vigil.health;

import java.util.Map;

/**
 * Health report for a single Gauss component (HU-035).
 *
 * @param name    component identifier (e.g. {@code "model-registry"}, {@code "pipeline"})
 * @param status  current health status
 * @param message human-readable status description
 * @param details additional key/value pairs surfaced in the health endpoint
 */
public record ComponentHealth(
        String              name,
        HealthStatus        status,
        String              message,
        Map<String, Object> details
) {

    public static ComponentHealth up(String name, String message) {
        return new ComponentHealth(name, HealthStatus.UP, message, Map.of());
    }

    public static ComponentHealth up(String name, String message, Map<String, Object> details) {
        return new ComponentHealth(name, HealthStatus.UP, message, Map.copyOf(details));
    }

    public static ComponentHealth down(String name, String message) {
        return new ComponentHealth(name, HealthStatus.DOWN, message, Map.of());
    }

    public static ComponentHealth down(String name, String message, Map<String, Object> details) {
        return new ComponentHealth(name, HealthStatus.DOWN, message, Map.copyOf(details));
    }

    public static ComponentHealth degraded(String name, String message) {
        return new ComponentHealth(name, HealthStatus.DEGRADED, message, Map.of());
    }

    public static ComponentHealth unknown(String name, Throwable cause) {
        return new ComponentHealth(name, HealthStatus.UNKNOWN,
                cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName(),
                Map.of());
    }
}
