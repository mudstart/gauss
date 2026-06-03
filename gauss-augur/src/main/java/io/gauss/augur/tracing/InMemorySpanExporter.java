package io.gauss.augur.tracing;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory {@link SpanExporter} for unit tests (HU-034).
 *
 * <p>Captures every exported span so tests can assert on trace structure,
 * span names, attributes, durations, and parent–child relationships.
 *
 * <pre>{@code
 * InMemorySpanExporter exporter = new InMemorySpanExporter();
 * GaussTracer tracer = new GaussTracer(exporter);
 *
 * try (var span = tracer.startSpan("my-op")) {
 *     doWork();
 * }
 * assertThat(exporter.spans()).hasSize(1);
 * assertThat(exporter.spans().get(0).name()).isEqualTo("my-op");
 * }</pre>
 */
public final class InMemorySpanExporter implements SpanExporter {

    private final CopyOnWriteArrayList<GaussSpan> captured = new CopyOnWriteArrayList<>();

    @Override
    public void export(GaussSpan span) {
        captured.add(span);
    }

    /** Returns all captured spans in export order. */
    public List<GaussSpan> spans() {
        return List.copyOf(captured);
    }

    /** Returns the number of captured spans. */
    public int size() {
        return captured.size();
    }

    /** Clears all captured spans (for test isolation). */
    public void reset() {
        captured.clear();
    }

    /** Returns the first captured span whose {@code name} equals {@code spanName}. */
    public java.util.Optional<GaussSpan> findByName(String spanName) {
        return captured.stream().filter(s -> s.name().equals(spanName)).findFirst();
    }
}
