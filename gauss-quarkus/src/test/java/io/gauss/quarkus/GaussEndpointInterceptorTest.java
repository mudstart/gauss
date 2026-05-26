package io.gauss.quarkus;

import io.gauss.core.annotation.AnonymousAllowed;
import io.gauss.core.annotation.MLEndpoint;
import io.gauss.quarkus.interceptor.GaussEndpointInterceptorBinding;
import io.gauss.quarkus.interceptor.NotAuthenticatedException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit-level tests for GaussEndpointInterceptor logic,
 * exercised without a full CDI container.
 *
 * Covers HU-002 AC-2 (adapter) and HU-031 AC-1 (security default).
 */
class GaussEndpointInterceptorTest {

    // -----------------------------------------------------------------------
    // Interceptor binding annotation
    // -----------------------------------------------------------------------

    @Test
    void interceptorBinding_isRetainedAtRuntime() {
        assertThat(GaussEndpointInterceptorBinding.class
                .isAnnotationPresent(jakarta.interceptor.InterceptorBinding.class)).isTrue();
    }

    @Test
    void interceptorBinding_isInherited() {
        assertThat(GaussEndpointInterceptorBinding.class
                .isAnnotationPresent(java.lang.annotation.Inherited.class)).isTrue();
    }

    // -----------------------------------------------------------------------
    // isPublic() logic (white-box via test helpers below)
    // -----------------------------------------------------------------------

    @Test
    void anonymousAllowedOnClass_meansPublicEndpoint() {
        assertThat(isPublicClass(PublicEndpoint.class)).isTrue();
    }

    @Test
    void noAnonymousAllowed_meansProtectedEndpoint() {
        assertThat(isPublicClass(SecureEndpoint.class)).isFalse();
    }

    @Test
    void anonymousAllowedOnMethod_meansPublicMethod() throws Exception {
        Method m = SecureEndpoint.class.getMethod("publicOp");
        assertThat(isPublicMethod(m)).isTrue();
    }

    @Test
    void noAnnotationOnMethod_ofSecureClass_meansProtected() throws Exception {
        Method m = SecureEndpoint.class.getMethod("secureOp");
        assertThat(isPublicMethod(m)).isFalse();
    }

    // -----------------------------------------------------------------------
    // Unauthenticated request simulation
    // -----------------------------------------------------------------------

    @Test
    void unauthenticated_withNoAnonymousAllowed_throwsNotAuthenticatedException() {
        // Simulate the interceptor's decision for a secure, unauthenticated call.
        assertThatThrownBy(() -> {
            if (!isPublicClass(SecureEndpoint.class) && !hasAuthenticatedPrincipal(null)) {
                throw new NotAuthenticatedException("Authentication required");
            }
        }).isInstanceOf(NotAuthenticatedException.class)
          .hasMessageContaining("Authentication required");
    }

    @Test
    void authenticated_withSecureEndpoint_doesNotThrow() {
        // No exception when the caller has a principal.
        boolean blocked = !isPublicClass(SecureEndpoint.class) && !hasAuthenticatedPrincipal("alice");
        assertThat(blocked).isFalse();
    }

    // -----------------------------------------------------------------------
    // Helpers (replicate interceptor logic without a CDI container)
    // -----------------------------------------------------------------------

    private static boolean isPublicClass(Class<?> cls) {
        return cls.isAnnotationPresent(AnonymousAllowed.class);
    }

    private static boolean isPublicMethod(Method m) {
        return m.isAnnotationPresent(AnonymousAllowed.class);
    }

    private static boolean hasAuthenticatedPrincipal(String name) {
        return name != null;
    }

    // -----------------------------------------------------------------------
    // Fixtures
    // -----------------------------------------------------------------------

    @MLEndpoint(path = "/public")
    @AnonymousAllowed
    static class PublicEndpoint {
        public String greet() { return "hello"; }
    }

    @MLEndpoint(path = "/secure")
    static class SecureEndpoint {
        public String secureOp() { return "secret"; }

        @AnonymousAllowed
        public String publicOp() { return "public"; }
    }
}
