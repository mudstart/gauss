package io.gauss.vigil.experiment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link ExperimentRunner}.
 * Covers HU-022 acceptance criteria.
 */
class ExperimentRunnerTest {

    private InMemoryExperimentStore store;
    private ExperimentRunner        runner;

    @BeforeEach
    void setUp() {
        store  = new InMemoryExperimentStore();
        runner = new ExperimentRunner(store);
    }

    // -------------------------------------------------------------------------
    // Basic run recording
    // -------------------------------------------------------------------------

    @Test
    void run_persists_completedRun() {
        runner.run("churn-xgb", new String[]{"xgboost"}, Map.of("lr", 0.1), ctx -> {
            ctx.logMetric("auc", 0.95);
            return "model";
        });

        assertThat(store.findAll()).hasSize(1);
        ExperimentRun run = store.findAll().get(0);
        assertThat(run.experimentName()).isEqualTo("churn-xgb");
        assertThat(run.status()).isEqualTo(RunStatus.COMPLETED);
    }

    @Test
    void run_capturesReturnValue() {
        runner.run("exp", new String[0], Map.of(), ctx -> "trained-model");

        ExperimentRun run = store.findAll().get(0);
        assertThat(run.returnValue()).hasValue("trained-model");
    }

    @Test
    void run_capturesMetrics() {
        runner.run("exp", ctx -> {
            ctx.logMetric("auc", 0.95);
            ctx.logMetric("f1",  0.88);
            return null;
        });

        ExperimentRun run = store.findAll().get(0);
        assertThat(run.metrics()).hasSize(2);
        assertThat(run.latestMetric("auc")).hasValue(0.95);
        assertThat(run.latestMetric("f1")).hasValue(0.88);
    }

    @Test
    void run_capturesArtifacts() {
        runner.run("exp", ctx -> {
            ctx.logArtifact("confusion_matrix", new int[][]{{10, 2}, {1, 9}});
            return null;
        });

        ExperimentRun run = store.findAll().get(0);
        assertThat(run.artifacts()).hasSize(1);
        assertThat(run.artifacts().get(0).name()).isEqualTo("confusion_matrix");
    }

    @Test
    void run_capturesParams() {
        runner.run("exp", new String[0], Map.of("lr", 0.01, "depth", 5), ctx -> null);

        ExperimentRun run = store.findAll().get(0);
        assertThat(run.params()).containsEntry("lr", 0.01);
        assertThat(run.params()).containsEntry("depth", 5);
    }

    @Test
    void run_assignsUniqueIds() {
        runner.run("exp", ctx -> null);
        runner.run("exp", ctx -> null);

        List<ExperimentRun> runs = store.findAll();
        assertThat(runs.get(0).id()).isNotEqualTo(runs.get(1).id());
    }

    @Test
    void run_setsTimestamps() {
        runner.run("exp", ctx -> null);

        ExperimentRun run = store.findAll().get(0);
        assertThat(run.startedAt()).isNotNull();
        assertThat(run.finishedAt()).isNotNull();
        assertThat(run.finishedAt()).isAfterOrEqualTo(run.startedAt());
    }

    @Test
    void run_propagatesTag() {
        runner.run("exp", new String[]{"production", "v2"}, Map.of(), ctx -> null);

        ExperimentRun run = store.findAll().get(0);
        assertThat(run.tags()).containsExactly("production", "v2");
    }

    // -------------------------------------------------------------------------
    // Failure recording
    // -------------------------------------------------------------------------

    @Test
    void run_persists_failedRun_onException() {
        assertThatRuntimeException()
                .isThrownBy(() -> runner.run("exp", ctx -> {
                    throw new IllegalStateException("training failed");
                }));

        assertThat(store.findAll()).hasSize(1);
        ExperimentRun run = store.findAll().get(0);
        assertThat(run.status()).isEqualTo(RunStatus.FAILED);
        assertThat(run.failureCause()).isPresent();
        assertThat(run.failureCause().get()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void run_rethrows_originalException() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> runner.run("exp", ctx -> {
                    throw new IllegalArgumentException("bad param");
                }))
                .withMessage("bad param");
    }

    // -------------------------------------------------------------------------
    // Convenience overloads
    // -------------------------------------------------------------------------

    @Test
    void run_noTagsNoParams_overload() {
        runner.run("simple", ctx -> {
            ctx.logMetric("acc", 0.99);
            return null;
        });
        assertThat(store.findByName("simple")).hasSize(1);
    }

    @Test
    void run_withTagsNoParams_overload() {
        runner.run("tagged", new String[]{"v1"}, ctx -> null);
        assertThat(store.findAll().get(0).tags()).containsExactly("v1");
    }

    // -------------------------------------------------------------------------
    // latestMetric helper
    // -------------------------------------------------------------------------

    @Test
    void latestMetric_returnsLastObservation_forRepeatedMetric() {
        runner.run("exp", ctx -> {
            ctx.logMetric("loss", 1.0, 1);
            ctx.logMetric("loss", 0.5, 2);
            ctx.logMetric("loss", 0.2, 3);
            return null;
        });

        ExperimentRun run = store.findAll().get(0);
        assertThat(run.latestMetric("loss")).hasValue(0.2);
    }

    @Test
    void latestMetric_returnsEmpty_forUnknownMetric() {
        runner.run("exp", ctx -> null);
        assertThat(store.findAll().get(0).latestMetric("nonexistent")).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Reflection-based invocation
    // -------------------------------------------------------------------------

    static class TrainingBean {
        @io.gauss.core.annotation.Experiment(name = "reflect-exp", tags = {"test"})
        public String train(double lr, ExperimentContext ctx) {
            ctx.logMetric("accuracy", lr * 10);
            return "model-" + lr;
        }
    }

    @Test
    void invokeAnnotated_recordsRunViaReflection() throws Exception {
        TrainingBean bean = new TrainingBean();
        java.lang.reflect.Method method = TrainingBean.class.getMethod(
                "train", double.class, ExperimentContext.class);
        io.gauss.core.annotation.Experiment ann =
                method.getAnnotation(io.gauss.core.annotation.Experiment.class);

        Object result = runner.invokeAnnotated(bean, method, new Object[]{0.1, null}, ann);

        assertThat(result).isEqualTo("model-0.1");
        assertThat(store.findAll()).hasSize(1);
        ExperimentRun run = store.findAll().get(0);
        assertThat(run.experimentName()).isEqualTo("reflect-exp");
        assertThat(run.latestMetric("accuracy")).hasValue(1.0);
        assertThat(run.params()).containsKey("lr");
    }
}
