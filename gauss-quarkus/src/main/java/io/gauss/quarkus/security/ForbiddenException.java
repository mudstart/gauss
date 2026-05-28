package io.gauss.quarkus.security;

/**
 * Thrown by {@link io.gauss.quarkus.interceptor.GaussEndpointInterceptor}
 * when an authenticated caller lacks a required role from {@code @RolesAllowed}.
 */
public class ForbiddenException extends SecurityException {

    private final String[] requiredRoles;

    public ForbiddenException(String message, String[] requiredRoles) {
        super(message);
        this.requiredRoles = requiredRoles.clone();
    }

    public String[] requiredRoles() { return requiredRoles.clone(); }
}
