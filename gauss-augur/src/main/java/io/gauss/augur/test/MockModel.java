package io.gauss.augur.test;

import java.lang.annotation.*;

/**
 * Declares a mock ONNX model for use in endpoint tests.
 *
 * <p>Apply on a test class or test method alongside
 * {@link GaussModelExtension}. Each {@code @MockModel} registers a
 * {@link MockOnnxModel} that returns the JSON-specified output map whenever
 * it is invoked, keyed by the classpath {@link #path()}.
 *
 * <pre>{@code
 * @ExtendWith(GaussModelExtension.class)
 * @MockModel(
 *     path   = "models/churn.onnx",
 *     output = "{\"probability\":[0.85]}"
 * )
 * class ChurnServiceTest {
 *
 *     @Test
 *     void predict_returnsHighChurnScore(ModelRegistry registry) {
 *         ChurnService service = new ChurnService();
 *         service.model = registry.getOrLoad("models/churn.onnx");
 *         assertThat(service.predict(new float[]{1,2,3})).isGreaterThan(0.8f);
 *     }
 * }
 * }</pre>
 *
 * <p>Method-level annotations override class-level annotations for the same
 * {@link #path()}.
 */
@Documented
@Repeatable(MockModels.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface MockModel {

    /**
     * Classpath path of the model to mock — must match the value in
     * {@link io.gauss.augur.annotation.InjectModel @InjectModel}.
     */
    String path();

    /**
     * JSON object whose entries are returned as the output map of every
     * {@link io.gauss.augur.model.OnnxModel#run OnnxModel.run()} call.
     * Array values are deserialized as Java arrays.
     *
     * <p>Example: {@code "{\"output\":[0.9,0.1]}"}
     */
    String output();
}
