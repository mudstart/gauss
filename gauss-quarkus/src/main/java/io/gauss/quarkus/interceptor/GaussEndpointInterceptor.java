package io.gauss.quarkus.interceptor;

import io.gauss.core.annotation.AnonymousAllowed;
import io.gauss.quarkus.security.ForbiddenException;
import jakarta.annotation.Priority;
import jakarta.annotation.security.RolesAllowed;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.SecurityContext;

import java.lang.reflect.Method;

/**
 * CDI interceptor applied to all {@code @MLEndpoint} beans via
 * {@link GaussEndpointInterceptorBinding}.
 *
 * <p>Responsibilities (HU-031):
 * <ol>
 *   <li><b>Authentication</b> — rejects unauthenticated callers (HTTP 401)
 *       unless the method or class carries {@code @AnonymousAllowed}.</li>
 *   <li><b>Authorisation</b> — checks {@code @RolesAllowed} on the method or
 *       class; throws {@link ForbiddenException} (HTTP 403) if the caller
 *       does not hold any of the required roles.</li>
 *   <li><b>Error mapping</b> — wraps unhandled {@link RuntimeException}s in
 *       {@link GaussEndpointException} for RFC-9457 Problem Details rendering.</li>
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
                    "Authentication required for "
                    + ctx.getMethod().getDeclaringClass().getSimpleName()
                    + "." + ctx.getMethod().getName());
        }
        checkRoles(ctx);
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

    /**
     * Enforces {@code @RolesAllowed} on the invoked method (or class fallback).
     * No-op if no {@code @RolesAllowed} annotation is present.
     */
    private void checkRoles(InvocationContext ctx) {
        Method method = ctx.getMethod();
        RolesAllowed ra = method.getAnnotation(RolesAllowed.class);
        if (ra == null) ra = ctx.getTarget().getClass().getAnnotation(RolesAllowed.class);
        if (ra == null) return; // no restriction

        if (securityContext == null || securityContext.getUserPrincipal() == null) {
            throw new ForbiddenException("No authenticated principal to check roles", ra.value());
        }
        for (String role : ra.value()) {
            if (securityContext.isUserInRole(role)) return; // at least one matching role
        }
        throw new ForbiddenException(
                "Required roles " + java.util.Arrays.toString(ra.value())
                + " not held by " + securityContext.getUserPrincipal().getName(),
                ra.value());
    }
}
