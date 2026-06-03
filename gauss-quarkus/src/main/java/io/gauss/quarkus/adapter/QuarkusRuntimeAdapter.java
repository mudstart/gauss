package io.gauss.quarkus.adapter;

import io.gauss.core.spi.RuntimeAdapter;
import io.gauss.quarkus.endpoint.MLEndpointRegistry;

/**
 * {@link RuntimeAdapter} implementation for the Quarkus runtime.
 *
 * <p>Detected as active when the Quarkus {@code io.quarkus.runtime.Quarkus}
 * class is present on the classpath. This avoids a hard compile-time dependency
 * on Quarkus in application code that wants runtime portability.
 *
 * <p>{@link #registerEndpoint(Class)} delegates to {@link MLEndpointRegistry},
 * which also auto-discovers {@code @MLEndpoint} beans via CDI scanning at startup.
 * Both paths are idempotent — registering the same class twice is harmless.
 */
public final class QuarkusRuntimeAdapter implements RuntimeAdapter {

    /**
     * Optional registry reference — populated when running inside the Quarkus
     * Arc container.  {@code null} in plain-JUnit environments.
     */
    private MLEndpointRegistry endpointRegistry;

    @Override
    public String name() {
        return "quarkus";
    }

    /**
     * Returns {@code true} when the Arc CDI container is initialised — i.e. we are
     * running inside a live Quarkus application, not just a plain JUnit test JVM that
     * happens to have {@code quarkus-arc} on its classpath.
     */
    @Override
    public boolean isActive() {
        try {
            Class<?> arc = Class.forName("io.quarkus.arc.Arc", false, getClass().getClassLoader());
            Object container = arc.getMethod("container").invoke(null);
            return container != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Wires the {@link MLEndpointRegistry} so that explicit registrations made
     * via {@link #registerEndpoint(Class)} are tracked.  Called by the CDI
     * container once the registry bean is available.
     */
    public void setEndpointRegistry(MLEndpointRegistry registry) {
        this.endpointRegistry = registry;
    }

    /**
     * Registers {@code endpointClass} in the {@link MLEndpointRegistry} (if one
     * is available).  Safe to call before the registry is set — the call is then
     * silently ignored; startup CDI scanning will still discover the class.
     */
    @Override
    public void registerEndpoint(Class<?> endpointClass) {
        if (endpointRegistry != null) {
            endpointRegistry.register(endpointClass);
        }
    }
}
