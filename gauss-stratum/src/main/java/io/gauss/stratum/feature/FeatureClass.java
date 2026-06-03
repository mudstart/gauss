package io.gauss.stratum.feature;

import io.gauss.core.annotation.Feature;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Scans a class for {@link Feature @Feature}-annotated methods and builds the
 * dependency graph (HU-027).
 *
 * <p>Usage:
 * <pre>{@code
 * FeatureClass fc = FeatureClass.scan(MyFeatures.class);
 * List<FeatureDescriptor> all      = fc.descriptors();
 * List<FeatureDescriptor> depsOf   = fc.dependenciesOf(someDescriptor);
 * List<FeatureDescriptor> sorted   = fc.topologicalOrder();
 * }</pre>
 */
public final class FeatureClass {

    private final Class<?>                     cls;
    private final Map<String, FeatureDescriptor> byName;

    private FeatureClass(Class<?> cls, List<FeatureDescriptor> descriptors) {
        this.cls    = cls;
        this.byName = descriptors.stream()
                .collect(Collectors.toUnmodifiableMap(FeatureDescriptor::name, Function.identity()));
    }

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    /**
     * Scans {@code cls} for {@code @Feature} methods and returns a {@code FeatureClass}
     * with the full dependency graph resolved.
     *
     * @param cls the class to scan
     * @throws IllegalArgumentException if the class has no {@code @Feature} methods
     */
    public static FeatureClass scan(Class<?> cls) {
        // First pass: collect raw descriptors without dependencies
        List<FeatureDescriptor> partial = new ArrayList<>();
        for (Method m : cls.getMethods()) {
            if (m.isAnnotationPresent(Feature.class) && Modifier.isPublic(m.getModifiers())) {
                partial.add(FeatureDescriptor.from(m, cls, List.of()));  // no peers yet
            }
        }
        // Second pass: rebuild with peer list for dependency resolution
        List<FeatureDescriptor> full = new ArrayList<>();
        for (Method m : cls.getMethods()) {
            if (m.isAnnotationPresent(Feature.class) && Modifier.isPublic(m.getModifiers())) {
                full.add(FeatureDescriptor.from(m, cls, partial));
            }
        }
        return new FeatureClass(cls, full);
    }

    // -------------------------------------------------------------------------
    // Query
    // -------------------------------------------------------------------------

    /** Returns all feature descriptors in declaration order. */
    public List<FeatureDescriptor> descriptors() {
        return List.copyOf(byName.values());
    }

    /** Finds a descriptor by feature name, or empty. */
    public Optional<FeatureDescriptor> find(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    /**
     * Returns the feature descriptors that {@code descriptor} directly depends on
     * (i.e. whose values must be computed first).
     */
    public List<FeatureDescriptor> dependenciesOf(FeatureDescriptor descriptor) {
        return descriptor.dependencies().stream()
                .map(byName::get)
                .filter(d -> d != null)
                .toList();
    }

    /**
     * Returns all features in topological evaluation order — dependencies first.
     * Features with no dependencies come first; features that depend on others come last.
     *
     * @throws IllegalStateException if a cyclic dependency is detected
     */
    public List<FeatureDescriptor> topologicalOrder() {
        List<FeatureDescriptor> result  = new ArrayList<>();
        java.util.Set<String>   visited = new java.util.LinkedHashSet<>();
        java.util.Set<String>   inStack = new java.util.HashSet<>();

        for (FeatureDescriptor d : byName.values()) {
            dfs(d, visited, inStack, result);
        }
        return List.copyOf(result);
    }

    /** The scanned class. */
    public Class<?> featureClass() { return cls; }

    // -------------------------------------------------------------------------

    private void dfs(FeatureDescriptor node,
                     java.util.Set<String> visited,
                     java.util.Set<String> inStack,
                     List<FeatureDescriptor> result) {
        if (visited.contains(node.name())) return;
        if (inStack.contains(node.name())) {
            throw new IllegalStateException(
                    "Cyclic feature dependency detected at '" + node.name() + "'");
        }
        inStack.add(node.name());
        for (FeatureDescriptor dep : dependenciesOf(node)) {
            dfs(dep, visited, inStack, result);
        }
        inStack.remove(node.name());
        visited.add(node.name());
        result.add(node);
    }
}
