package io.gauss.quarkus.endpoint;

import io.gauss.core.annotation.MLEndpoint;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

/**
 * Immutable metadata for a registered {@link MLEndpoint @MLEndpoint} class.
 *
 * @param name          logical endpoint name (from {@code @MLEndpoint} value or class name)
 * @param endpointClass the annotated class
 * @param httpBasePath  HTTP base path (from {@code @MLEndpoint} path or derived from name)
 * @param publicMethods public non-{@code Object} methods exposed as HTTP operations
 */
public record MLEndpointDescriptor(
        String        name,
        Class<?>      endpointClass,
        String        httpBasePath,
        List<Method>  publicMethods
) {

    /**
     * Builds a descriptor from a {@link MLEndpoint @MLEndpoint}-annotated class.
     *
     * @param cls class to describe
     * @throws IllegalArgumentException if {@code cls} lacks {@code @MLEndpoint}
     */
    public static MLEndpointDescriptor from(Class<?> cls) {
        MLEndpoint ann = cls.getAnnotation(MLEndpoint.class);
        if (ann == null) {
            throw new IllegalArgumentException(
                    cls.getName() + " is not annotated with @MLEndpoint");
        }

        String name = ann.value().isBlank() ? cls.getSimpleName() : ann.value();
        String path = ann.path().isBlank()
                ? "/api/" + camelToKebab(name)
                : ann.path();

        List<Method> methods = Arrays.stream(cls.getMethods())
                .filter(m -> m.getDeclaringClass() != Object.class)
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .toList();

        return new MLEndpointDescriptor(name, cls, path, methods);
    }

    /** Returns the number of HTTP operations this endpoint exposes. */
    public int operationCount() { return publicMethods.size(); }

    // -------------------------------------------------------------------------

    private static String camelToKebab(String s) {
        return s.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
    }
}
