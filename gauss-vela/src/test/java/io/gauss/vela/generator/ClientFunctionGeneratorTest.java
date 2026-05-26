package io.gauss.vela.generator;

import io.gauss.core.annotation.MLEndpoint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers HU-007 AC-1 (async + JSDoc), AC-2 (params in order),
 * AC-3 (GaussApiError), AC-4 (file name).
 */
class ClientFunctionGeneratorTest {

    private final ClientFunctionGenerator gen = new ClientFunctionGenerator();

    // -----------------------------------------------------------------------
    // AC-1: async function with JSDoc
    // -----------------------------------------------------------------------

    @Test
    void generatedFunction_isAsync() {
        String ts = gen.generate(PredictEndpoint.class);
        assertThat(ts).contains("export async function predict(");
    }

    @Test
    void generatedFunction_hasJsDoc() {
        String ts = gen.generate(PredictEndpoint.class);
        assertThat(ts).contains("/**");
        assertThat(ts).contains(" * POST /ml/predict");
        assertThat(ts).contains(" */");
    }

    @Test
    void getMethod_hasJsDocWithGet() {
        String ts = gen.generate(PredictEndpoint.class);
        assertThat(ts).contains(" * GET /ml/health");
    }

    // -----------------------------------------------------------------------
    // AC-2: parameters mapped in order
    // -----------------------------------------------------------------------

    @Test
    void parameters_areMappedInOrder() {
        String ts = gen.generate(PredictEndpoint.class);
        // predict(ModelInput input) → predict(input: ModelInput)
        assertThat(ts).contains("export async function predict(input: ModelInput)");
    }

    @Test
    void returnType_isWrappedInPromise() {
        String ts = gen.generate(PredictEndpoint.class);
        assertThat(ts).contains("): Promise<ModelOutput>");
    }

    @Test
    void voidReturn_usesPromiseVoid() {
        String ts = gen.generate(NotifyEndpoint.class);
        assertThat(ts).contains("): Promise<void>");
    }

    // -----------------------------------------------------------------------
    // AC-3: typed error class
    // -----------------------------------------------------------------------

    @Test
    void output_containsGaussApiError() {
        String ts = gen.generate(PredictEndpoint.class);
        assertThat(ts).contains("export class GaussApiError extends Error");
    }

    @Test
    void errorThrown_whenResponseNotOk() {
        String ts = gen.generate(PredictEndpoint.class);
        assertThat(ts).contains("throw new GaussApiError(response.status");
    }

    // -----------------------------------------------------------------------
    // AC-4: file name derived from class name
    // -----------------------------------------------------------------------

    @Test
    void fileName_isClassNameDotTs() {
        assertThat(gen.fileName(PredictEndpoint.class)).isEqualTo("PredictEndpoint.ts");
    }

    // -----------------------------------------------------------------------
    // Fixtures
    // -----------------------------------------------------------------------

    @MLEndpoint(path = "/ml")
    static class PredictEndpoint {
        public ModelOutput predict(ModelInput input) { return null; }
        public String health() { return "ok"; }
    }

    @MLEndpoint(path = "/notify")
    static class NotifyEndpoint {
        public void send(String message) {}
    }

    static class ModelInput {}
    static class ModelOutput {}
}
