package io.gauss.vigil.health;

/**
 * SPI for contributing a health check to the Gauss health report (HU-035).
 *
 * <p>Implementations are discovered via {@link java.util.ServiceLoader} and
 * collected by {@link GaussHealthService}.  The Quarkus adapter bridges each
 * indicator into the Quarkus SmallRye Health extension for {@code /q/health}.
 *
 * <p>Implementations must be thread-safe — {@link #check()} may be called
 * concurrently.
 */
public interface HealthIndicator {

    /**
     * Returns the logical name of this indicator (used as the component name
     * in the health report).
     */
    String name();

    /**
     * Performs the health check and returns the current status.
     *
     * <p>This method must never throw — any exception should be caught and
     * returned as a {@link ComponentHealth#unknown(String, Throwable)} result.
     */
    ComponentHealth check();
}
