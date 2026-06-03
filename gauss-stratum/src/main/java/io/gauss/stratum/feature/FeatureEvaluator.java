package io.gauss.stratum.feature;

import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Computes feature values by invoking {@link Feature @Feature}-annotated methods
 * via reflection, resolving dependencies from a provided value supplier (HU-027).
 *
 * <p>The first parameter of every {@code @Feature} method must be a {@code String}
 * that receives the entity ID.  Additional parameters are resolved by invoking
 * the corresponding dependency features (matched by return type).
 *
 * <p>Usage:
 * <pre>{@code
 * FeatureEvaluator evaluator = new FeatureEvaluator();
 * Object value = evaluator.evaluate(
 *         myFeaturesBean, descriptor, "customer-42",
 *         (dep, entityId) -> cachedValue);
 * }</pre>
 */
public final class FeatureEvaluator {

    /**
     * A function that resolves a dependency feature's value for a given entity.
     * Returns {@link Optional#empty()} when the value is not yet available and
     * must be computed.
     */
    @FunctionalInterface
    public interface DependencyResolver {
        Optional<Object> resolve(FeatureDescriptor dependency, String entityId);
    }

    // -------------------------------------------------------------------------

    /**
     * Evaluates {@code descriptor}'s feature for {@code entityId}.
     *
     * @param featureBean        the bean instance that owns the {@code @Feature} method
     * @param descriptor         the feature to evaluate
     * @param entityId           the entity for which to compute the value
     * @param featureClass       pre-scanned {@link FeatureClass} (for dependency resolution)
     * @param dependencyResolver called for each dependency — returns a cached value or empty
     * @return the computed feature value
     * @throws FeatureEvaluationException if the method throws or reflection fails
     */
    public Object evaluate(Object featureBean,
                            FeatureDescriptor descriptor,
                            String entityId,
                            FeatureClass featureClass,
                            DependencyResolver dependencyResolver) {
        try {
            java.lang.reflect.Parameter[] params = descriptor.method().getParameters();
            Object[] args = buildArgs(params, entityId, descriptor, featureClass, dependencyResolver);
            descriptor.method().setAccessible(true);
            return descriptor.method().invoke(featureBean, args);
        } catch (InvocationTargetException ite) {
            Throwable cause = ite.getCause() != null ? ite.getCause() : ite;
            throw new FeatureEvaluationException(descriptor.name(), entityId, cause);
        } catch (Exception e) {
            throw new FeatureEvaluationException(descriptor.name(), entityId, e);
        }
    }

    // -------------------------------------------------------------------------

    private Object[] buildArgs(java.lang.reflect.Parameter[] params,
                                String entityId,
                                FeatureDescriptor descriptor,
                                FeatureClass featureClass,
                                DependencyResolver resolver) {
        Object[] args = new Object[params.length];
        List<FeatureDescriptor> deps = featureClass.dependenciesOf(descriptor);
        int depIndex = 0;

        for (int i = 0; i < params.length; i++) {
            if (i == 0 && params[i].getType() == String.class) {
                args[i] = entityId;  // first String param = entity ID
            } else if (depIndex < deps.size()) {
                FeatureDescriptor dep = deps.get(depIndex++);
                args[i] = resolver.resolve(dep, entityId)
                        .orElseGet(() -> evaluate(
                                null, dep, entityId, featureClass, resolver));
            } else {
                args[i] = null;   // should not happen with a well-formed feature class
            }
        }
        return args;
    }
}
