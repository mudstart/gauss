package io.gauss.quarkus;

import io.gauss.core.spi.AdapterRegistry;
import io.gauss.core.spi.RuntimeAdapter;
import io.gauss.quarkus.adapter.QuarkusRuntimeAdapter;
import org.junit.jupiter.api.Test;

import java.util.ServiceLoader;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers HU-002: RuntimeAdapter SPI contract and ServiceLoader registration.
 */
class QuarkusRuntimeAdapterTest {

    private final QuarkusRuntimeAdapter adapter = new QuarkusRuntimeAdapter();

    @Test
    void adapterName_isQuarkus() {
        assertThat(adapter.name()).isEqualTo("quarkus");
    }

    @Test
    void isActive_returnsFalse_whenArcContainerNotStarted() {
        // quarkus-arc is on the test classpath but Arc.container() returns null
        // in a plain JUnit JVM — no Quarkus app started.
        assertThat(adapter.isActive()).isFalse();
    }

    @Test
    void registerEndpoint_doesNotThrow() {
        // No-op for Quarkus — Arc discovers beans via CDI scanning.
        adapter.registerEndpoint(Object.class);
    }

    @Test
    void serviceLoader_discoversQuarkusAdapter() {
        boolean found = false;
        for (RuntimeAdapter a : ServiceLoader.load(RuntimeAdapter.class)) {
            if ("quarkus".equals(a.name())) {
                found = true;
                break;
            }
        }
        assertThat(found)
                .as("QuarkusRuntimeAdapter must be discoverable via ServiceLoader")
                .isTrue();
    }

    @Test
    void adapterRegistry_returnsEmpty_whenQuarkusNotActive() {
        // In the test JVM there is no active adapter (Quarkus not present).
        assertThat(AdapterRegistry.active()).isEmpty();
    }
}
