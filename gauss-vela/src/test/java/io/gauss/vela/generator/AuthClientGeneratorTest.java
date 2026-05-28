package io.gauss.vela.generator;

import io.gauss.core.annotation.MLEndpoint;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers HU-031 AC-4: generated TypeScript client includes auth token interceptor.
 */
class AuthClientGeneratorTest {

    private final ClientFunctionGenerator gen = new ClientFunctionGenerator();

    @Test
    void output_containsGetTokenFunction() {
        String ts = gen.generate(SimpleEndpoint.class);
        assertThat(ts).contains("function getToken()");
    }

    @Test
    void output_containsAuthFetchFunction() {
        String ts = gen.generate(SimpleEndpoint.class);
        assertThat(ts).contains("async function authFetch(");
    }

    @Test
    void authFetch_setsBearerToken() {
        String ts = gen.generate(SimpleEndpoint.class);
        assertThat(ts).contains("Authorization");
        assertThat(ts).contains("Bearer ${token}");
    }

    @Test
    void generatedFunction_usesAuthFetch_notRawFetch() {
        String ts = gen.generate(SimpleEndpoint.class);
        // Every endpoint call must go through authFetch
        assertThat(ts).contains("authFetch(");
        // authFetch internally calls the real fetch (return fetch(...))
        assertThat(ts).contains("return fetch(");
        // Endpoint bodies must NOT call fetch() directly — only authFetch()
        long directFetchInEndpoints = ts.lines()
                .filter(l -> l.contains("await fetch("))
                .count();
        assertThat(directFetchInEndpoints)
                .as("Endpoint bodies must call authFetch(), not raw fetch() directly")
                .isZero();
    }

    @Test
    void authFetch_readsFromLocalStorage() {
        String ts = gen.generate(SimpleEndpoint.class);
        assertThat(ts).contains("localStorage");
        assertThat(ts).contains("gauss.token");
    }

    // -----------------------------------------------------------------------
    // Fixture
    // -----------------------------------------------------------------------

    @MLEndpoint(path = "/api")
    static class SimpleEndpoint {
        public String getData() { return null; }
        public String postData(String body) { return null; }
    }
}
