package io.gauss.vigil.health;

/**
 * Overall health state of a component or the whole Gauss application (HU-035).
 */
public enum HealthStatus {

    /** All checks passed; the component is operating normally. */
    UP,

    /** One or more checks failed; the component is not ready/live. */
    DOWN,

    /**
     * The component is partially functional — some checks degraded but the
     * component is still serving requests.
     */
    DEGRADED,

    /** Health could not be determined (e.g. the check itself threw). */
    UNKNOWN
}
