package io.gauss.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Configures a {@link DataPipeline} class (or individual method) to execute
 * automatically on a cron schedule (Flume module, HU-013).
 *
 * <p>When placed on a {@code @DataPipeline} class the entire pipeline is
 * scheduled.  When placed on a method inside a pipeline class, only that
 * method's trigger is scheduled independently.
 *
 * <p>Cron syntax uses 5 space-separated fields:
 * {@code <minute> <hour> <day-of-month> <month> <day-of-week>}
 *
 * <pre>{@code
 * @Scheduled(cron = "0 2 * * *", description = "Daily retraining at 2 AM")
 * @DataPipeline("churn-retrain")
 * public class ChurnRetrainingPipeline { ... }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Scheduled {

    /**
     * Cron expression with 5 fields:
     * {@code minute hour day-of-month month day-of-week}.
     * Standard wildcard ({@code *}), ranges ({@code 1-5}), lists ({@code 1,3,5})
     * and steps ({@code *}{@code /2}) are supported.
     */
    String cron();

    /** Optional human-readable description shown in the admin UI. */
    String description() default "";
}
