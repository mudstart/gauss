package io.gauss.lex.namespace;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry that tracks which resources (models, pipelines, features,
 * experiments) belong to each namespace (HU-057).
 *
 * <p>Resources are identified by a {@code resourceType:resourceId} string
 * (e.g., {@code "model:churn-v2"}, {@code "pipeline:etl-daily"}).
 * The registry enforces namespace isolation: a request running in namespace A
 * will never see resources registered under namespace B.
 *
 * <p>Superadmin access (viewing all namespaces) is provided via
 * {@link #findAllNamespaces()} and {@link #findResourcesInNamespace(String)}.
 *
 * <p>Usage:
 * <pre>{@code
 * registry.register("model:churn-v2", "team-alpha");
 * registry.register("model:risk-v1",  "team-beta");
 *
 * NamespaceContext.set("team-alpha");
 * List<String> visible = registry.visibleResources("model");
 * // → ["model:churn-v2"]  (team-beta resource not visible)
 * }</pre>
 */
public final class NamespaceRegistry {

    // resourceKey → namespace
    private final ConcurrentHashMap<String, String> ownership = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Registration
    // -------------------------------------------------------------------------

    /**
     * Registers {@code resourceKey} under {@code namespace}.
     * If the resource is already registered under a different namespace,
     * the namespace is updated.
     *
     * @param resourceKey  composite key in the form {@code "type:id"}
     * @param namespace    the owning namespace
     */
    public void register(String resourceKey, String namespace) {
        ownership.put(resourceKey, namespace);
    }

    /**
     * Registers {@code resourceKey} under the currently active namespace
     * ({@link NamespaceContext#current()}).
     */
    public void registerInCurrentNamespace(String resourceKey) {
        register(resourceKey, NamespaceContext.current());
    }

    // -------------------------------------------------------------------------
    // Access control
    // -------------------------------------------------------------------------

    /**
     * Returns the namespace that owns {@code resourceKey}, or empty if the
     * resource is not registered.
     */
    public Optional<String> ownerOf(String resourceKey) {
        return Optional.ofNullable(ownership.get(resourceKey));
    }

    /**
     * Returns {@code true} if {@code resourceKey} is visible from
     * {@code namespace}.  A resource is visible if it belongs to the same
     * namespace OR if {@code namespace} equals {@code "superadmin"}.
     */
    public boolean isVisible(String resourceKey, String namespace) {
        if ("superadmin".equals(namespace)) return true;
        String owner = ownership.get(resourceKey);
        return namespace.equals(owner);
    }

    /**
     * Returns {@code true} if {@code resourceKey} is visible from the current
     * thread's active namespace.
     */
    public boolean isVisibleInCurrentNamespace(String resourceKey) {
        return isVisible(resourceKey, NamespaceContext.current());
    }

    // -------------------------------------------------------------------------
    // Filtered queries
    // -------------------------------------------------------------------------

    /**
     * Returns all resource keys of the given {@code resourceType} that are
     * visible in {@code namespace}.
     *
     * @param resourceType prefix before the colon (e.g., {@code "model"})
     * @param namespace    the requesting namespace
     */
    public List<String> visibleResources(String resourceType, String namespace) {
        String prefix = resourceType + ":";
        return ownership.entrySet().stream()
                .filter(e -> e.getKey().startsWith(prefix))
                .filter(e -> isVisible(e.getKey(), namespace))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }

    /**
     * Convenience overload using the active namespace from
     * {@link NamespaceContext#current()}.
     */
    public List<String> visibleResources(String resourceType) {
        return visibleResources(resourceType, NamespaceContext.current());
    }

    // -------------------------------------------------------------------------
    // Superadmin views
    // -------------------------------------------------------------------------

    /** Returns the set of all registered namespaces. */
    public Set<String> findAllNamespaces() {
        return Set.copyOf(ownership.values());
    }

    /** Returns all resource keys registered under {@code namespace}. */
    public List<String> findResourcesInNamespace(String namespace) {
        return ownership.entrySet().stream()
                .filter(e -> namespace.equals(e.getValue()))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }

    /** Total number of registered resources across all namespaces. */
    public int resourceCount() {
        return ownership.size();
    }

    /** Clears all registrations (for tests). */
    public void reset() {
        ownership.clear();
    }
}
