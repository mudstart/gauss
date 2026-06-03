package io.gauss.quarkus.endpoint;

import io.gauss.core.annotation.MLEndpoint;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * CDI bean that discovers all {@link MLEndpoint @MLEndpoint}-annotated beans
 * in the Quarkus Arc container at startup and maintains a registry of their
 * descriptors.
 *
 * <p>The registry is used by the Gauss Dev UI and the OpenAPI generator to
 * enumerate available ML endpoints at runtime.
 *
 * <p>Endpoints can also be registered programmatically via
 * {@link #register(Class)} — this is the path used by
 * {@link io.gauss.quarkus.adapter.QuarkusRuntimeAdapter#registerEndpoint(Class)}.
 */
@ApplicationScoped
public class MLEndpointRegistry {

    private static final Logger LOG =
            Logger.getLogger(MLEndpointRegistry.class.getName());

    /** Endpoints explicitly registered via {@link #register(Class)}. */
    private final List<MLEndpointDescriptor> descriptors = new CopyOnWriteArrayList<>();

    @Inject
    BeanManager beanManager;

    // -------------------------------------------------------------------------
    // Startup discovery
    // -------------------------------------------------------------------------

    /**
     * Scans all CDI beans for {@code @MLEndpoint} at application startup.
     * Called automatically by the Arc event bus.
     */
    void onStartup(@Observes StartupEvent ignored) {
        Set<Bean<?>> beans = beanManager.getBeans(Object.class);
        int discovered = 0;
        for (Bean<?> bean : beans) {
            Class<?> beanClass = bean.getBeanClass();
            if (beanClass.isAnnotationPresent(MLEndpoint.class) && !isAlreadyRegistered(beanClass)) {
                try {
                    MLEndpointDescriptor desc = MLEndpointDescriptor.from(beanClass);
                    descriptors.add(desc);
                    discovered++;
                    LOG.fine(() -> "Gauss: registered MLEndpoint '"
                            + desc.name() + "' at " + desc.httpBasePath());
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Could not register MLEndpoint: " + beanClass, e);
                }
            }
        }
        if (discovered > 0) {
            LOG.info("Gauss: discovered " + discovered + " @MLEndpoint bean(s)");
        }
    }

    // -------------------------------------------------------------------------
    // Programmatic registration
    // -------------------------------------------------------------------------

    /**
     * Registers a {@code @MLEndpoint} class explicitly (called from
     * {@link io.gauss.quarkus.adapter.QuarkusRuntimeAdapter}).
     *
     * @param endpointClass class annotated with {@code @MLEndpoint}
     * @throws IllegalArgumentException if the class lacks {@code @MLEndpoint}
     */
    public void register(Class<?> endpointClass) {
        if (!isAlreadyRegistered(endpointClass)) {
            descriptors.add(MLEndpointDescriptor.from(endpointClass));
        }
    }

    // -------------------------------------------------------------------------
    // Query API
    // -------------------------------------------------------------------------

    /**
     * Returns an unmodifiable view of all registered endpoint descriptors.
     */
    public List<MLEndpointDescriptor> getAll() {
        return Collections.unmodifiableList(descriptors);
    }

    /**
     * Returns the descriptor for the endpoint with the given name, or empty.
     */
    public Optional<MLEndpointDescriptor> findByName(String name) {
        return descriptors.stream()
                .filter(d -> d.name().equals(name))
                .findFirst();
    }

    /** Returns {@code true} if {@code endpointClass} is already registered. */
    public boolean isRegistered(Class<?> endpointClass) {
        return isAlreadyRegistered(endpointClass);
    }

    // -------------------------------------------------------------------------

    private boolean isAlreadyRegistered(Class<?> cls) {
        return descriptors.stream().anyMatch(d -> d.endpointClass().equals(cls));
    }
}
