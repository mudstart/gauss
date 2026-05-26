package io.gauss.vela.scanner;

import io.gauss.core.annotation.MLEndpoint;
import io.gauss.vela.model.EndpointMethod;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that EndpointScanner produces correct EndpointMethod descriptors
 * from @MLEndpoint classes (HU-007 AC-1, AC-2).
 */
class EndpointScannerTest {

    private final EndpointScanner scanner = new EndpointScanner();

    @Test
    void scansPublicMethodsOnly() {
        List<EndpointMethod> methods = scanner.scan(ChurnEndpoint.class);
        assertThat(methods).extracting(EndpointMethod::name)
                .containsExactlyInAnyOrder("predict", "status");
    }

    @Test
    void methodWithParameters_isPost() {
        EndpointMethod predict = methodNamed(scanner.scan(ChurnEndpoint.class), "predict");
        assertThat(predict.httpMethod()).isEqualTo("POST");
    }

    @Test
    void methodWithoutParameters_isGet() {
        EndpointMethod status = methodNamed(scanner.scan(ChurnEndpoint.class), "status");
        assertThat(status.httpMethod()).isEqualTo("GET");
    }

    @Test
    void parametersAreMappedInOrder() {
        EndpointMethod predict = methodNamed(scanner.scan(ChurnEndpoint.class), "predict");
        assertThat(predict.parameters()).hasSize(1);
        assertThat(predict.parameters().get(0).name()).isEqualTo("input");
        assertThat(predict.parameters().get(0).type().render()).isEqualTo("ChurnInput");
    }

    @Test
    void returnTypeIsMapped() {
        EndpointMethod predict = methodNamed(scanner.scan(ChurnEndpoint.class), "predict");
        assertThat(predict.returnType().render()).isEqualTo("ChurnResult");
    }

    @Test
    void pathIncludesClassPathAndMethodName() {
        EndpointMethod predict = methodNamed(scanner.scan(ChurnEndpoint.class), "predict");
        assertThat(predict.path()).isEqualTo("/churn/predict");
    }

    @Test
    void resultsAreSortedAlphabetically() {
        List<EndpointMethod> methods = scanner.scan(ChurnEndpoint.class);
        assertThat(methods).extracting(EndpointMethod::name)
                .isSortedAccordingTo(String::compareTo);
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    @MLEndpoint(path = "/churn")
    static class ChurnEndpoint {
        public ChurnResult predict(ChurnInput input) { return null; }
        public String status() { return "ok"; }
        @SuppressWarnings("unused")
        private void internal() {}
    }

    static class ChurnInput {}
    static class ChurnResult {}

    // -------------------------------------------------------------------------

    private static EndpointMethod methodNamed(List<EndpointMethod> methods, String name) {
        return methods.stream().filter(m -> m.name().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("Method not found: " + name));
    }
}
