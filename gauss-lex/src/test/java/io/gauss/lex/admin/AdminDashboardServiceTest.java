package io.gauss.lex.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link AdminDashboardService} and {@link SystemOverview}.
 * Covers HU-036 acceptance criteria.
 */
class AdminDashboardServiceTest {

    private AdminDashboardService svc;

    @BeforeEach
    void setUp() { svc = new AdminDashboardService(); }

    @Test
    void overview_defaultsToZeros() {
        SystemOverview o = svc.overview();
        assertThat(o.modelsTotal()).isZero();
        assertThat(o.experimentsTotal()).isZero();
    }

    @Test
    void setModelsTotal_reflectsInOverview() {
        svc.setModelsTotal(12);
        assertThat(svc.overview().modelsTotal()).isEqualTo(12);
    }

    @Test
    void setModelsInProduction_reflectsInOverview() {
        svc.setModelsInProduction(3);
        assertThat(svc.overview().modelsInProduction()).isEqualTo(3);
    }

    @Test
    void setPipelinesScheduled_reflectsInOverview() {
        svc.setPipelinesScheduled(5);
        assertThat(svc.overview().pipelinesScheduled()).isEqualTo(5);
    }

    @Test
    void setExperimentsTotal_reflectsInOverview() {
        svc.setExperimentsTotal(200);
        assertThat(svc.overview().experimentsTotal()).isEqualTo(200);
    }

    @Test
    void setFeaturesTotal_reflectsInOverview() {
        svc.setFeaturesTotal(30);
        assertThat(svc.overview().featuresTotal()).isEqualTo(30);
    }

    @Test
    void setNamespacesTotal_reflectsInOverview() {
        svc.setNamespacesTotal(4);
        assertThat(svc.overview().namespacesTotal()).isEqualTo(4);
    }

    @Test
    void setComponentHealth_appearsInOverview() {
        svc.setComponentHealth("model-registry", "UP");
        svc.setComponentHealth("feature-store",  "UP");
        assertThat(svc.overview().componentHealth())
                .containsEntry("model-registry", "UP")
                .containsEntry("feature-store",  "UP");
    }

    @Test
    void isHealthy_trueWhenAllUp() {
        svc.setComponentHealth("model-registry", "UP");
        svc.setComponentHealth("feature-store",  "UP");
        assertThat(svc.overview().isHealthy()).isTrue();
    }

    @Test
    void isHealthy_falseWhenAnyDown() {
        svc.setComponentHealth("model-registry", "UP");
        svc.setComponentHealth("feature-store",  "DOWN");
        assertThat(svc.overview().isHealthy()).isFalse();
    }

    @Test
    void isHealthy_trueWhenNoComponents() {
        assertThat(svc.overview().isHealthy()).isTrue();
    }

    @Test
    void overview_generatedAt_isSet() {
        Instant before = Instant.now().minusSeconds(1);
        assertThat(svc.overview().generatedAt()).isAfter(before);
    }

    @Test
    void componentHealth_isDefensiveCopy() {
        svc.setComponentHealth("c", "UP");
        SystemOverview o = svc.overview();
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> o.componentHealth().put("new", "DOWN"));
    }

    @Test
    void setComponentHealth_canBeUpdated() {
        svc.setComponentHealth("c", "DOWN");
        svc.setComponentHealth("c", "UP");
        assertThat(svc.overview().componentHealth()).containsEntry("c", "UP");
    }
}
