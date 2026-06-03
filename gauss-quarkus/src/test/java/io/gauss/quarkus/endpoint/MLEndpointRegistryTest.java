package io.gauss.quarkus.endpoint;

import io.gauss.core.annotation.MLEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link MLEndpointRegistry} — programmatic registration API.
 *
 * <p>CDI startup discovery ({@code onStartup}) requires a live Arc container
 * and is covered by integration tests.  Here we exercise the programmatic
 * {@link MLEndpointRegistry#register(Class)} path and query methods.
 *
 * <p>Because the registry is {@code @ApplicationScoped}, we instantiate it
 * directly and inject a blank {@code descriptors} list via reflection to keep
 * tests isolated.
 */
class MLEndpointRegistryTest {

    // -------------------------------------------------------------------------
    // Fixture endpoint classes
    // -------------------------------------------------------------------------

    @MLEndpoint("AlphaService")
    static class AlphaService {
        public String predict(String input) { return input; }
    }

    @MLEndpoint("BetaService")
    static class BetaService {
        public int score() { return 0; }
    }

    @MLEndpoint
    static class GammaService {
        public void process() {}
    }

    static class NoAnnotation {}

    // -------------------------------------------------------------------------
    // Setup
    // -------------------------------------------------------------------------

    private MLEndpointRegistry registry;

    @BeforeEach
    void setUp() throws Exception {
        registry = new MLEndpointRegistry();
        // Inject an empty descriptors list via reflection so each test starts clean.
        Field f = MLEndpointRegistry.class.getDeclaredField("descriptors");
        f.setAccessible(true);
        f.set(registry, new CopyOnWriteArrayList<>());
    }

    // -------------------------------------------------------------------------
    // register()
    // -------------------------------------------------------------------------

    @Test
    void register_addsDescriptorForAnnotatedClass() {
        registry.register(AlphaService.class);
        assertThat(registry.getAll()).hasSize(1);
        assertThat(registry.getAll().get(0).name()).isEqualTo("AlphaService");
    }

    @Test
    void register_isIdempotent_sameClassTwice() {
        registry.register(AlphaService.class);
        registry.register(AlphaService.class);
        assertThat(registry.getAll()).hasSize(1);
    }

    @Test
    void register_throwsForUnannotatedClass() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> registry.register(NoAnnotation.class))
                .withMessageContaining("@MLEndpoint");
    }

    @Test
    void register_multipleDistinctClasses() {
        registry.register(AlphaService.class);
        registry.register(BetaService.class);
        assertThat(registry.getAll()).hasSize(2);
    }

    // -------------------------------------------------------------------------
    // getAll()
    // -------------------------------------------------------------------------

    @Test
    void getAll_returnsEmptyList_whenNothingRegistered() {
        assertThat(registry.getAll()).isEmpty();
    }

    @Test
    void getAll_returnsUnmodifiableView() {
        registry.register(AlphaService.class);
        List<MLEndpointDescriptor> all = registry.getAll();
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> all.add(MLEndpointDescriptor.from(BetaService.class)));
    }

    @Test
    void getAll_containsAllRegisteredDescriptors() {
        registry.register(AlphaService.class);
        registry.register(BetaService.class);
        registry.register(GammaService.class);

        List<String> names = registry.getAll().stream()
                .map(MLEndpointDescriptor::name)
                .toList();
        assertThat(names).containsExactlyInAnyOrder("AlphaService", "BetaService", "GammaService");
    }

    // -------------------------------------------------------------------------
    // findByName()
    // -------------------------------------------------------------------------

    @Test
    void findByName_returnsDescriptor_whenRegistered() {
        registry.register(AlphaService.class);
        Optional<MLEndpointDescriptor> found = registry.findByName("AlphaService");
        assertThat(found).isPresent();
        assertThat(found.get().endpointClass()).isEqualTo(AlphaService.class);
    }

    @Test
    void findByName_returnsEmpty_whenNotRegistered() {
        Optional<MLEndpointDescriptor> found = registry.findByName("NonExistent");
        assertThat(found).isEmpty();
    }

    @Test
    void findByName_usesAnnotationValue_notSimpleClassName() {
        // GammaService has @MLEndpoint with blank value => name is "GammaService" (simple class name)
        registry.register(GammaService.class);
        assertThat(registry.findByName("GammaService")).isPresent();
        assertThat(registry.findByName("gamma")).isEmpty();
    }

    // -------------------------------------------------------------------------
    // isRegistered()
    // -------------------------------------------------------------------------

    @Test
    void isRegistered_returnsFalse_beforeRegistration() {
        assertThat(registry.isRegistered(AlphaService.class)).isFalse();
    }

    @Test
    void isRegistered_returnsTrue_afterRegistration() {
        registry.register(AlphaService.class);
        assertThat(registry.isRegistered(AlphaService.class)).isTrue();
    }

    @Test
    void isRegistered_returnsFalse_forDifferentClass() {
        registry.register(AlphaService.class);
        assertThat(registry.isRegistered(BetaService.class)).isFalse();
    }
}
