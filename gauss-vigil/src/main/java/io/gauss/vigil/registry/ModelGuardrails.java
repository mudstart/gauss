package io.gauss.vigil.registry;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Container annotation for repeatable {@link ModelGuardrail}.
 *
 * <p>This type is used automatically by the Java compiler when multiple
 * {@code @ModelGuardrail} annotations are placed on the same element.
 * You should not normally reference it directly.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface ModelGuardrails {
    ModelGuardrail[] value();
}
