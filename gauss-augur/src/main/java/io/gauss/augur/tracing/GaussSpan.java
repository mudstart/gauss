package io.gauss.augur.tracing;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable snapshot of a completed OTel-style span (HU-034).
 *
 * <p>Spans are produced by {@link GaussTracer} and captured by registered
 * {@link SpanExporter} implementations.  The {@link InMemorySpanExporter}
 * is the default for unit tests.
 *
 * @param traceId    trace identifier shared by all spans in the same request
 * @param spanId     unique identifier for this span
 * @param parentId   identifier of the parent span, or empty for root spans
 * @param name       operation name (from {@link io.gauss.core.annotation.Traced}
 *                   or auto-derived)
 * @param kind       span kind (INTERNAL, SERVER, CLIENT, PRODUCER, CONSUMER)
 * @param startTime  wall-clock time when the span started
 * @param endTime    wall-clock time when the span ended
 * @param attributes key–value pairs recorded during the span
 * @param error      exception caught during the span, if any
 */
public record GaussSpan(
        String              traceId,
        String              spanId,
        Optional<String>    parentId,
        String              name,
        String              kind,
        Instant             startTime,
        Instant             endTime,
        Map<String, String> attributes,
        Optional<Throwable> error
) {

    public GaussSpan {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    /** Duration of the span in milliseconds. */
    public long durationMs() {
        return endTime.toEpochMilli() - startTime.toEpochMilli();
    }

    /** Returns {@code true} if this span recorded an exception. */
    public boolean hasError() {
        return error.isPresent();
    }

    /** Returns {@code true} if this is a root span (no parent). */
    public boolean isRoot() {
        return parentId.isEmpty();
    }
}
