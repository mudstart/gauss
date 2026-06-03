package io.gauss.quarkus.endpoint;

import io.gauss.core.annotation.MLEndpoint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link MLEndpointDescriptor}.
 * Covers HU-016 acceptance criteria: endpoint descriptor creation,
 * path derivation, camelToKebab conversion, and method scanning.
 */
class MLEndpointDescriptorTest {

    // -------------------------------------------------------------------------
    // Fixture classes
    // -------------------------------------------------------------------------

    @MLEndpoint
    static class SimplePredictionService {
        public String predict(String input) { return input; }
        public int count()                  { return 0; }
    }

    @MLEndpoint("ChurnService")
    static class ChurnService {
        public float score(float[] features) { return 0f; }
    }

    @MLEndpoint(path = "/api/v2/custom")
    static class CustomPathService {
        public String echo(String msg) { return msg; }
    }

    @MLEndpoint("MyMLModel")
    static class CamelCaseNameService {
        public void run() {}
    }

    /** Not annotated — should trigger an error. */
    static class NotAnnotated {}

    // -------------------------------------------------------------------------
    // from() — error handling
    // -------------------------------------------------------------------------

    @Test
    void from_throwsForUnannotatedClass() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MLEndpointDescriptor.from(NotAnnotated.class))
                .withMessageContaining("@MLEndpoint");
    }

    // -------------------------------------------------------------------------
    // Name derivation
    // -------------------------------------------------------------------------

    @Test
    void from_usesAnnotationValue_whenProvided() {
        MLEndpointDescriptor desc = MLEndpointDescriptor.from(ChurnService.class);
        assertThat(desc.name()).isEqualTo("ChurnService");
    }

    @Test
    void from_usesSimpleClassName_whenValueIsBlank() {
        MLEndpointDescriptor desc = MLEndpointDescriptor.from(SimplePredictionService.class);
        assertThat(desc.name()).isEqualTo("SimplePredictionService");
    }

    // -------------------------------------------------------------------------
    // Path derivation
    // -------------------------------------------------------------------------

    @Test
    void from_derivesPathFromSimpleClassName_inKebabCase() {
        // "SimplePredictionService" has two [a-z][A-Z] boundaries:
        //   e,P => "Simple-PredictionService"
        //   n,S => "Simple-Prediction-Service"
        // lowercased: "simple-prediction-service"
        MLEndpointDescriptor desc = MLEndpointDescriptor.from(SimplePredictionService.class);
        assertThat(desc.httpBasePath()).isEqualTo("/api/simple-prediction-service");
    }

    @Test
    void from_derivesPathFromAnnotationValue_inKebabCase() {
        // "ChurnService": n,S is the only [a-z][A-Z] boundary => "churn-service"
        MLEndpointDescriptor desc = MLEndpointDescriptor.from(ChurnService.class);
        assertThat(desc.httpBasePath()).isEqualTo("/api/churn-service");
    }

    @Test
    void from_respectsExplicitPathAnnotation() {
        MLEndpointDescriptor desc = MLEndpointDescriptor.from(CustomPathService.class);
        assertThat(desc.httpBasePath()).isEqualTo("/api/v2/custom");
    }

    @Test
    void from_appliesCamelToKebab_onMultiWordName() {
        // "MyMLModel": only the y,M boundary fires => "My-MLModel" lowercased = "my-mlmodel"
        MLEndpointDescriptor desc = MLEndpointDescriptor.from(CamelCaseNameService.class);
        assertThat(desc.httpBasePath()).isEqualTo("/api/my-mlmodel");
    }

    // -------------------------------------------------------------------------
    // endpointClass
    // -------------------------------------------------------------------------

    @Test
    void from_storesEndpointClass() {
        MLEndpointDescriptor desc = MLEndpointDescriptor.from(ChurnService.class);
        assertThat(desc.endpointClass()).isEqualTo(ChurnService.class);
    }

    // -------------------------------------------------------------------------
    // Method scanning
    // -------------------------------------------------------------------------

    @Test
    void from_includesAllPublicNonObjectMethods() {
        MLEndpointDescriptor desc = MLEndpointDescriptor.from(SimplePredictionService.class);
        List<String> methodNames = desc.publicMethods().stream()
                .map(java.lang.reflect.Method::getName)
                .toList();
        assertThat(methodNames).contains("predict", "count");
    }

    @Test
    void from_excludesObjectMethods() {
        MLEndpointDescriptor desc = MLEndpointDescriptor.from(SimplePredictionService.class);
        List<String> methodNames = desc.publicMethods().stream()
                .map(java.lang.reflect.Method::getName)
                .toList();
        assertThat(methodNames).doesNotContain("toString", "hashCode", "equals", "getClass");
    }

    @Test
    void operationCount_matchesPublicMethodCount() {
        MLEndpointDescriptor desc = MLEndpointDescriptor.from(SimplePredictionService.class);
        assertThat(desc.operationCount()).isEqualTo(desc.publicMethods().size());
    }

    @Test
    void operationCount_isOne_forSingleMethodEndpoint() {
        MLEndpointDescriptor desc = MLEndpointDescriptor.from(ChurnService.class);
        assertThat(desc.operationCount()).isEqualTo(1);
    }
}
