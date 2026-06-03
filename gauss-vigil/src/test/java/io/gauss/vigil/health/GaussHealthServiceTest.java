package io.gauss.vigil.health;

import io.gauss.vigil.registry.ModelRegistry;
import io.gauss.vigil.registry.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link GaussHealthService} and {@link ModelRegistryHealthIndicator}.
 * Covers HU-035 acceptance criteria.
 */
class GaussHealthServiceTest {

    @BeforeEach
    @AfterEach
    void resetRegistry() {
        ModelRegistry.reset();
    }

    // -------------------------------------------------------------------------
    // Stub indicators
    // -------------------------------------------------------------------------

    static class AlwaysUpIndicator implements HealthIndicator {
        @Override public String name()          { return "always-up"; }
        @Override public ComponentHealth check() { return ComponentHealth.up("always-up", "ok"); }
    }

    static class AlwaysDownIndicator implements HealthIndicator {
        @Override public String name()          { return "always-down"; }
        @Override public ComponentHealth check() { return ComponentHealth.down("always-down", "broken"); }
    }

    static class AlwaysDegradedIndicator implements HealthIndicator {
        @Override public String name()          { return "always-degraded"; }
        @Override public ComponentHealth check() { return ComponentHealth.degraded("always-degraded", "slow"); }
    }

    static class ThrowingIndicator implements HealthIndicator {
        @Override public String name()          { return "throwing"; }
        @Override public ComponentHealth check() {
            return ComponentHealth.unknown("throwing", new RuntimeException("check failed"));
        }
    }

    // -------------------------------------------------------------------------
    // GaussHealthService
    // -------------------------------------------------------------------------

    @Test
    void check_allUp_overallIsUp() {
        GaussHealthService svc = new GaussHealthService(List.of(new AlwaysUpIndicator()));
        GaussHealthReport report = svc.check();
        assertThat(report.overall()).isEqualTo(HealthStatus.UP);
        assertThat(report.components()).hasSize(1);
    }

    @Test
    void check_anyDown_overallIsDown() {
        GaussHealthService svc = new GaussHealthService(
                List.of(new AlwaysUpIndicator(), new AlwaysDownIndicator()));
        assertThat(svc.check().overall()).isEqualTo(HealthStatus.DOWN);
    }

    @Test
    void check_anyDegraded_noDown_overallIsDegraded() {
        GaussHealthService svc = new GaussHealthService(
                List.of(new AlwaysUpIndicator(), new AlwaysDegradedIndicator()));
        assertThat(svc.check().overall()).isEqualTo(HealthStatus.DEGRADED);
    }

    @Test
    void check_timestampIsPopulated() {
        GaussHealthService svc = new GaussHealthService(List.of(new AlwaysUpIndicator()));
        assertThat(svc.check().checkedAt()).isNotNull();
    }

    @Test
    void isReady_returnsTrue_whenOverallNotDown() {
        GaussHealthService svc = new GaussHealthService(List.of(new AlwaysUpIndicator()));
        assertThat(svc.isReady()).isTrue();
    }

    @Test
    void isReady_returnsFalse_whenOverallDown() {
        GaussHealthService svc = new GaussHealthService(List.of(new AlwaysDownIndicator()));
        assertThat(svc.isReady()).isFalse();
    }

    @Test
    void isLive_alwaysTrue_forNonThrowing() {
        GaussHealthService svc = new GaussHealthService(List.of(new AlwaysUpIndicator()));
        assertThat(svc.isLive()).isTrue();
    }

    // -------------------------------------------------------------------------
    // ModelRegistryHealthIndicator
    // -------------------------------------------------------------------------

    @Test
    void modelRegistryIndicator_degraded_whenEmpty() {
        ModelRegistryHealthIndicator indicator = new ModelRegistryHealthIndicator();
        ComponentHealth health = indicator.check();
        assertThat(health.status()).isEqualTo(HealthStatus.DEGRADED);
        assertThat(health.message()).containsIgnoringCase("no models");
    }

    @Test
    void modelRegistryIndicator_up_whenModelsRegistered() {
        String id = ModelRegistry.register("churn", null, "p.onnx");
        ModelRegistry.promote(id, Stage.PRODUCTION, "alice");

        ModelRegistryHealthIndicator indicator = new ModelRegistryHealthIndicator();
        ComponentHealth health = indicator.check();
        assertThat(health.status()).isEqualTo(HealthStatus.UP);
        assertThat(health.details()).containsKey("production");
        assertThat(health.details().get("production")).isEqualTo(1L);
    }

    @Test
    void modelRegistryIndicator_details_countsByStage() {
        String id1 = ModelRegistry.register("m1", null, "p1");
        String id2 = ModelRegistry.register("m2", null, "p2");
        ModelRegistry.promote(id1, Stage.PRODUCTION, "alice");

        ModelRegistryHealthIndicator indicator = new ModelRegistryHealthIndicator();
        ComponentHealth health = indicator.check();
        assertThat(health.details().get("total")).isEqualTo(2);
        assertThat(health.details().get("staging")).isEqualTo(1L);
        assertThat(health.details().get("production")).isEqualTo(1L);
    }

    @Test
    void modelRegistryIndicator_name_isModelRegistry() {
        assertThat(new ModelRegistryHealthIndicator().name()).isEqualTo("model-registry");
    }

    // -------------------------------------------------------------------------
    // Aggregation
    // -------------------------------------------------------------------------

    @Test
    void aggregate_empty_isUp() {
        assertThat(GaussHealthReport.aggregate(List.of())).isEqualTo(HealthStatus.UP);
    }

    @Test
    void aggregate_downBeforesDegraded() {
        List<ComponentHealth> components = List.of(
                ComponentHealth.degraded("a", "slow"),
                ComponentHealth.down("b", "broken"),
                ComponentHealth.up("c", "ok"));
        assertThat(GaussHealthReport.aggregate(components)).isEqualTo(HealthStatus.DOWN);
    }

    @Test
    void aggregate_unknownMapsTooDegraded() {
        List<ComponentHealth> components = List.of(
                ComponentHealth.up("a", "ok"),
                ComponentHealth.unknown("b", new RuntimeException()));
        assertThat(GaussHealthReport.aggregate(components)).isEqualTo(HealthStatus.DEGRADED);
    }
}
