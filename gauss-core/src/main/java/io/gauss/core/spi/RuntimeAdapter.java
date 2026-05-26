package io.gauss.core.spi;

/**
 * SPI that decouples Gauss framework logic from the underlying runtime.
 *
 * <p>Each supported runtime (Quarkus, Spring Boot) provides exactly one
 * implementation of this interface, loaded via {@link java.util.ServiceLoader}.
 *
 * <p>The contract is deliberately minimal for Sprint 1. Richer lifecycle
 * hooks (startup, shutdown, health) will be added in Sprint 4+ when the
 * Spring adapter is introduced.
 */
public interface RuntimeAdapter {

    /** Short, lowercase identifier for the runtime (e.g. {@code "quarkus"}, {@code "spring"}). */
    String name();

    /**
     * Returns {@code true} when the runtime is active in the current JVM.
     * Used by the framework to select the correct adapter via ServiceLoader.
     */
    boolean isActive();

    /**
     * Called once per {@code @MLEndpoint} class discovered at startup.
     * Implementations register the class with the runtime's component model.
     */
    void registerEndpoint(Class<?> endpointClass);
}
