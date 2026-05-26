package io.gauss.quarkus.adapter;

import io.gauss.core.spi.RuntimeAdapter;

/**
 * {@link RuntimeAdapter} implementation for the Quarkus runtime.
 *
 * <p>Detected as active when the Quarkus {@code io.quarkus.runtime.Quarkus}
 * class is present on the classpath. This avoids a hard compile-time dependency
 * on Quarkus in application code that wants runtime portability.
 *
 * <p>{@link #registerEndpoint(Class)} is a no-op here because Quarkus/Arc
 * discovers {@code @MLEndpoint} beans through its own CDI scanning mechanism.
 * The {@link io.gauss.quarkus.interceptor.GaussEndpointInterceptor} is applied
 * automatically via the {@code @GaussEndpointInterceptorBinding}.
 */
public final class QuarkusRuntimeAdapter implements RuntimeAdapter {

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

    @Override
    public void registerEndpoint(Class<?> endpointClass) {
        // Arc discovers @MLEndpoint beans via CDI scanning — no manual registration needed.
    }
}
