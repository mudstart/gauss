package io.gauss.lex.namespace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link NamespaceRegistry} and {@link NamespaceContext}.
 * Covers HU-057 acceptance criteria.
 */
class NamespaceRegistryTest {

    private NamespaceRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new NamespaceRegistry();
        NamespaceContext.clear();
    }

    @AfterEach
    void tearDown() {
        NamespaceContext.clear();
    }

    // -------------------------------------------------------------------------
    // NamespaceContext
    // -------------------------------------------------------------------------

    @Test
    void context_defaultNamespace_isDefault() {
        assertThat(NamespaceContext.current()).isEqualTo("default");
    }

    @Test
    void context_set_changesCurrentNamespace() {
        NamespaceContext.set("team-alpha");
        assertThat(NamespaceContext.current()).isEqualTo("team-alpha");
    }

    @Test
    void context_clear_resetsToDefault() {
        NamespaceContext.set("team-alpha");
        NamespaceContext.clear();
        assertThat(NamespaceContext.current()).isEqualTo("default");
    }

    @Test
    void context_set_blankNamespace_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> NamespaceContext.set("   "));
    }

    @Test
    void context_set_null_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> NamespaceContext.set(null));
    }

    @Test
    void context_isDefault_trueByDefault() {
        assertThat(NamespaceContext.isDefault()).isTrue();
    }

    @Test
    void context_isDefault_falseAfterSet() {
        NamespaceContext.set("team-beta");
        assertThat(NamespaceContext.isDefault()).isFalse();
    }

    // -------------------------------------------------------------------------
    // NamespaceRegistry — registration
    // -------------------------------------------------------------------------

    @Test
    void register_storesOwnership() {
        registry.register("model:churn-v1", "team-alpha");
        assertThat(registry.ownerOf("model:churn-v1")).hasValue("team-alpha");
    }

    @Test
    void register_updatesExistingOwnership() {
        registry.register("model:churn-v1", "team-alpha");
        registry.register("model:churn-v1", "team-beta");
        assertThat(registry.ownerOf("model:churn-v1")).hasValue("team-beta");
    }

    @Test
    void registerInCurrentNamespace_usesActiveNamespace() {
        NamespaceContext.set("team-gamma");
        registry.registerInCurrentNamespace("pipeline:etl");
        assertThat(registry.ownerOf("pipeline:etl")).hasValue("team-gamma");
    }

    @Test
    void ownerOf_unknownResource_returnsEmpty() {
        assertThat(registry.ownerOf("model:nonexistent")).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Isolation — isVisible
    // -------------------------------------------------------------------------

    @Test
    void isVisible_sameNamespace_returnsTrue() {
        registry.register("model:risk-v1", "team-beta");
        assertThat(registry.isVisible("model:risk-v1", "team-beta")).isTrue();
    }

    @Test
    void isVisible_differentNamespace_returnsFalse() {
        registry.register("model:risk-v1", "team-beta");
        assertThat(registry.isVisible("model:risk-v1", "team-alpha")).isFalse();
    }

    @Test
    void isVisible_superadmin_seesAllNamespaces() {
        registry.register("model:secret", "team-x");
        assertThat(registry.isVisible("model:secret", "superadmin")).isTrue();
    }

    @Test
    void isVisibleInCurrentNamespace_usesContext() {
        registry.register("model:churn-v1", "team-alpha");
        NamespaceContext.set("team-alpha");
        assertThat(registry.isVisibleInCurrentNamespace("model:churn-v1")).isTrue();
        NamespaceContext.set("team-beta");
        assertThat(registry.isVisibleInCurrentNamespace("model:churn-v1")).isFalse();
    }

    // -------------------------------------------------------------------------
    // visibleResources
    // -------------------------------------------------------------------------

    @Test
    void visibleResources_returnsOnlyOwnNamespace() {
        registry.register("model:churn-v1", "team-alpha");
        registry.register("model:risk-v1",  "team-beta");
        registry.register("model:nlp-v1",   "team-alpha");

        assertThat(registry.visibleResources("model", "team-alpha"))
                .containsExactlyInAnyOrder("model:churn-v1", "model:nlp-v1");
    }

    @Test
    void visibleResources_superadmin_seesAll() {
        registry.register("model:a", "team-alpha");
        registry.register("model:b", "team-beta");
        assertThat(registry.visibleResources("model", "superadmin")).hasSize(2);
    }

    @Test
    void visibleResources_filtersByResourceType() {
        registry.register("model:churn",    "team-alpha");
        registry.register("pipeline:etl",  "team-alpha");
        assertThat(registry.visibleResources("model", "team-alpha"))
                .containsExactly("model:churn");
    }

    @Test
    void visibleResources_usesCurrentNamespace_viaConvenienceOverload() {
        registry.register("model:m1", "team-alpha");
        NamespaceContext.set("team-alpha");
        assertThat(registry.visibleResources("model")).contains("model:m1");
    }

    // -------------------------------------------------------------------------
    // Superadmin views
    // -------------------------------------------------------------------------

    @Test
    void findAllNamespaces_returnsDistinctNamespaces() {
        registry.register("model:a", "ns-1");
        registry.register("model:b", "ns-1");
        registry.register("model:c", "ns-2");
        assertThat(registry.findAllNamespaces()).containsExactlyInAnyOrder("ns-1", "ns-2");
    }

    @Test
    void findResourcesInNamespace_returnsOnlyThatNamespace() {
        registry.register("model:a", "team-a");
        registry.register("model:b", "team-b");
        assertThat(registry.findResourcesInNamespace("team-a"))
                .containsExactly("model:a");
    }

    @Test
    void resourceCount_reflectsRegistrations() {
        registry.register("model:a", "ns");
        registry.register("model:b", "ns");
        assertThat(registry.resourceCount()).isEqualTo(2);
    }

    // -------------------------------------------------------------------------
    // Reset
    // -------------------------------------------------------------------------

    @Test
    void reset_clearsAllRegistrations() {
        registry.register("model:x", "team");
        registry.reset();
        assertThat(registry.resourceCount()).isZero();
    }
}
