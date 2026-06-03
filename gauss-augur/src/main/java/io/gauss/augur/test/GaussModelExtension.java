package io.gauss.augur.test;

import io.gauss.augur.onnx.ModelLoader;
import io.gauss.augur.onnx.ModelRegistry;
import org.junit.jupiter.api.extension.*;

import java.io.IOException;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * JUnit 5 extension that wires {@link MockModel} annotations into a
 * {@link ModelRegistry} and injects it into test methods as a parameter.
 *
 * <p>Usage:
 * <pre>{@code
 * @ExtendWith(GaussModelExtension.class)
 * @MockModel(path = "models/churn.onnx", output = "{\"prob\":[0.9]}")
 * class ChurnServiceTest {
 *
 *     @Test
 *     void highRiskCustomer_returnsPositivePrediction(ModelRegistry registry) {
 *         ChurnService svc = new ChurnService();
 *         svc.model = registry.getOrLoad("models/churn.onnx");
 *         float prob = (float) ((List<?>) svc.predict(features).get("prob")).get(0);
 *         assertThat(prob).isGreaterThan(0.5f);
 *     }
 * }
 * }</pre>
 *
 * <p>A fresh {@link ModelRegistry} (backed by {@link MockOnnxModel} stubs) is
 * created before each test.  Method-level {@code @MockModel} entries override
 * class-level entries for the same {@link MockModel#path() path}.
 */
public class GaussModelExtension implements BeforeEachCallback, ParameterResolver {

    private static final ExtensionContext.Namespace NS =
            ExtensionContext.Namespace.create(GaussModelExtension.class);

    private static final String REGISTRY_KEY = "modelRegistry";

    // -------------------------------------------------------------------------
    // BeforeEachCallback
    // -------------------------------------------------------------------------

    @Override
    public void beforeEach(ExtensionContext context) {
        Map<String, String> mocks = collectMocks(context);

        List<ModelLoader> loaders = mocks.entrySet().stream()
                .<ModelLoader>map(e -> new MockModelLoader(e.getKey(), e.getValue()))
                .toList();

        ModelRegistry registry = new ModelRegistry(loaders);
        context.getStore(NS).put(REGISTRY_KEY, registry);
    }

    // -------------------------------------------------------------------------
    // ParameterResolver — injects ModelRegistry into test method parameters
    // -------------------------------------------------------------------------

    @Override
    public boolean supportsParameter(ParameterContext parameterContext,
                                     ExtensionContext extensionContext) {
        return parameterContext.getParameter().getType().equals(ModelRegistry.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext,
                                   ExtensionContext extensionContext) {
        return extensionContext.getStore(NS).get(REGISTRY_KEY, ModelRegistry.class);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Map<String, String> collectMocks(ExtensionContext context) {
        Map<String, String> mocks = new LinkedHashMap<>();
        context.getTestClass().ifPresent(cls -> addMocks(cls, mocks));
        context.getTestMethod().ifPresent(m  -> addMocks(m,   mocks));
        return mocks;
    }

    private static void addMocks(AnnotatedElement element, Map<String, String> mocks) {
        MockModels container = element.getAnnotation(MockModels.class);
        if (container != null) {
            for (MockModel mm : container.value()) {
                mocks.put(mm.path(), mm.output());
            }
        }
        MockModel single = element.getAnnotation(MockModel.class);
        if (single != null) {
            mocks.put(single.path(), single.output());
        }
    }

    // -------------------------------------------------------------------------
    // Inner ModelLoader adapter
    // -------------------------------------------------------------------------

    /** Wraps a {@link MockOnnxModel} as a {@link ModelLoader}. */
    private static final class MockModelLoader implements ModelLoader {
        private final String path;
        private final String outputJson;

        MockModelLoader(String path, String outputJson) {
            this.path       = path;
            this.outputJson = outputJson;
        }

        @Override public boolean supports(String p) { return path.equals(p); }

        @Override public io.gauss.augur.model.OnnxModel load(String p) {
            return new MockOnnxModel(p, outputJson);
        }
    }
}
