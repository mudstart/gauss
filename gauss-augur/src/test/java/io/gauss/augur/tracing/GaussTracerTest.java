package io.gauss.augur.tracing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link GaussTracer}, {@link GaussSpan} and {@link InMemorySpanExporter}.
 * Covers HU-034 acceptance criteria.
 */
class GaussTracerTest {

    private InMemorySpanExporter exporter;
    private GaussTracer          tracer;

    @BeforeEach
    void setUp() {
        exporter = new InMemorySpanExporter();
        tracer   = new GaussTracer(exporter);
    }

    // -------------------------------------------------------------------------
    // Basic span lifecycle
    // -------------------------------------------------------------------------

    @Test
    void startSpan_exportedOnClose() {
        try (var span = tracer.startSpan("predict")) {
            // work
        }
        assertThat(exporter.size()).isEqualTo(1);
    }

    @Test
    void startSpan_nameIsCorrect() {
        try (var span = tracer.startSpan("churn-predict")) { }
        assertThat(exporter.spans().get(0).name()).isEqualTo("churn-predict");
    }

    @Test
    void startSpan_defaultKind_isInternal() {
        try (var span = tracer.startSpan("op")) { }
        assertThat(exporter.spans().get(0).kind()).isEqualTo("INTERNAL");
    }

    @Test
    void startSpan_customKind() {
        try (var span = tracer.startSpan("ingest", "SERVER")) { }
        assertThat(exporter.spans().get(0).kind()).isEqualTo("SERVER");
    }

    @Test
    void span_hasNonNullStartAndEndTime() {
        try (var span = tracer.startSpan("op")) { }
        GaussSpan s = exporter.spans().get(0);
        assertThat(s.startTime()).isNotNull();
        assertThat(s.endTime()).isNotNull();
    }

    @Test
    void span_durationMs_isNonNegative() {
        try (var span = tracer.startSpan("op")) { }
        assertThat(exporter.spans().get(0).durationMs()).isGreaterThanOrEqualTo(0);
    }

    // -------------------------------------------------------------------------
    // Attributes
    // -------------------------------------------------------------------------

    @Test
    void setAttribute_storedOnSpan() {
        try (var span = tracer.startSpan("predict")) {
            span.setAttribute("endpoint", "churn")
                .setAttribute("model", "v2");
        }
        GaussSpan s = exporter.spans().get(0);
        assertThat(s.attributes()).containsEntry("endpoint", "churn");
        assertThat(s.attributes()).containsEntry("model", "v2");
    }

    // -------------------------------------------------------------------------
    // Error recording
    // -------------------------------------------------------------------------

    @Test
    void recordException_marksSpanAsErrored() {
        try (var span = tracer.startSpan("failing-op")) {
            span.recordException(new RuntimeException("boom"));
        }
        assertThat(exporter.spans().get(0).hasError()).isTrue();
    }

    @Test
    void noException_spanHasNoError() {
        try (var span = tracer.startSpan("ok-op")) { }
        assertThat(exporter.spans().get(0).hasError()).isFalse();
    }

    // -------------------------------------------------------------------------
    // Parent–child (nested spans)
    // -------------------------------------------------------------------------

    @Test
    void nestedSpans_shareTraceId() {
        try (var parent = tracer.startSpan("parent")) {
            try (var child = tracer.startSpan("child")) { }
        }
        String parentTrace = exporter.spans().get(0).traceId();
        String childTrace  = exporter.spans().get(1).traceId();
        assertThat(childTrace).isEqualTo(parentTrace);
    }

    @Test
    void nestedSpan_hasParentId() {
        try (var parent = tracer.startSpan("parent")) {
            try (var child = tracer.startSpan("child")) { }
        }
        // child exported first (inner closes first)
        GaussSpan child  = exporter.spans().get(0);
        GaussSpan parent = exporter.spans().get(1);
        assertThat(child.parentId()).hasValue(parent.spanId());
    }

    @Test
    void rootSpan_hasNoParentId() {
        try (var root = tracer.startSpan("root")) { }
        assertThat(exporter.spans().get(0).isRoot()).isTrue();
        assertThat(exporter.spans().get(0).parentId()).isEmpty();
    }

    @Test
    void separateRootSpans_haveDifferentTraceIds() {
        try (var s1 = tracer.startSpan("span-1")) { }
        try (var s2 = tracer.startSpan("span-2")) { }
        String t1 = exporter.spans().get(0).traceId();
        String t2 = exporter.spans().get(1).traceId();
        assertThat(t1).isNotEqualTo(t2);
    }

    // -------------------------------------------------------------------------
    // InMemorySpanExporter
    // -------------------------------------------------------------------------

    @Test
    void exporter_findByName_returnsMatchingSpan() {
        try (var s = tracer.startSpan("feature-enrichment")) { }
        assertThat(exporter.findByName("feature-enrichment")).isPresent();
    }

    @Test
    void exporter_findByName_returnsEmpty_whenNotFound() {
        assertThat(exporter.findByName("nonexistent")).isEmpty();
    }

    @Test
    void exporter_reset_clearsAllSpans() {
        try (var s = tracer.startSpan("op")) { }
        exporter.reset();
        assertThat(exporter.size()).isZero();
    }

    // -------------------------------------------------------------------------
    // Clock control
    // -------------------------------------------------------------------------

    @Test
    void customClock_durationReflectsAdvance() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.ofEpochMilli(1000));
        GaussTracer timedTracer = new GaussTracer(exporter, now::get);
        try (var span = timedTracer.startSpan("timed")) {
            now.set(Instant.ofEpochMilli(1050));  // advance 50ms
        }
        assertThat(exporter.spans().get(0).durationMs()).isEqualTo(50);
    }
}
