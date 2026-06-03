package io.gauss.vigil.rollback;

import io.gauss.vigil.registry.ModelRegistry;
import io.gauss.vigil.registry.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link RollbackService}.
 * Covers HU-054 acceptance criteria.
 */
class RollbackServiceTest {

    private AtomicReference<Instant> clockRef;
    private RollbackService          svc;
    private RollbackPolicy           policy;

    // model IDs set up in setUp()
    private String idV1;
    private String idV2;

    @BeforeEach
    void setUp() {
        ModelRegistry.reset();
        clockRef = new AtomicReference<>(Instant.now());
        svc      = new RollbackService(clockRef::get);
        policy   = RollbackPolicy.of("error_rate", 0.15, 10, 3);

        // Register v1 and promote to PRODUCTION
        idV1 = ModelRegistry.register("churn", null, "path/v1");
        ModelRegistry.promote(idV1, Stage.PRODUCTION, "alice");

        // Register v2 and promote to PRODUCTION (v1 transitions to implicit staging/archived)
        idV2 = ModelRegistry.register("churn", null, "path/v2");
        ModelRegistry.promote(idV2, Stage.PRODUCTION, "alice");
    }

    @AfterEach
    void tearDown() {
        ModelRegistry.reset();
    }

    // -------------------------------------------------------------------------
    // RollbackPolicy
    // -------------------------------------------------------------------------

    @Test
    void policy_of_defaults() {
        RollbackPolicy p = RollbackPolicy.of("error_rate", 0.10);
        assertThat(p.windowMinutes()).isEqualTo(10);
        assertThat(p.maxPerHour()).isEqualTo(3);
    }

    @Test
    void policy_customValues() {
        RollbackPolicy p = RollbackPolicy.of("latency", 0.20, 5, 2);
        assertThat(p.metricName()).isEqualTo("latency");
        assertThat(p.threshold()).isEqualTo(0.20);
        assertThat(p.windowMinutes()).isEqualTo(5);
        assertThat(p.maxPerHour()).isEqualTo(2);
    }

    // -------------------------------------------------------------------------
    // No-rollback cases
    // -------------------------------------------------------------------------

    @Test
    void evaluate_belowThreshold_returnsEmpty() {
        svc.recordMetric(idV2, "error_rate", 0.05);  // below 0.15
        assertThat(svc.evaluate(idV2, policy)).isEmpty();
    }

    @Test
    void evaluate_noObservations_returnsEmpty() {
        assertThat(svc.evaluate(idV2, policy)).isEmpty();
    }

    @Test
    void evaluate_metricsOutsideWindow_returnsEmpty() {
        // Record old metric, advance clock past window
        svc.recordMetric(idV2, "error_rate", 0.99);
        clockRef.set(clockRef.get().plusSeconds(policy.windowMinutes() * 60L + 60));
        assertThat(svc.evaluate(idV2, policy)).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Rollback triggered
    // -------------------------------------------------------------------------

    @Test
    void evaluate_aboveThreshold_returnsEvent() {
        svc.recordMetric(idV2, "error_rate", 0.25);  // above 0.15
        svc.recordMetric(idV2, "error_rate", 0.30);
        assertThat(svc.evaluate(idV2, policy)).isPresent();
    }

    @Test
    void rollback_event_containsCorrectMetricValue() {
        svc.recordMetric(idV2, "error_rate", 0.20);
        svc.recordMetric(idV2, "error_rate", 0.24);
        RollbackEvent event = svc.evaluate(idV2, policy).orElseThrow();
        assertThat(event.metricValue()).isCloseTo(0.22, within(0.001));
        assertThat(event.threshold()).isEqualTo(0.15);
    }

    @Test
    void rollback_event_containsModelName() {
        svc.recordMetric(idV2, "error_rate", 0.99);
        RollbackEvent event = svc.evaluate(idV2, policy).orElseThrow();
        assertThat(event.modelName()).isEqualTo("churn");
    }

    @Test
    void rollback_event_previousId_isV1() {
        svc.recordMetric(idV2, "error_rate", 0.99);
        RollbackEvent event = svc.evaluate(idV2, policy).orElseThrow();
        assertThat(event.previousId()).isEqualTo(idV1);
    }

    @Test
    void rollback_promotesV1_toProduction() {
        svc.recordMetric(idV2, "error_rate", 0.99);
        svc.evaluate(idV2, policy);
        assertThat(ModelRegistry.find(idV1).orElseThrow().currentStage())
                .isEqualTo(Stage.PRODUCTION);
    }

    @Test
    void rollback_archivesV2() {
        svc.recordMetric(idV2, "error_rate", 0.99);
        svc.evaluate(idV2, policy);
        assertThat(ModelRegistry.find(idV2).orElseThrow().currentStage())
                .isEqualTo(Stage.ARCHIVED);
    }

    // -------------------------------------------------------------------------
    // Rate limiting
    // -------------------------------------------------------------------------

    @Test
    void rateLimitReached_preventsAdditionalRollback() {
        RollbackPolicy limitPolicy = RollbackPolicy.of("error_rate", 0.01, 10, 1);
        svc.recordMetric(idV2, "error_rate", 0.99);
        svc.evaluate(idV2, limitPolicy);  // first rollback

        // Re-register and re-promote for a second attempt
        String idV3 = ModelRegistry.register("churn", null, "path/v3");
        ModelRegistry.promote(idV3, Stage.PRODUCTION, "alice");
        svc.recordMetric(idV3, "error_rate", 0.99);

        // Second rollback should be rate-limited (maxPerHour=1)
        assertThat(svc.evaluate(idV3, limitPolicy)).isEmpty();
    }

    @Test
    void rateLimitResets_afterOneHour() {
        RollbackPolicy limitPolicy = RollbackPolicy.of("error_rate", 0.01, 10, 1);
        svc.recordMetric(idV2, "error_rate", 0.99);
        svc.evaluate(idV2, limitPolicy);  // first rollback

        // Advance clock past 1 hour
        clockRef.set(clockRef.get().plusSeconds(3601));

        String idV3 = ModelRegistry.register("churn", null, "path/v3");
        ModelRegistry.promote(idV3, Stage.PRODUCTION, "alice");
        svc.recordMetric(idV3, "error_rate", 0.99);
        assertThat(svc.evaluate(idV3, limitPolicy)).isPresent();
    }

    // -------------------------------------------------------------------------
    // Audit log
    // -------------------------------------------------------------------------

    @Test
    void auditLog_recordsRollbackEvent() {
        svc.recordMetric(idV2, "error_rate", 0.99);
        svc.evaluate(idV2, policy);
        assertThat(svc.auditLog()).hasSize(1);
    }

    @Test
    void auditLog_emptyWhenNoRollback() {
        assertThat(svc.auditLog()).isEmpty();
    }

    @Test
    void rollbackEvent_summary_containsModelName() {
        svc.recordMetric(idV2, "error_rate", 0.99);
        RollbackEvent event = svc.evaluate(idV2, policy).orElseThrow();
        assertThat(event.summary()).contains("churn");
    }

    // -------------------------------------------------------------------------
    // Reset
    // -------------------------------------------------------------------------

    @Test
    void reset_clearsObservationsAndHistory() {
        svc.recordMetric(idV2, "error_rate", 0.99);
        svc.evaluate(idV2, policy);
        svc.reset();
        assertThat(svc.auditLog()).isEmpty();
    }
}
