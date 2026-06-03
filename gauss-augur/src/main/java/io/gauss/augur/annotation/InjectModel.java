package io.gauss.augur.annotation;

import java.lang.annotation.*;

/**
 * Injects an ONNX model into a field of a {@code @MLEndpoint} class.
 *
 * <p>The framework resolves the model at startup by loading it from the
 * classpath path given in {@link #value()}.  The annotated field must be of
 * type {@link io.gauss.augur.model.OnnxModel}.
 *
 * <pre>{@code
 * @MLEndpoint
 * public class ChurnPredictionService {
 *
 *     @InjectModel("models/churn.onnx")
 *     OnnxModel churnModel;
 *
 *     public float[] predict(float[] features) {
 *         return (float[]) churnModel.run(Map.of("input", features)).get("output");
 *     }
 * }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface InjectModel {

    /**
     * Classpath-relative path to the {@code .onnx} model file.
     * Example: {@code "models/churn-v2.onnx"}.
     */
    String value();
}
