package io.gauss.stratum.feature;

import io.gauss.core.annotation.Feature;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;

/**
 * Immutable metadata for a single {@link Feature @Feature}-annotated method (HU-027).
 *
 * @param name          method name (used as the feature identifier)
 * @param description   human-readable description from {@code @Feature.description()}
 * @param ttl           parsed TTL duration from {@code @Feature.ttl()}
 * @param version       feature version from {@code @Feature.version()}
 * @param returnType    the Java type returned by the method
 * @param method        the annotated method (for reflective invocation)
 * @param featureClass  the class that declares this feature
 * @param dependencies  other feature names this feature depends on (resolved at scan time)
 */
public record FeatureDescriptor(
        String            name,
        String            description,
        Duration          ttl,
        int               version,
        Class<?>          returnType,
        Method            method,
        Class<?>          featureClass,
        List<String>      dependencies
) {

    /**
     * Builds a descriptor from a {@link Feature @Feature}-annotated method.
     *
     * @param method        the annotated method
     * @param featureClass  the declaring class
     * @param allPeers      all other feature descriptors on the same class (used for
     *                      dependency detection via return-type matching)
     * @throws IllegalArgumentException if the method lacks {@code @Feature}
     */
    public static FeatureDescriptor from(Method method,
                                          Class<?> featureClass,
                                          List<FeatureDescriptor> allPeers) {
        Feature ann = method.getAnnotation(Feature.class);
        if (ann == null) {
            throw new IllegalArgumentException(
                    method + " is not annotated with @Feature");
        }
        Duration ttl = TtlParser.parse(ann.ttl());

        // Detect dependencies: params beyond the first String (entity ID) whose
        // type matches another @Feature method's return type on the same class.
        List<String> deps = resolveDependencies(method, allPeers);

        return new FeatureDescriptor(
                method.getName(),
                ann.description(),
                ttl,
                ann.version(),
                method.getReturnType(),
                method,
                featureClass,
                List.copyOf(deps));
    }

    // -------------------------------------------------------------------------

    private static List<String> resolveDependencies(Method method,
                                                      List<FeatureDescriptor> peers) {
        java.lang.reflect.Parameter[] params = method.getParameters();
        if (params.length <= 1) return List.of();   // only entity ID param — no deps

        List<String> deps = new java.util.ArrayList<>();
        for (int i = 1; i < params.length; i++) {   // skip first param (entity ID)
            Class<?> paramType = params[i].getType();
            peers.stream()
                    .filter(p -> p.returnType().equals(paramType)
                            && !p.name().equals(method.getName()))
                    .findFirst()
                    .ifPresent(peer -> deps.add(peer.name()));
        }
        return deps;
    }

    /**
     * Returns the cache key used by the feature stores.
     *
     * <p>Format: {@code entityId:featureName:version}
     */
    public String cacheKey(String entityId) {
        return entityId + ":" + name + ":" + version;
    }
}
