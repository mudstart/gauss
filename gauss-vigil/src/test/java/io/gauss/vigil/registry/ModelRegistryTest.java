package io.gauss.vigil.registry;

import io.gauss.vigil.experiment.ExperimentRunner;
import io.gauss.vigil.experiment.InMemoryExperimentStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link ModelRegistry}.
 * Covers HU-025 and HU-043 acceptance criteria.
 */
class ModelRegistryTest {

    @BeforeEach
    @AfterEach
    void resetRegistry() {
        ModelRegistry.reset();
    }

    // -------------------------------------------------------------------------
    // HU-025 — Registration
    // -------------------------------------------------------------------------

    @Test
    void register_returnsUniqueId() {
        String id1 = ModelRegistry.register("churn-v1", null, "models/churn.onnx");
        String id2 = ModelRegistry.register("churn-v2", null, "models/churn.onnx");
        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    void register_initialStageIsStaging() {
        String id = ModelRegistry.register("churn", null, "models/churn.onnx");
        assertThat(ModelRegistry.find(id).get().currentStage()).isEqualTo(Stage.STAGING);
    }

    @Test
    void register_storesModelName() {
        String id = ModelRegistry.register("churn-v3", null, "models/c.onnx");
        assertThat(ModelRegistry.find(id).get().modelName()).isEqualTo("churn-v3");
    }

    @Test
    void findAll_returnsAllRegistrations() {
        ModelRegistry.register("m1", null, "p1");
        ModelRegistry.register("m2", null, "p2");
        assertThat(ModelRegistry.findAll()).hasSize(2);
    }

    @Test
    void find_returnsEmpty_forUnknownId() {
        assertThat(ModelRegistry.find("unknown")).isEmpty();
    }

    // -------------------------------------------------------------------------
    // HU-025 — Stage transitions
    // -------------------------------------------------------------------------

    @Test
    void promote_updatesStage() {
        String id = ModelRegistry.register("churn", null, "p.onnx");
        ModelRegistry.promote(id, Stage.PRODUCTION, "alice");
        assertThat(ModelRegistry.find(id).get().currentStage()).isEqualTo(Stage.PRODUCTION);
    }

    @Test
    void promote_appendsHistoryEntry() {
        String id = ModelRegistry.register("churn", null, "p.onnx");
        ModelRegistry.promote(id, Stage.PRODUCTION, "alice");
        ModelRegistration reg = ModelRegistry.find(id).get();
        // history: initial STAGING + transition to PRODUCTION
        assertThat(reg.history()).hasSize(2);
        StageTransition last = reg.lastTransition();
        assertThat(last.toStage()).isEqualTo(Stage.PRODUCTION);
        assertThat(last.actor()).isEqualTo("alice");
    }

    @Test
    void promote_unknownId_throwsIllegalArgument() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ModelRegistry.promote("nonexistent", Stage.PRODUCTION));
    }

    @Test
    void findByStage_returnsOnlyMatchingStage() {
        String id1 = ModelRegistry.register("m1", null, "p1");
        String id2 = ModelRegistry.register("m2", null, "p2");
        ModelRegistry.promote(id1, Stage.PRODUCTION, "alice");

        assertThat(ModelRegistry.findByStage(Stage.PRODUCTION)).hasSize(1);
        assertThat(ModelRegistry.findByStage(Stage.STAGING)).hasSize(1);
    }

    @Test
    void findProduction_returnsLatestProductionModel() {
        String id1 = ModelRegistry.register("churn", null, "v1.onnx");
        String id2 = ModelRegistry.register("churn", null, "v2.onnx");
        ModelRegistry.promote(id1, Stage.PRODUCTION, "alice");
        ModelRegistry.promote(id2, Stage.PRODUCTION, "alice");

        Optional<ModelRegistration> prod = ModelRegistry.findProduction("churn");
        assertThat(prod).isPresent();
        assertThat(prod.get().id()).isEqualTo(id2);
    }

    @Test
    void archive_setsArchivedStage() {
        String id = ModelRegistry.register("churn", null, "p.onnx");
        ModelRegistry.promote(id, Stage.ARCHIVED, "system");
        assertThat(ModelRegistry.find(id).get().currentStage()).isEqualTo(Stage.ARCHIVED);
    }

    // -------------------------------------------------------------------------
    // HU-043 — Guardrails (metric map path)
    // -------------------------------------------------------------------------

    @Test
    void promote_withPassingGuardrails_succeeds() {
        String id = ModelRegistry.register("churn", null, "p.onnx");

        @ModelGuardrail(metric = "auc", min = 0.90)
        class G {}
        ModelGuardrail[] guardrails = G.class.getAnnotationsByType(ModelGuardrail.class);

        assertThatCode(() -> ModelRegistry.promote(id, Stage.PRODUCTION, "alice",
                guardrails, Map.of("auc", 0.95)))
                .doesNotThrowAnyException();
    }

    @Test
    void promote_withFailingGuardrail_throwsViolation() {
        String id = ModelRegistry.register("churn", null, "p.onnx");

        @ModelGuardrail(metric = "auc", min = 0.90)
        class G {}
        ModelGuardrail[] guardrails = G.class.getAnnotationsByType(ModelGuardrail.class);

        assertThatExceptionOfType(GuardrailViolationException.class)
                .isThrownBy(() -> ModelRegistry.promote(id, Stage.PRODUCTION, "alice",
                        guardrails, Map.of("auc", 0.75)))
                .satisfies(ex -> {
                    assertThat(ex.metric()).isEqualTo("auc");
                    assertThat(ex.actualValue()).isEqualTo(0.75);
                });
    }

    @Test
    void promote_guardrailNotApplied_forNonProductionStage() {
        String id = ModelRegistry.register("churn", null, "p.onnx");

        @ModelGuardrail(metric = "auc", min = 0.90)
        class G {}
        ModelGuardrail[] guardrails = G.class.getAnnotationsByType(ModelGuardrail.class);

        // Promoting to STAGING with failing metric — guardrail should NOT fire
        assertThatCode(() -> ModelRegistry.promote(id, Stage.STAGING, "alice",
                guardrails, Map.of("auc", 0.50)))
                .doesNotThrowAnyException();
    }

    // -------------------------------------------------------------------------
    // HU-043 — Guardrails (ExperimentRun path)
    // -------------------------------------------------------------------------

    @Test
    void promote_withExperimentStore_evaluatesGuardrails() {
        InMemoryExperimentStore expStore = new InMemoryExperimentStore();
        ExperimentRunner expRunner = new ExperimentRunner(expStore);
        ModelRegistry.setExperimentStore(expStore);

        // Run an experiment and capture its ID
        expRunner.run("churn-exp", ctx -> {
            ctx.logMetric("auc", 0.93);
            return null;
        });
        String experimentId = expStore.findAll().get(0).id();

        String modelId = ModelRegistry.register("churn", experimentId, "p.onnx");

        @ModelGuardrail(metric = "auc", min = 0.90)
        class G {}
        ModelGuardrail[] guardrails = G.class.getAnnotationsByType(ModelGuardrail.class);

        assertThatCode(() -> ModelRegistry.promote(modelId, Stage.PRODUCTION, "alice", guardrails))
                .doesNotThrowAnyException();
    }
}
