package io.gauss.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as the data ingestion step of a {@link DataPipeline}.
 *
 * <p>Supported source URI schemes:
 * <ul>
 *   <li>{@code jdbc://datasource/table} — reads from a configured JDBC datasource.</li>
 *   <li>{@code file:///path/to/data.csv} — reads CSV, JSON or Parquet files.</li>
 *   <li>{@code http://api.example.com/data} — fetches and deserializes JSON from a REST API.</li>
 * </ul>
 *
 * <pre>{@code
 * @Ingest(source = "jdbc://ds-main/transactions")
 * public Dataset<Transaction> loadTransactions() { return null; }  // framework fills this
 *
 * @Ingest(source = "file:///data/labels.parquet")
 * public Dataset<Label> loadLabels() { return null; }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Ingest {

    /** URI of the data source. */
    String source();

    /** Optional cron schedule for triggered ingestion (e.g. {@code "0 2 * * *"}). */
    String schedule() default "";
}
