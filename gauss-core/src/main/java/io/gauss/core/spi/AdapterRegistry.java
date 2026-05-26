package io.gauss.core.spi;

import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Selects the active {@link RuntimeAdapter} via {@link ServiceLoader}.
 *
 * <p>Picks the first adapter that reports {@link RuntimeAdapter#isActive()}.
 * If none is active, returns {@link Optional#empty()} — callers may degrade
 * gracefully or throw, depending on context.
 */
public final class AdapterRegistry {

    private AdapterRegistry() {}

    public static Optional<RuntimeAdapter> active() {
        for (RuntimeAdapter adapter : ServiceLoader.load(RuntimeAdapter.class)) {
            if (adapter.isActive()) return Optional.of(adapter);
        }
        return Optional.empty();
    }
}
