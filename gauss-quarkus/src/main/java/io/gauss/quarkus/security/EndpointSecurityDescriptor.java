package io.gauss.quarkus.security;

import io.gauss.core.annotation.AnonymousAllowed;
import io.gauss.core.annotation.MLEndpoint;
import jakarta.annotation.security.RolesAllowed;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Describes the security requirements of a single {@code @MLEndpoint} class.
 *
 * <p>Used by the Dev UI (HU-031 AC-5) to display the security level of
 * each endpoint without requiring a live request.
 */
public record EndpointSecurityDescriptor(
        String className,
        String path,
        boolean classAnonymous,
        List<MethodSecurity> methods
) {

    public record MethodSecurity(
            String name,
            boolean anonymous,
            String[] rolesAllowed
    ) {
        public boolean isPublic()     { return anonymous; }
        public boolean hasRoles()     { return rolesAllowed.length > 0; }
    }

    public static EndpointSecurityDescriptor from(Class<?> endpointClass) {
        MLEndpoint ann  = endpointClass.getAnnotation(MLEndpoint.class);
        String path     = ann != null ? (ann.path().isEmpty() ? ann.value() : ann.path()) : "";
        boolean classAnon = endpointClass.isAnnotationPresent(AnonymousAllowed.class);

        List<MethodSecurity> methods = new ArrayList<>();
        for (Method m : endpointClass.getDeclaredMethods()) {
            boolean anon  = m.isAnnotationPresent(AnonymousAllowed.class) || classAnon;
            RolesAllowed ra = m.getAnnotation(RolesAllowed.class);
            if (ra == null) ra = endpointClass.getAnnotation(RolesAllowed.class);
            String[] roles = ra != null ? ra.value() : new String[0];
            methods.add(new MethodSecurity(m.getName(), anon, roles));
        }
        return new EndpointSecurityDescriptor(endpointClass.getSimpleName(), path, classAnon, methods);
    }
}
