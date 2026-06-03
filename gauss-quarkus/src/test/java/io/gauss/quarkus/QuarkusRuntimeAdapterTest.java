package io.gauss.quarkus;

import io.gauss.core.annotation.MLEndpoint;
import io.gauss.core.spi.AdapterRegistry;
import io.gauss.core.spi.RuntimeAdapter;
import io.gauss.quarkus.adapter.QuarkusRuntimeAdapter;
import io.gauss.quarkus.endpoint.MLEndpointDescriptor;
import io.gauss.quarkus.endpoint.MLEndpointRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ServiceLoader;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.*;

/**
 * Covers HU-002 (RuntimeAdapter SPI) and HU-016 (endpoint registration via
 * {@link QuarkusRuntimeAdapter#registerEndpoint(Class)}).
 */
class QuarkusRuntimeAdapterTest {

    // -------------------------------------------------------------------------
    // Fixture
    // -------------------------------------------------------------------------

    @MLEndpoint("WidgetService")
    static class WidgetService {
        public String predict(String x) { return x; }
    }

    private QuarkusRuntimeAdapter adapter;
    private MLEndpointRegistry     registry;

    @BeforeEach
    void setUp() throws Exception {
        adapter  = new QuarkusRuntimeAdapter();
        registry = new MLEndpointRegistry();

        // Blank out the descriptor list so each test starts clean.
        Field f = MLEndpointRegistry.class.getDeclaredField("descriptors");
        f.setAccessible(true);
        f.set(registry, new CopyOnWriteArrayList<>());
    }

    // -------------------------------------------------------------------------
    // HU-002 — basic SPI contract
    // -------------------------------------------------------------------------

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
        // In the test JVM there is no active adapter (Arc container not started).
        assertThat(AdapterRegistry.active()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // HU-016 — registerEndpoint() delegation
    // -------------------------------------------------------------------------

    @Test
    void registerEndpoint_isNoOp_whenRegistryNotSet() {
        // No registry wired — call must not throw.
        assertThatCode(() -> adapter.registerEndpoint(WidgetService.class))
                .doesNotThrowAnyException();
    }

    @Test
    void registerEndpoint_delegatesToRegistry_whenRegistryIsSet() {
        adapter.setEndpointRegistry(registry);

        adapter.registerEndpoint(WidgetService.class);

        assertThat(registry.isRegistered(WidgetService.class)).isTrue();
    }

    @Test
    void registerEndpoint_isIdempotent_withRegistrySet() {
        adapter.setEndpointRegistry(registry);

        adapter.registerEndpoint(WidgetService.class);
        adapter.registerEndpoint(WidgetService.class);

        assertThat(registry.getAll()).hasSize(1);
    }

    @Test
    void registerEndpoint_storesCorrectDescriptor() {
        adapter.setEndpointRegistry(registry);

        adapter.registerEndpoint(WidgetService.class);

        MLEndpointDescriptor desc = registry.findByName("WidgetService").orElseThrow();
        assertThat(desc.endpointClass()).isEqualTo(WidgetService.class);
        assertThat(desc.httpBasePath()).isEqualTo("/api/widget-service");
    }

    @Test
    void setEndpointRegistry_replacesExistingRegistry() throws Exception {
        MLEndpointRegistry second = new MLEndpointRegistry();
        Field f = MLEndpointRegistry.class.getDeclaredField("descriptors");
        f.setAccessible(true);
        f.set(second, new CopyOnWriteArrayList<>());

        adapter.setEndpointRegistry(registry);
        adapter.registerEndpoint(WidgetService.class);

        // Switch to a fresh registry — original should be unaffected.
        adapter.setEndpointRegistry(second);
        adapter.registerEndpoint(WidgetService.class);

        assertThat(registry.getAll()).hasSize(1);   // unchanged
        assertThat(second.getAll()).hasSize(1);
    }
}
