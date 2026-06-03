package io.gauss.augur.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link PredictionMetrics}.
 */
class PredictionMetricsTest {

    private SimpleMeterRegistry registry;
    private PredictionMetrics   metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics  = new PredictionMetrics(registry);
    }

    // -------------------------------------------------------------------------
    // Meter names and tags
    // -------------------------------------------------------------------------

    @Test
    void timerRegistered_withCorrectNameAndTags() {
        metrics.record("churn", () -> "result");

        Timer timer = registry.find(PredictionMetrics.LATENCY_METRIC)
                .tag(PredictionMetrics.TAG_MODEL,  "churn")
                .tag(PredictionMetrics.TAG_STATUS, PredictionMetrics.STATUS_SUCCESS)
                .timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    void counterRegistered_withCorrectNameAndTags() {
        metrics.record("churn", () -> "result");

        Counter counter = registry.find(PredictionMetrics.COUNT_METRIC)
                .tag(PredictionMetrics.TAG_MODEL,  "churn")
                .tag(PredictionMetrics.TAG_STATUS, PredictionMetrics.STATUS_SUCCESS)
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    // -------------------------------------------------------------------------
    // Successful calls
    // -------------------------------------------------------------------------

    @Test
    void record_returnsInferenceResult() {
        String result = metrics.record("nlp-model", () -> "prediction");
        assertThat(result).isEqualTo("prediction");
    }

    @Test
    void record_incrementsCounterOnSuccess() {
        metrics.record("model-a", () -> 42);
        metrics.record("model-a", () -> 43);
        metrics.record("model-a", () -> 44);

        Counter counter = registry.find(PredictionMetrics.COUNT_METRIC)
                .tag(PredictionMetrics.TAG_MODEL,  "model-a")
                .tag(PredictionMetrics.TAG_STATUS, PredictionMetrics.STATUS_SUCCESS)
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(3.0);
    }

    @Test
    void record_tracksMultipleModelsIndependently() {
        metrics.record("model-x", () -> "x");
        metrics.record("model-y", () -> "y");
        metrics.record("model-y", () -> "y2");

        double xCount = registry.find(PredictionMetrics.COUNT_METRIC)
                .tag(PredictionMetrics.TAG_MODEL, "model-x").counter().count();
        double yCount = registry.find(PredictionMetrics.COUNT_METRIC)
                .tag(PredictionMetrics.TAG_MODEL, "model-y").counter().count();

        assertThat(xCount).isEqualTo(1.0);
        assertThat(yCount).isEqualTo(2.0);
    }

    // -------------------------------------------------------------------------
    // Error calls
    // -------------------------------------------------------------------------

    @Test
    void record_rethrowsExceptionFromInference() {
        assertThatRuntimeException()
                .isThrownBy(() -> metrics.record("bad-model",
                        () -> { throw new RuntimeException("inference failed"); }))
                .withMessage("inference failed");
    }

    @Test
    void record_tagsErrorStatus_whenExceptionThrown() {
        try {
            metrics.record("error-model",
                    () -> { throw new RuntimeException("boom"); });
        } catch (RuntimeException ignored) {}

        Counter errorCounter = registry.find(PredictionMetrics.COUNT_METRIC)
                .tag(PredictionMetrics.TAG_MODEL,  "error-model")
                .tag(PredictionMetrics.TAG_STATUS, PredictionMetrics.STATUS_ERROR)
                .counter();

        assertThat(errorCounter).isNotNull();
        assertThat(errorCounter.count()).isEqualTo(1.0);

        // Success counter should NOT have been incremented
        Counter successCounter = registry.find(PredictionMetrics.COUNT_METRIC)
                .tag(PredictionMetrics.TAG_MODEL,  "error-model")
                .tag(PredictionMetrics.TAG_STATUS, PredictionMetrics.STATUS_SUCCESS)
                .counter();
        assertThat(successCounter).isNull();
    }

    @Test
    void record_voidOverload_incrementsCounter() {
        metrics.record("void-model", () -> { /* warm-up call */ });

        Counter counter = registry.find(PredictionMetrics.COUNT_METRIC)
                .tag(PredictionMetrics.TAG_MODEL,  "void-model")
                .tag(PredictionMetrics.TAG_STATUS, PredictionMetrics.STATUS_SUCCESS)
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    // -------------------------------------------------------------------------
    // Grafana dashboard resource
    // -------------------------------------------------------------------------

    @Test
    void grafanaDashboard_existsOnClasspath() {
        InputStream is = getClass().getResourceAsStream(
                "/gauss/grafana/prediction-dashboard.json");
        assertThat(is).isNotNull();
    }

    @Test
    void grafanaDashboard_containsExpectedMetricNames() throws Exception {
        InputStream is = getClass().getResourceAsStream(
                "/gauss/grafana/prediction-dashboard.json");
        String content = new String(is.readAllBytes());

        assertThat(content).contains("dsml_prediction_latency_seconds_bucket");
        assertThat(content).contains("dsml_prediction_count_total");
        assertThat(content).contains("gauss-ml-predictions");
    }

    // -------------------------------------------------------------------------
    // Construction guard
    // -------------------------------------------------------------------------

    @Test
    void constructor_throwsForNullRegistry() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PredictionMetrics(null));
    }
}
