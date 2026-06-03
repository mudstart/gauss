package io.gauss.vigil.catalog;

import io.gauss.core.annotation.ModelCard;
import io.gauss.vigil.experiment.ExperimentRunner;
import io.gauss.vigil.experiment.InMemoryExperimentStore;
import io.gauss.vigil.registry.ModelRegistration;
import io.gauss.vigil.registry.ModelRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link ModelCardService}.
 * Covers HU-055 acceptance criteria.
 */
class ModelCardServiceTest {

    // -------------------------------------------------------------------------
    // Fixture model classes
    // -------------------------------------------------------------------------

    @ModelCard(
            description   = "XGBoost churn prediction model",
            intendedUse   = "Predict churn probability for B2C customers",
            limitations   = "Not suitable for B2B or enterprise segments",
            trainedOn     = "Customer transactions 2020-2024 (~5M rows)",
            version       = "2.0"
    )
    static class ChurnModelClass { }

    static class NoAnnotationModel { }

    // -------------------------------------------------------------------------

    private InMemoryExperimentStore store;
    private ModelCardService        service;

    @BeforeEach
    void setUp() {
        ModelRegistry.reset();
        store   = new InMemoryExperimentStore();
        service = new ModelCardService(store);
    }

    @AfterEach
    void tearDown() {
        ModelRegistry.reset();
    }

    // -------------------------------------------------------------------------
    // Annotation enrichment
    // -------------------------------------------------------------------------

    @Test
    void build_extractsDescription() {
        ModelRegistration reg = registerAndFind("churn-v1");
        assertThat(service.build(reg, ChurnModelClass.class).description())
                .isEqualTo("XGBoost churn prediction model");
    }

    @Test
    void build_extractsIntendedUse() {
        ModelRegistration reg = registerAndFind("churn-v1");
        assertThat(service.build(reg, ChurnModelClass.class).intendedUse())
                .contains("B2C customers");
    }

    @Test
    void build_extractsLimitations() {
        ModelRegistration reg = registerAndFind("churn-v1");
        assertThat(service.build(reg, ChurnModelClass.class).limitations())
                .contains("B2B");
    }

    @Test
    void build_extractsTrainedOn() {
        ModelRegistration reg = registerAndFind("churn-v1");
        assertThat(service.build(reg, ChurnModelClass.class).trainedOn())
                .contains("2020-2024");
    }

    @Test
    void build_extractsCardVersion() {
        ModelRegistration reg = registerAndFind("churn-v1");
        assertThat(service.build(reg, ChurnModelClass.class).cardVersion())
                .isEqualTo("2.0");
    }

    // -------------------------------------------------------------------------
    // ModelRegistration metadata
    // -------------------------------------------------------------------------

    @Test
    void build_setsModelId_fromRegistration() {
        String id = ModelRegistry.register("churn-v1", null, "models/churn.onnx");
        ModelRegistration reg = ModelRegistry.find(id).orElseThrow();
        assertThat(service.build(reg, ChurnModelClass.class).modelId()).isEqualTo(id);
    }

    @Test
    void build_setsModelName_fromRegistration() {
        ModelRegistration reg = registerAndFind("sentiment-model");
        assertThat(service.build(reg, ChurnModelClass.class).modelName())
                .isEqualTo("sentiment-model");
    }

    // -------------------------------------------------------------------------
    // No annotation
    // -------------------------------------------------------------------------

    @Test
    void build_noAnnotation_emptyTextFields() {
        ModelRegistration reg = registerAndFind("plain-model");
        ModelCardEntry card = service.build(reg, NoAnnotationModel.class);
        assertThat(card.description()).isEmpty();
        assertThat(card.limitations()).isEmpty();
        assertThat(card.intendedUse()).isEmpty();
    }

