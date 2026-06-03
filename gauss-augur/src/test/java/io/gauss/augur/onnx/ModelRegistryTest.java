package io.gauss.augur.onnx;

import io.gauss.augur.model.ModelDescriptor;
import io.gauss.augur.model.OnnxModel;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ModelRegistry}.
 */
class ModelRegistryTest {

    // Stub OnnxModel for tests
    static class StubModel implements OnnxModel {
        private final String name;
        boolean closed = false;

        StubModel(String name) { this.name = name; }

        @Override public Map<String, Object> run(Map<String, Object> in) { return Map.of(); }
        @Override public ModelDescriptor descriptor() {
            return new ModelDescriptor(name, name + ".onnx", List.of(), List.of());
        }
        @Override public void close() { closed = true; }
    }

    // Stub ModelLoader
    static class StubLoader implements ModelLoader {
        private final String path;
        private final OnnxModel model;
        int loadCount = 0;

        StubLoader(String path, OnnxModel model) { this.path = path; this.model = model; }

        @Override public boolean supports(String p) { return path.equals(p); }
        @Override public OnnxModel load(String p) { loadCount++; return model; }
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    void getOrLoad_returnsModelForMatchingPath() {
        StubModel model = new StubModel("test");
        StubLoader loader = new StubLoader("models/test.onnx", model);

        ModelRegistry registry = new ModelRegistry(List.of(loader));
        OnnxModel result = registry.getOrLoad("models/test.onnx");

        assertThat(result).isSameAs(model);
    }

    @Test
    void getOrLoad_cachesSameInstanceOnRepeatedCalls() {
        StubModel model = new StubModel("cached");
        StubLoader loader = new StubLoader("models/cached.onnx", model);

        ModelRegistry registry = new ModelRegistry(List.of(loader));
        OnnxModel first  = registry.getOrLoad("models/cached.onnx");
        OnnxModel second = registry.getOrLoad("models/cached.onnx");

        assertThat(first).isSameAs(second);
        assertThat(loader.loadCount).isEqualTo(1);   // loaded only once
    }

    @Test
    void isLoaded_returnsFalseBeforeFirstAccess() {
        ModelRegistry registry = new ModelRegistry(List.of());
        assertThat(registry.isLoaded("models/test.onnx")).isFalse();
    }

    @Test
    void isLoaded_returnsTrueAfterGetOrLoad() {
        StubModel model = new StubModel("loaded");
        StubLoader loader = new StubLoader("models/loaded.onnx", model);

        ModelRegistry registry = new ModelRegistry(List.of(loader));
        registry.getOrLoad("models/loaded.onnx");

        assertThat(registry.isLoaded("models/loaded.onnx")).isTrue();
    }

    @Test
    void getOrLoad_throwsWhenNoLoaderSupports() {
        ModelRegistry registry = new ModelRegistry(List.of());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> registry.getOrLoad("models/unsupported.pkl"))
                .withMessageContaining("No ModelLoader supports path");
    }

    @Test
    void close_closesAllCachedModels() {
        StubModel modelA = new StubModel("a");
        StubModel modelB = new StubModel("b");
        StubLoader loaderA = new StubLoader("a.onnx", modelA);
        StubLoader loaderB = new StubLoader("b.onnx", modelB);

        ModelRegistry registry = new ModelRegistry(List.of(loaderA, loaderB));
        registry.getOrLoad("a.onnx");
        registry.getOrLoad("b.onnx");

        registry.close();

        assertThat(modelA.closed).isTrue();
        assertThat(modelB.closed).isTrue();
    }

    @Test
    void registerLoader_takePriorityOverDefaultLoaders() {
        StubModel customModel = new StubModel("custom");
        StubLoader customLoader = new StubLoader("models/x.onnx", customModel);

        // Registry with no default loaders; custom loader registered after construction
        ModelRegistry registry = new ModelRegistry(List.of());
        registry.registerLoader(customLoader);

        OnnxModel result = registry.getOrLoad("models/x.onnx");
        assertThat(result).isSameAs(customModel);
    }
}
