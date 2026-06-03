package io.gauss.flume.test;

import java.lang.annotation.*;

/**
 * Container annotation for repeatable {@link MockSource} declarations.
 * You do not need to use this annotation directly — use {@link MockSource}
 * with repetition instead.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface MockSources {
    MockSource[] value();
}
