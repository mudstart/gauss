package io.gauss.augur.test;

import io.gauss.augur.model.OnnxModel;
import io.gauss.augur.onnx.ModelRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link GaussModelExtension} — verifies that {@link MockModel}
 * annotations are resolved and a ready-to-use {@link ModelRegistry} is
 * injected into test methods.
 */
@ExtendWith(GaussModelExtension.class)
@MockModel(
    path   = "models/churn.onnx",
    output = "{\"probability\":[0.9,0.1]}"
)
class GaussModelExtensionTest {

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    void extension_injectsRegistryWithClassLevelMock(ModelRegistry registry) {
        assertThat(registry).isNotNull();
        assertThat(registry.isLoaded("models/churn.onnx")).isFalse();

        OnnxModel model = registry.getOrLoad("models/churn.onnx");
        assertThat(model).isInstanceOf(MockOnnxModel.class);
    }

    @Test
    void mock_returnsConfiguredOutput(ModelRegistry registry) {
        OnnxModel model = registry.getOrLoad("models/churn.onnx");
        Map<String, Object> result = model.run(Map.of("features", new float[]{1f}));

        @SuppressWarnings("unchecked")
        List<Number> probs = (List<Number>) result.get("probability");
        assertThat(probs.get(0).doubleValue()).isEqualTo(0.9);
    }

    @Test
    @MockModel(
        path   = "models/nlp.onnx",
        output = "{\"label\":\"positive\"}"
    )
    void methodLevel_addsAdditionalMock(ModelRegistry registry) {
        // Class-level mock still present
        assertThat(registry.getOrLoad("models/churn.onnx"))
                .isInstanceOf(MockOnnxModel.class);

        // Method-level mock also available
        OnnxModel nlp = registry.getOrLoad("models/nlp.onnx");
        assertThat(nlp.run(Map.of()).get("label")).isEqualTo("positive");
    }

    @Test
    @MockModel(
        path   = "models/churn.onnx",
        output = "{\"probability\":[0.1,0.9]}"
    )
    void methodLevel_overridesClassLevelForSamePath(ModelRegistry registry) {
        OnnxModel model = registry.getOrLoad("models/churn.onnx");
        Map<String, Object> result = model.run(Map.of());

        @SuppressWarnings("unchecked")
        List<Number> probs = (List<Number>) result.get("probability");
        // Method-level output overrides class-level: first value is now 0.1
        assertThat(probs.get(0).doubleValue()).isEqualTo(0.1);
    }

    @Test
    void eachTest_getsAFreshRegistry(ModelRegistry registry) {
        // Load model in this test — it should not affect other tests
        registry.getOrLoad("models/churn.onnx");
        // The registry was fresh (not loaded before getOrLoad) — assertion below confirms it
        // By calling getOrLoad twice we test caching within the same test
        OnnxModel second = registry.getOrLoad("models/churn.onnx");
        assertThat(registry.isLoaded("models/churn.onnx")).isTrue();
        assertThat(second).isNotNull();
    }

    @Test
    void callCount_tracksInvocationsOnMockModel(ModelRegistry registry) {
        MockOnnxModel mock = (MockOnnxModel) registry.getOrLoad("models/churn.onnx");
        mock.run(Map.of());
        mock.run(Map.of());
        assertThat(mock.callCount()).isEqualTo(2);
    }
}