    @Test
    void build_nullClass_emptyTextFields() {
        ModelRegistration reg = registerAndFind("plain-model");
        ModelCardEntry card = service.build(reg);
        assertThat(card.description()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Experiment metrics enrichment
    // -------------------------------------------------------------------------

    @Test
    void build_enrichesMetrics_fromExperimentRun() {
        // Use ExperimentRunner (public API) to create a run with metrics
        ExperimentRunner runner = new ExperimentRunner(store);
        runner.run("churn", new String[0], Map.of(), ctx -> {
            ctx.logMetric("auc", 0.92);
            ctx.logMetric("f1",  0.88);
            return null;
        });
        String expId = store.findAll().get(0).id();

        String id = ModelRegistry.register("churn-v1", expId, "path");
        ModelRegistration reg = ModelRegistry.find(id).orElseThrow();
        ModelCardEntry card = service.build(reg, ChurnModelClass.class);

        assertThat(card.metrics()).containsEntry("auc", 0.92);
        assertThat(card.metrics()).containsEntry("f1",  0.88);
    }

    @Test
    void build_noExperimentStore_emptyMetrics() {
        ModelCardService noStore = new ModelCardService();
        ModelRegistration reg = registerAndFind("model-a");
        assertThat(noStore.build(reg, ChurnModelClass.class).metrics()).isEmpty();
    }

    @Test
    void build_unknownExperimentId_emptyMetrics() {
        String id = ModelRegistry.register("model-b", "exp-missing", "path");
        ModelRegistration reg = ModelRegistry.find(id).orElseThrow();
        assertThat(service.build(reg, ChurnModelClass.class).metrics()).isEmpty();
    }

    @Test
    void build_setsExperimentId_whenPresent() {
        ExperimentRunner runner = new ExperimentRunner(store);
        runner.run("test", new String[0], Map.of(), ctx -> null);
        String expId = store.findAll().get(0).id();

        String id = ModelRegistry.register("m", expId, "path");
        ModelRegistration reg = ModelRegistry.find(id).orElseThrow();
        assertThat(service.build(reg, ChurnModelClass.class).experimentId()).isEqualTo(expId);
    }

    // -------------------------------------------------------------------------
    // buildAll
    // -------------------------------------------------------------------------

    @Test
    void buildAll_returnsOneCardPerRegistration() {
        ModelRegistry.register("m1", null, "path");
        ModelRegistry.register("m2", null, "path");
        assertThat(service.buildAll(ChurnModelClass.class)).hasSize(2);
    }

    @Test
    void buildAll_emptyRegistry_returnsEmptyList() {
        assertThat(service.buildAll(ChurnModelClass.class)).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Timestamp
    // -------------------------------------------------------------------------

    @Test
    void build_generatedAt_isRecentTimestamp() {
        Instant before = Instant.now().minusSeconds(1);
        ModelRegistration reg = registerAndFind("ts-model");
        assertThat(service.build(reg, ChurnModelClass.class).generatedAt()).isAfter(before);
    }

    // -------------------------------------------------------------------------
    // JSON export
    // -------------------------------------------------------------------------

    @Test
    void toJson_containsModelName() {
        ModelRegistration reg = registerAndFind("risk-model");
        ModelCardEntry card = service.build(reg, ChurnModelClass.class);
        assertThat(service.toJson(card)).contains("risk-model");
    }

    @Test
    void toJson_containsDescription() {
        ModelRegistration reg = registerAndFind("risk-model");
        ModelCardEntry card = service.build(reg, ChurnModelClass.class);
        assertThat(service.toJson(card)).contains("XGBoost churn prediction model");
    }

    @Test
    void toJson_isValidJson() {
        ModelRegistration reg = registerAndFind("json-model");
        ModelCardEntry card = service.build(reg, ChurnModelClass.class);
        String json = service.toJson(card);
        assertThat(json).startsWith("{").endsWith("}");
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private static ModelRegistration registerAndFind(String name) {
        String id = ModelRegistry.register(name, null, "models/" + name + ".onnx");
        return ModelRegistry.find(id).orElseThrow();
    }
}
