package io.gauss.vela.generator;

import io.gauss.core.annotation.MLEndpoint;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers HU-008 AC-1 to AC-5: SSE-based AsyncIterable client generation.
 */
class StreamingClientGeneratorTest {

    private final ClientFunctionGenerator gen = new ClientFunctionGenerator();

    // -----------------------------------------------------------------------
    // AC-1: AsyncIterable<T> return type (async generator function)
    // -----------------------------------------------------------------------

    @Test
    void streamingMethod_generatesAsyncGenerator() {
        String ts = gen.generate(LlmEndpoint.class);
        assertThat(ts).contains("export async function* streamText(");
    }

    @Test
    void streamingMethod_returnsAsyncIterable() {
        String ts = gen.generate(LlmEndpoint.class);
        assertThat(ts).contains("): AsyncIterable<string>");
    }

    // -----------------------------------------------------------------------
    // AC-2: SSE via fetch + ReadableStream
    // -----------------------------------------------------------------------

    @Test
    void streamingMethod_usesSseProtocol() {
        String ts = gen.generate(LlmEndpoint.class);
        assertThat(ts).contains("response.body");
        assertThat(ts).contains("getReader()");
        assertThat(ts).contains("data: ");   // SSE line prefix
    }

    // -----------------------------------------------------------------------
    // AC-3: GaussApiError on non-ok response
    // -----------------------------------------------------------------------

    @Test
    void streamingMethod_throwsGaussApiError() {
        String ts = gen.generate(LlmEndpoint.class);
        assertThat(ts).contains("throw new GaussApiError(response.status");
    }

    // -----------------------------------------------------------------------
    // AC-4: AbortController cancellation support
    // -----------------------------------------------------------------------

    @Test
    void streamingMethod_acceptsAbortSignal() {
        String ts = gen.generate(LlmEndpoint.class);
        assertThat(ts).contains("signal?: AbortSignal");
        assertThat(ts).contains("{ signal }");
    }

    // -----------------------------------------------------------------------
    // AC-5: Correct iterator typing via yield
    // -----------------------------------------------------------------------

    @Test
    void streamingMethod_usesYield() {
        String ts = gen.generate(LlmEndpoint.class);
        assertThat(ts).contains("yield JSON.parse");
    }

    @Test
    void streamingMethod_releasesReaderOnFinish() {
        String ts = gen.generate(LlmEndpoint.class);
        assertThat(ts).contains("reader.releaseLock()");
    }

    // -----------------------------------------------------------------------
    // Non-streaming methods still work normally
    // -----------------------------------------------------------------------

    @Test
    void nonStreamingMethod_generatesNormalPromise() {
        String ts = gen.generate(LlmEndpoint.class);
        assertThat(ts).contains("export async function predict(");
        assertThat(ts).contains("): Promise<string>");
    }

    // -----------------------------------------------------------------------
    // Flux also generates async generator
    // -----------------------------------------------------------------------

    @Test
    void fluxMethod_generatesAsyncGenerator() {
        String ts = gen.generate(ScoreEndpoint.class);
        assertThat(ts).contains("export async function* streamScores(");
        assertThat(ts).contains("): AsyncIterable<number>");
    }

    // -----------------------------------------------------------------------
    // Fixtures
    // -----------------------------------------------------------------------

    @MLEndpoint(path = "/llm")
    static class LlmEndpoint {
        public Multi<String> streamText(String prompt) { return null; }
        public String predict(String input) { return null; }
    }

    @MLEndpoint(path = "/scores")
    static class ScoreEndpoint {
        public Flux<Double> streamScores() { return null; }
    }
}
