package io.gauss.quarkus.interceptor;

import io.gauss.core.annotation.AnonymousAllowed;
import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.SecurityContext;

/**
 * CDI interceptor applied to all {@code @MLEndpoint} beans via
 * {@link GaussEndpointInterceptorBinding}.
 *
 * <p>Responsibilities:
 * <ol>
 *   <li>Enforce authentication — rejects unauthenticated callers unless the
 *       method or class carries {@code @AnonymousAllowed}.</li>
 *   <li>Map unhandled {@link RuntimeException}s to RFC-9457 Problem Details
 *       responses (HTTP 500) so the client always gets a typed error.</li>
 * </ol>
 *
 * <p>Priority 1000 places this interceptor after CDI built-ins but before
 * application-defined interceptors.
 */
@Interceptor
@GaussEndpointInterceptorBinding
@Priority(1000)
public class GaussEndpointInterceptor {

    @Context
    SecurityContext securityContext;

    @AroundInvoke
    public Object intercept(InvocationContext ctx) throws Exception {
        if (!isPublic(ctx) && !isAuthenticated()) {
            throw new NotAuthenticatedException(
                    "Authentication required for " + ctx.getMethod().getDeclaringClass().getSimpleName()
                    + "." + ctx.getMethod().getName());
        }
        try {
            return ctx.proceed();
        } catch (RuntimeException ex) {
            throw new GaussEndpointException("Unhandled error in endpoint", ex);
        }
    }

    // -------------------------------------------------------------------------

    private boolean isPublic(InvocationContext ctx) {
        return ctx.getMethod().isAnnotationPresent(AnonymousAllowed.class)
                || ctx.getTarget().getClass().isAnnotationPresent(AnonymousAllowed.class);
    }

    private boolean isAuthenticated() {
        return securityContext != null && securityContext.getUserPrincipal() != null;
    }
}
