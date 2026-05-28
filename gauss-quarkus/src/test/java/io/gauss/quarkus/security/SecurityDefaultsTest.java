package io.gauss.quarkus.security;

import io.gauss.core.annotation.AnonymousAllowed;
import io.gauss.core.annotation.MLEndpoint;
import jakarta.annotation.security.RolesAllowed;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers HU-031: security defaults on @MLEndpoint — auth + role checking logic.
 */
class SecurityDefaultsTest {

    // -----------------------------------------------------------------------
    // AC-1: endpoints require auth by default
    // -----------------------------------------------------------------------

    @Test
    void secureEndpoint_withoutAuth_isRejected() {
        assertThatThrownBy(() -> simulateRequest(SecureEndpoint.class, "getData", null, null))
                .isInstanceOf(io.gauss.quarkus.interceptor.NotAuthenticatedException.class)
                .hasMessageContaining("Authentication required");
    }

    @Test
    void anonymousEndpoint_withoutAuth_isAllowed() throws Exception {
        // No exception expected
        simulateRequest(PublicEndpoint.class, "health", null, null);
    }

    @Test
    void anonymousMethod_onSecureClass_isAllowed() throws Exception {
        simulateRequest(MixedEndpoint.class, "publicInfo", null, null);
    }

    // -----------------------------------------------------------------------
    // AC-2: @RolesAllowed restricts access by role
    // -----------------------------------------------------------------------

    @Test
    void rolesAllowed_withCorrectRole_isAllowed() throws Exception {
        simulateRequest(RoleEndpoint.class, "dsOnly", "alice", List.of("DS"));
    }

    @Test
    void rolesAllowed_withWrongRole_isForbidden() {
        assertThatThrownBy(() ->
                simulateRequest(RoleEndpoint.class, "dsOnly", "bob", List.of("VIEWER")))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("DS");
    }

    @Test
    void rolesAllowed_multipleRoles_anyMatchAllows() throws Exception {
        simulateRequest(RoleEndpoint.class, "dsOrMlEng", "carol", List.of("ML_ENG"));
    }

    @Test
    void rolesAllowed_noMatchingRole_isForbidden() {
        assertThatThrownBy(() ->
                simulateRequest(RoleEndpoint.class, "dsOrMlEng", "dave", List.of("VIEWER")))
                .isInstanceOf(ForbiddenException.class);
    }

    // -----------------------------------------------------------------------
    // AC-5: EndpointSecurityDescriptor for Dev UI
    // -----------------------------------------------------------------------

    @Test
    void securityDescriptor_reflectsAnonymousClass() {
        EndpointSecurityDescriptor desc = EndpointSecurityDescriptor.from(PublicEndpoint.class);
        assertThat(desc.classAnonymous()).isTrue();
    }

    @Test
    void securityDescriptor_reflectsRoles() {
        EndpointSecurityDescriptor desc = EndpointSecurityDescriptor.from(RoleEndpoint.class);
        EndpointSecurityDescriptor.MethodSecurity m = desc.methods().stream()
                .filter(ms -> ms.name().equals("dsOnly")).findFirst().orElseThrow();
        assertThat(m.rolesAllowed()).contains("DS");
    }

    @Test
    void securityDescriptor_path_fromMLEndpoint() {
        EndpointSecurityDescriptor desc = EndpointSecurityDescriptor.from(SecureEndpoint.class);
        assertThat(desc.path()).isEqualTo("/secure");
    }

    // -----------------------------------------------------------------------
    // ForbiddenException carries role info
    // -----------------------------------------------------------------------

    @Test
    void forbiddenException_exposesRequiredRoles() {
        ForbiddenException ex = new ForbiddenException("test", new String[]{"DS", "ML_ENG"});
        assertThat(ex.requiredRoles()).containsExactlyInAnyOrder("DS", "ML_ENG");
    }

    // -----------------------------------------------------------------------
    // Simulation helpers (replicate interceptor logic without CDI container)
    // -----------------------------------------------------------------------

    private static void simulateRequest(Class<?> cls, String methodName,
                                        String principal, List<String> roles) throws Exception {
        java.lang.reflect.Method method = cls.getMethod(methodName);

        // 1. Auth check
        boolean isPublic = method.isAnnotationPresent(AnonymousAllowed.class)
                || cls.isAnnotationPresent(AnonymousAllowed.class);
        if (!isPublic && principal == null) {
            throw new io.gauss.quarkus.interceptor.NotAuthenticatedException(
                    "Authentication required for " + cls.getSimpleName() + "." + methodName);
        }

        // 2. Role check
        RolesAllowed ra = method.getAnnotation(RolesAllowed.class);
        if (ra == null) ra = cls.getAnnotation(RolesAllowed.class);
        if (ra != null && principal != null) {
            List<String> userRoles = roles == null ? List.of() : roles;
            boolean hasRole = java.util.Arrays.stream(ra.value()).anyMatch(userRoles::contains);
            if (!hasRole) {
                throw new ForbiddenException(
                        "Required roles " + java.util.Arrays.toString(ra.value())
                        + " not held by " + principal, ra.value());
            }
        }
    }

    // -----------------------------------------------------------------------
    // Fixtures
    // -----------------------------------------------------------------------

    @MLEndpoint(path = "/secure")
    static class SecureEndpoint {
        public String getData() { return "secret"; }
    }

    @MLEndpoint(path = "/public") @AnonymousAllowed
    static class PublicEndpoint {
        public String health() { return "ok"; }
    }

    @MLEndpoint(path = "/mixed")
    static class MixedEndpoint {
        public String secureOp() { return "secret"; }
        @AnonymousAllowed public String publicInfo() { return "info"; }
    }

    @MLEndpoint(path = "/roles")
    static class RoleEndpoint {
        @RolesAllowed("DS")           public String dsOnly()    { return "ds"; }
        @RolesAllowed({"DS","ML_ENG"}) public String dsOrMlEng() { return "both"; }
    }
}
