package io.gauss.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a Gauss ML endpoint.
 *
 * <p>The framework automatically:
 * <ul>
 *   <li>Registers an HTTP endpoint for every public method.</li>
 *   <li>Generates a typed TypeScript client via Vela at build-time.</li>
 *   <li>Applies authentication by default (override with {@link AnonymousAllowed}).</li>
 *   <li>Records Micrometer metrics per call.</li>
 * </ul>
 *
 * <pre>{@code
 * @MLEndpoint
 * public class ChurnPredictionService {
 *
 *     @InjectModel("models/churn.onnx")
 *     OnnxModel model;
 *
 *     public ChurnResult predict(CustomerInput input) {
 *         return model.predict(input, ChurnResult.class);
 *     }
 * }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface MLEndpoint {

    /** Logical name used in metrics and routing. Defaults to simple class name. */
    String value() default "";

    /** Base path for the generated HTTP endpoint. Defaults to "/api/{name}". */
    String path() default "";
}
