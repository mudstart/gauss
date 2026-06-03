package io.gauss.augur.tracing;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Lightweight OTel-compatible tracer for Gauss framework spans (HU-034).
 *
 * <p>Spans are tracked per-thread via a {@link ThreadLocal} stack.  Starting a
 * new span while another is active creates a parent–child relationship
 * automatically.  All spans in the same call stack share the same
 * {@code traceId}.
 *
 * <p>Completed spans are forwarded to the configured {@link SpanExporter}
 * (default: no-op).  Wire {@link InMemorySpanExporter} in tests.
 *
 * <p>Usage:
 * <pre>{@code
 * GaussTracer tracer = new GaussTracer(spanExporter);
 *
 * try (GaussTracer.ActiveSpan span = tracer.startSpan("predict")) {
 *     span.setAttribute("endpoint", "churn");
 *     Object result = model.predict(input);
 * }  // span closed and exported here
 * }</pre>
 */
public final class GaussTracer {

    private static final ThreadLocal<Deque<ActiveSpanState>> STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    private final SpanExporter exporter;
    private final Supplier<Instant> clock;

    // -------------------------------------------------------------------------

    public GaussTracer() {
        this(span -> {}, Instant::now);
    }

    public GaussTracer(SpanExporter exporter) {
        this(exporter, Instant::now);
    }

    public GaussTracer(SpanExporter exporter, Supplier<Instant> clock) {
        this.exporter = exporter;
        this.clock    = clock;
    }

    // -------------------------------------------------------------------------
    // Span lifecycle
    // -------------------------------------------------------------------------

    /**
     * Starts a new span and pushes it onto the current thread's span stack.
     * If a span is already active, the new span becomes its child.
     *
     * @param name operation name
     * @return an {@link ActiveSpan} that must be closed (use try-with-resources)
     */
    public ActiveSpan startSpan(String name) {
        return startSpan(name, "INTERNAL");
    }

    /**
     * Starts a new span with an explicit kind.
     *
     * @param name operation name
     * @param kind one of {@code INTERNAL}, {@code SERVER}, {@code CLIENT},
     *             {@code PRODUCER}, {@code CONSUMER}
     */
    public ActiveSpan startSpan(String name, String kind) {
        Deque<ActiveSpanState> stack = STACK.get();

        String traceId  = stack.isEmpty()
                ? UUID.randomUUID().toString()
                : stack.peek().traceId;
        String spanId   = UUID.randomUUID().toString();
        String parentId = stack.isEmpty() ? null : stack.peek().spanId;

        ActiveSpanState state = new ActiveSpanState(
                traceId, spanId, parentId, name, kind, clock.get());
        stack.push(state);
        return new ActiveSpan(state);
    }

    // -------------------------------------------------------------------------
    // Inner classes
    // -------------------------------------------------------------------------

    /** A span that is currently in progress on the current thread. */
    public final class ActiveSpan implements AutoCloseable {

        private final ActiveSpanState state;

        private ActiveSpan(ActiveSpanState state) {
            this.state = state;
        }

        /** Records a key–value attribute on this span. */
        public ActiveSpan setAttribute(String key, String value) {
            state.attributes.put(key, value);
            return this;
        }

        /** Records an exception on this span (marks the span as errored). */
        public ActiveSpan recordException(Throwable t) {
            state.error = t;
            return this;
        }

        /** Closes (ends) the span, pops it from the stack, and exports it. */
        @Override
        public void close() {
            Deque<ActiveSpanState> stack = STACK.get();
            if (!stack.isEmpty() && stack.peek() == state) {
                stack.pop();
            }
            GaussSpan completed = new GaussSpan(
                    state.traceId,
                    state.spanId,
                    Optional.ofNullable(state.parentId),
                    state.name,
                    state.kind,
                    state.startTime,
                    clock.get(),
                    Map.copyOf(state.attributes),
                    Optional.ofNullable(state.error));
            exporter.export(completed);
        }
    }

    // -------------------------------------------------------------------------

    private static final class ActiveSpanState {
        final String  traceId;
        final String  spanId;
        final String  parentId;   // null for root
        final String  name;
        final String  kind;
        final Instant startTime;
        final Map<String, String> attributes = new HashMap<>();
        Throwable error;

        ActiveSpanState(String traceId, String spanId, String parentId,
                         String name, String kind, Instant startTime) {
            this.traceId   = traceId;
            this.spanId    = spanId;
            this.parentId  = parentId;
            this.name      = name;
            this.kind      = kind;
            this.startTime = startTime;
        }
    }
}
