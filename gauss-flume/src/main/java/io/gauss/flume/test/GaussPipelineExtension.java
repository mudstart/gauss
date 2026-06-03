package io.gauss.flume.test;

import org.junit.jupiter.api.extension.*;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * JUnit 5 extension that wires {@link MockSource} annotations into a
 * {@link PipelineTestRunner} and injects it into test methods as a parameter.
 *
 * <p>Declare it on your test class and annotate with {@link MockSource}:
 * <pre>{@code
 * @ExtendWith(GaussPipelineExtension.class)
 * @MockSource(source = "file:///data/customers.json",
 *             value  = "[{\"id\":1,\"name\":\"Alice\"}]")
 * class ChurnPipelineTest {
 *
 *     @Test
 *     void enriches_customers(PipelineTestRunner runner) {
 *         Object result = runner.run(ChurnPipeline.class);
 *         assertThat(result)...;
 *     }
 * }
 * }</pre>
 *
 * <p>Method-level {@code @MockSource} annotations are merged on top of
 * class-level ones; the method-level entry wins on URI conflicts.
 *
 * <p>A fresh {@link PipelineTestRunner} is created before each test method
 * so that tests are fully isolated.
 */
public class GaussPipelineExtension
        implements BeforeEachCallback, ParameterResolver {

    private static final ExtensionContext.Namespace NS =
            ExtensionContext.Namespace.create(GaussPipelineExtension.class);

    private static final String RUNNER_KEY = "runner";

    // -------------------------------------------------------------------------
    // BeforeEachCallback — build a fresh runner before every test
    // -------------------------------------------------------------------------

    @Override
    public void beforeEach(ExtensionContext context) {
        Map<String, String> mocks = collectMocks(context);
        PipelineTestRunner runner = new PipelineTestRunner();
        mocks.forEach(runner::mockSource);
        context.getStore(NS).put(RUNNER_KEY, runner);
    }

    // -------------------------------------------------------------------------
    // ParameterResolver — inject PipelineTestRunner into test methods
    // -------------------------------------------------------------------------

    @Override
    public boolean supportsParameter(ParameterContext parameterContext,
                                     ExtensionContext extensionContext) {
        return parameterContext.getParameter().getType()
                .equals(PipelineTestRunner.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext,
                                   ExtensionContext extensionContext) {
        return extensionContext.getStore(NS).get(RUNNER_KEY, PipelineTestRunner.class);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Collects {@link MockSource} entries from class-level and method-level
     * annotations, with method-level entries taking precedence on URI conflicts.
     */
    private static Map<String, String> collectMocks(ExtensionContext context) {
        // Class-level first, then method-level overrides
        Map<String, String> mocks = new LinkedHashMap<>();

        Optional<Class<?>> testClass = context.getTestClass();
        testClass.ifPresent(cls -> addMocks(cls, mocks));

        Optional<Method> testMethod = context.getTestMethod();
        testMethod.ifPresent(m -> addMocks(m, mocks));

        return mocks;
    }

    private static void addMocks(AnnotatedElement element, Map<String, String> mocks) {
        MockSources container = element.getAnnotation(MockSources.class);
        if (container != null) {
            for (MockSource ms : container.value()) {
                mocks.put(ms.source(), ms.value());
            }
        }
        MockSource single = element.getAnnotation(MockSource.class);
        if (single != null) {
            mocks.put(single.source(), single.value());
        }
    }
}
