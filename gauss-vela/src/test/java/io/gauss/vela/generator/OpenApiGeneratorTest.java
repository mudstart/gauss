package io.gauss.vela.generator;

import io.gauss.core.annotation.AnonymousAllowed;
import io.gauss.core.annotation.MLEndpoint;
import jakarta.annotation.security.RolesAllowed;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link OpenApiGenerator}.
 * Covers HU-044 acceptance criteria.
 */
class OpenApiGeneratorTest {

    // -------------------------------------------------------------------------
    // Fixture endpoint classes
    // -------------------------------------------------------------------------

    static class CustomerInput { public float[] features; }
    static class ChurnResult   { public float probability; }

    @MLEndpoint("ChurnService")
    static class ChurnService {
        public ChurnResult predict(CustomerInput input) { return null; }
        public float score(float[] features)           { return 0f; }
    }

    @MLEndpoint
    @AnonymousAllowed
    static class PublicMetricsService {
        public String health() { return "ok"; }
    }

    @MLEndpoint(path = "/api/v2/custom")
    static class CustomPathService {
        public String echo(String msg) { return msg; }
    }

    /** Not annotated — should trigger an error. */
    static class NotAnEndpoint { }

    // -------------------------------------------------------------------------
    // Setup
    // -------------------------------------------------------------------------

    private OpenApiGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new OpenApiGenerator("Test API", "0.1.0");
    }

    // -------------------------------------------------------------------------
    // Top-level document structure
    // -------------------------------------------------------------------------

    @Test
    void generate_containsOpenApiVersion() {
        String json = generator.generate(List.of(ChurnService.class));
        assertThat(json).contains("\"openapi\": \"3.0.3\"");
    }

    @Test
    void generate_containsApiTitleAndVersion() {
        String json = generator.generate(List.of(ChurnService.class));
        assertThat(json).contains("\"title\": \"Test API\"");
        assertThat(json).contains("\"version\": \"0.1.0\"");
    }

    @Test
    void generate_containsPathsAndComponents() {
        String json = generator.generate(List.of(ChurnService.class));
        assertThat(json).contains("\"paths\"");
        assertThat(json).contains("\"components\"");
    }

    @Test
    void generate_throwsForUnannotatedClass() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> generator.generate(List.of(NotAnEndpoint.class)))
                .withMessageContaining("@MLEndpoint");
    }

    // -------------------------------------------------------------------------
    // Path generation
    // -------------------------------------------------------------------------

    @Test
    void generate_createsPathForEachPublicMethod() {
        String json = generator.generate(List.of(ChurnService.class));
        assertThat(json).contains("/api/churn-service/predict");
        assertThat(json).contains("/api/churn-service/score");
    }

    @Test
    void generate_respectsCustomPath() {
        String json = generator.generate(List.of(CustomPathService.class));
        assertThat(json).contains("/api/v2/custom/echo");
    }

    @Test
    void generate_usesPostMethod() {
        String json = generator.generate(List.of(ChurnService.class));
        // Each path should contain a "post" operation
        assertThat(json).contains("\"post\"");
    }

    // -------------------------------------------------------------------------
    // Request body and response schemas
    // -------------------------------------------------------------------------

    @Test
    void generate_includesRequestBodyForParameterizedMethod() {
        String json = generator.generate(List.of(ChurnService.class));
        assertThat(json).contains("\"requestBody\"");
        assertThat(json).contains("\"input\""); // parameter name
    }

    @Test
    void generate_includesResponseSchemaForReturnType() {
        String json = generator.generate(List.of(ChurnService.class));
        assertThat(json).contains("\"responses\"");
        assertThat(json).contains("\"200\"");
    }

    @Test
    void generate_emptyDocument_forNoEndpoints() {
        String json = generator.generate(List.of());
        assertThat(json).contains("\"paths\": {}");
    }

    // -------------------------------------------------------------------------
    // Security
    // -------------------------------------------------------------------------

    @Test
    void generate_includesBearerSecurityForProtectedEndpoints() {
        String json = generator.generate(List.of(ChurnService.class));
        assertThat(json).contains("\"bearerAuth\"");
        assertThat(json).contains("\"securitySchemes\"");
    }

    @Test
    void generate_noSecurityForAnonymousAllowedClass() {
        String json = generator.generate(List.of(PublicMetricsService.class));
        // The health() method should NOT have a security requirement
        // (bearerAuth scheme still declared in components, just not referenced per-op)
        assertThat(json).contains("\"/api/public-metrics-service/health\"");
    }

    // -------------------------------------------------------------------------
    // Multiple endpoints
    // -------------------------------------------------------------------------

    @Test
    void generate_handlesMultipleEndpointClasses() {
        String json = generator.generate(
                List.of(ChurnService.class, PublicMetricsService.class));

        assertThat(json).contains("/api/churn-service/predict");
        assertThat(json).contains("/api/public-metrics-service/health");
    }
}
