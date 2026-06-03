package io.gauss.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method or type for automatic OpenTelemetry span generation (HU-034).
 *
 * <p>When placed on a method, the framework wraps each invocation in an OTel
 * span.  Spans are nested automatically if the calling thread already has an
 * active span, producing a parent–child trace tree.
 *
 * <p>Built-in instrumentation is applied automatically to all
 * {@link MLEndpoint} prediction methods and all {@link DataPipeline}
 * {@code @Transform} steps; this annotation is for user-defined methods that
 * also need tracing.
 *
 * <pre>{@code
 * @Traced(operationName = "feature-enrichment", kind = SpanKind.INTERNAL)
 * public FeatureVector enrich(CustomerInput input) { ... }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Traced {

    /**
     * Human-readable name for the span.  Defaults to {@code ""}, in which case
     * the framework uses {@code ClassName.methodName}.
     */
    String operationName() default "";

    /**
     * OTel span kind.  One of {@code "INTERNAL"} (default), {@code "SERVER"},
     * {@code "CLIENT"}, {@code "PRODUCER"}, {@code "CONSUMER"}.
     */
    String kind() default "INTERNAL";
}
