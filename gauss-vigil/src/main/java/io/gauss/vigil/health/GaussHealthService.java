package io.gauss.vigil.health;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Collects all registered {@link HealthIndicator} instances and produces an
 * aggregated {@link GaussHealthReport} (HU-035).
 *
 * <p>Indicators are discovered via {@link ServiceLoader} on construction.
 * Additional indicators can be registered programmatically (useful for
 * testing and for the Quarkus adapter).
 *
 * <p>Usage:
 * <pre>{@code
 * GaussHealthService health = new GaussHealthService();
 * GaussHealthReport  report = health.check();
 *
 * // Kubernetes liveness / readiness
 * boolean ready = health.isReady();
 * boolean live  = health.isLive();
 * }</pre>
 */
public final class GaussHealthService {

    private final List<HealthIndicator> indicators;

    /**
     * Creates a service that discovers indicators via {@link ServiceLoader}
     * and always includes the built-in {@link ModelRegistryHealthIndicator}.
     */
    public GaussHealthService() {
        List<HealthIndicator> discovered = new ArrayList<>();
        discovered.add(new ModelRegistryHealthIndicator());
        ServiceLoader.load(HealthIndicator.class).forEach(discovered::add);
        this.indicators = List.copyOf(discovered);
    }

    /**
     * Creates a service backed by the given explicit indicator list
     * (primarily for testing).
     */
    public GaussHealthService(List<HealthIndicator> indicators) {
        this.indicators = List.copyOf(indicators);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Runs all indicators and returns the aggregated health report.
     */
    public GaussHealthReport check() {
        List<ComponentHealth> components = indicators.stream()
                .map(HealthIndicator::check)
                .toList();
        HealthStatus overall = GaussHealthReport.aggregate(components);
        return new GaussHealthReport(overall, components, Instant.now());
    }

    /**
     * Returns {@code true} when the application is ready to serve traffic
     * (i.e., overall status is not {@link HealthStatus#DOWN}).
     *
     * <p>Maps to Quarkus {@code /q/health/ready}.
     */
    public boolean isReady() {
        return check().overall() != HealthStatus.DOWN;
    }

    /**
     * Returns {@code true} when the application is live (basic process health).
     *
     * <p>Uses a lightweight sub-set of indicators tagged as liveness checks.
     * Currently: always {@code true} unless an indicator throws, which is
     * treated as a process-level failure.
     *
     * <p>Maps to Quarkus {@code /q/health/live}.
     */
    public boolean isLive() {
        try {
            check();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Returns the list of indicators this service will invoke. */
    public List<HealthIndicator> indicators() {
        return indicators;
    }
}
