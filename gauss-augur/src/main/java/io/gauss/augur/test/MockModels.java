package io.gauss.augur.test;

import java.lang.annotation.*;

/**
 * Container annotation for repeatable {@link MockModel} declarations.
 * Use {@link MockModel} directly with repetition rather than this type.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface MockModels {
    MockModel[] value();
}
