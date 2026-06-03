package io.gauss.augur.tracing;

/**
 * SPI for exporting completed spans to a backend (HU-034).
 *
 * <p>Implementations can forward spans to OTLP (Jaeger, Tempo, Zipkin) or any
 * other backend.  For unit tests, use {@link InMemorySpanExporter}.
 */
@FunctionalInterface
public interface SpanExporter {

    /**
     * Called by {@link GaussTracer} when a span has been fully completed
     * (i.e., {@code close()} was called on the active span).
     *
     * @param span the completed, immutable span record
     */
    void export(GaussSpan span);
}
