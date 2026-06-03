package io.gauss.augur.test;

import io.gauss.augur.model.ModelDescriptor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link MockOnnxModel}.
 */
class MockOnnxModelTest {

    @Test
    void run_returnsFixedOutputMap() {
        MockOnnxModel mock = new MockOnnxModel(
                "models/churn.onnx", "{\"probability\":[0.9,0.1]}");

        Map<String, Object> result = mock.run(Map.of("input", new float[]{1f, 2f}));

        assertThat(result).containsKey("probability");
        @SuppressWarnings("unchecked")
        List<Number> probs = (List<Number>) result.get("probability");
        assertThat(probs.get(0).doubleValue()).isEqualTo(0.9);
    }

    @Test
    void run_ignoresInputs() {
        MockOnnxModel mock = new MockOnnxModel(
                "models/test.onnx", "{\"score\":42}");

        // Different inputs should still return the same fixed output
        assertThat(mock.run(Map.of("x", new float[]{1f}))).containsKey("score");
        assertThat(mock.run(Map.of("y", new float[]{9f}))).containsKey("score");
    }

    @Test
    void callCount_incrementsOnEachRun() {
        MockOnnxModel mock = new MockOnnxModel(
                "models/test.onnx", "{\"out\":1}");

        assertThat(mock.callCount()).isZero();
        mock.run(Map.of());
        mock.run(Map.of());
        assertThat(mock.callCount()).isEqualTo(2);
    }

    @Test
    void reset_setsCallCountToZero() {
        MockOnnxModel mock = new MockOnnxModel("models/t.onnx", "{\"v\":0}");
        mock.run(Map.of());
        mock.run(Map.of());
        mock.reset();
        assertThat(mock.callCount()).isZero();
    }

    @Test
    void close_setsClosed() {
        MockOnnxModel mock = new MockOnnxModel("models/t.onnx", "{\"v\":0}");
        assertThat(mock.isClosed()).isFalse();
        mock.close();
        assertThat(mock.isClosed()).isTrue();
    }

    @Test
    void descriptor_derivesNameFromPath() {
        MockOnnxModel mock = new MockOnnxModel(
                "models/churn-v2.onnx", "{\"out\":1}");
        ModelDescriptor desc = mock.descriptor();
        assertThat(desc.name()).isEqualTo("churn-v2");
        assertThat(desc.path()).isEqualTo("models/churn-v2.onnx");
    }

    @Test
    void constructor_throwsForInvalidJson() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MockOnnxModel("models/t.onnx", "NOT_JSON"))
                .withMessageContaining("invalid output JSON");
    }
}
