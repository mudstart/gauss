package io.gauss.vela.model;

/**
 * Indicates whether an endpoint method returns a reactive stream.
 *
 * <ul>
 *   <li>{@link #NONE} — regular single-value return (Promise).</li>
 *   <li>{@link #MULTI} — Mutiny {@code Multi<T>} (Quarkus).</li>
 *   <li>{@link #FLUX}  — Reactor {@code Flux<T>} (Spring).</li>
 * </ul>
 */
public enum ReactiveKind {
    NONE, MULTI, FLUX;

    public boolean isStreaming() { return this != NONE; }
}
