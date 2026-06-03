package io.gauss.flume.test;

import java.lang.annotation.*;

/**
 * Declares a mock data source for a pipeline test.
 *
 * <p>Apply on a test class or test method alongside
 * {@link GaussPipelineExtension}. Each {@code @MockSource} replaces the
 * real source reader for the given URI with an in-memory stub that returns
 * the JSON payload specified in {@link #value()}.
 *
 * <pre>{@code
 * @ExtendWith(GaussPipelineExtension.class)
 * @MockSource(source = "file:///data/customers.json",
 *             value  = "[{\"id\":1,\"name\":\"Alice\"}]")
 * class ChurnPipelineTest {
 *
 *     @Test
 *     void pipeline_enrichesCustomers(PipelineTestRunner runner) {
 *         runner.register(ChurnPipeline.class);
 *         List<?> result = (List<?>) runner.run("churn-features");
 *         assertThat(result).hasSize(1);
 *     }
 * }
 * }</pre>
 *
 * <p>Method-level annotations override class-level annotations for the same
 * source URI.
 */
@Documented
@Repeatable(MockSources.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface MockSource {

    /**
     * The source URI to mock — must match the {@code source} attribute of an
     * {@link io.gauss.core.annotation.Ingest @Ingest} method exactly.
     */
    String source();

    /**
     * JSON string that will be deserialized into the return type of the
     * {@code @Ingest} method. Use a JSON object ({@code {}}) for single-record
     * ingests and a JSON array ({@code []}) for collection ingests.
     */
    String value();
}
