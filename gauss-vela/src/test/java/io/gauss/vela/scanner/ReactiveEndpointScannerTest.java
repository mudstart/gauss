package io.gauss.vela.scanner;

import io.gauss.core.annotation.MLEndpoint;
import io.gauss.vela.model.EndpointMethod;
import io.gauss.vela.model.ReactiveKind;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers HU-008 AC-1/AC-5: Multi&lt;T&gt; and Flux&lt;T&gt; detected as reactive streams
 * with inner type mapped correctly.
 *
 * Uses lightweight test stubs ({@code io.smallrye.mutiny.Multi} and
 * {@code reactor.core.publisher.Flux}) that have the canonical FQNs the
 * scanner checks, with no real Mutiny/Reactor runtime dependency needed.
 */
class ReactiveEndpointScannerTest {

    private final EndpointScanner scanner = new EndpointScanner();

    // -----------------------------------------------------------------------
    // AC-1: Multi<T> detected as MULTI streaming kind
    // -----------------------------------------------------------------------

    @Test
    void multiReturn_isDetectedAsStreaming() {
        EndpointMethod m = methodNamed(scanner.scan(StreamingEndpoint.class), "streamTokens");
        assertThat(m.reactiveKind()).isEqualTo(ReactiveKind.MULTI);
        assertThat(m.reactiveKind().isStreaming()).isTrue();
    }

    @Test
    void multiReturn_innerTypeIsMapped() {
        EndpointMethod m = methodNamed(scanner.scan(StreamingEndpoint.class), "streamTokens");
        assertThat(m.returnType().render()).isEqualTo("string");
    }

    // -----------------------------------------------------------------------
    // AC-1: Flux<T> detected as FLUX streaming kind
    // -----------------------------------------------------------------------

    @Test
    void fluxReturn_isDetectedAsStreaming() {
        EndpointMethod m = methodNamed(scanner.scan(StreamingEndpoint.class), "streamScores");
        assertThat(m.reactiveKind()).isEqualTo(ReactiveKind.FLUX);
    }

    @Test
    void fluxReturn_innerTypeIsMapped() {
        EndpointMethod m = methodNamed(scanner.scan(StreamingEndpoint.class), "streamScores");
        assertThat(m.returnType().render()).isEqualTo("number");
    }

    // -----------------------------------------------------------------------
    // AC-5: Regular return is not streaming
    // -----------------------------------------------------------------------

    @Test
    void regularReturn_isNotStreaming() {
        EndpointMethod m = methodNamed(scanner.scan(StreamingEndpoint.class), "predict");
        assertThat(m.reactiveKind()).isEqualTo(ReactiveKind.NONE);
        assertThat(m.reactiveKind().isStreaming()).isFalse();
    }

    // -----------------------------------------------------------------------
    // Fixtures
    // -----------------------------------------------------------------------

    @MLEndpoint(path = "/stream")
    static class StreamingEndpoint {
        public Multi<String>  streamTokens() { return null; }
        public Flux<Double>   streamScores() { return null; }
        public String         predict()      { return null; }
    }

    private static EndpointMethod methodNamed(List<EndpointMethod> methods, String name) {
        return methods.stream().filter(m -> m.name().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("Method not found: " + name));
    }
}
