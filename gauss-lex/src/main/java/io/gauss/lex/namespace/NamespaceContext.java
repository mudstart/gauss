package io.gauss.lex.namespace;

/**
 * Thread-local holder for the active namespace (HU-057).
 *
 * <p>The framework sets the namespace at request ingress (e.g., from a JWT
 * claim or {@code dsml.namespace} config property) and clears it after the
 * request completes.  All framework services that support multi-tenancy read
 * the active namespace via {@link #current()}.
 *
 * <p>Usage in a request filter:
 * <pre>{@code
 * NamespaceContext.set("team-alpha");
 * try {
 *     return chain.proceed(request);
 * } finally {
 *     NamespaceContext.clear();
 * }
 * }</pre>
 */
public final class NamespaceContext {

    /** Value used when no namespace has been configured. */
    public static final String DEFAULT = "default";

    private static final ThreadLocal<String> CURRENT =
            ThreadLocal.withInitial(() -> DEFAULT);

    private NamespaceContext() {}

    /** Returns the active namespace for the current thread. */
    public static String current() {
        return CURRENT.get();
    }

    /**
     * Sets the active namespace for the current thread.
     *
     * @param namespace must not be {@code null} or blank
     * @throws IllegalArgumentException if namespace is blank
     */
    public static void set(String namespace) {
        if (namespace == null || namespace.isBlank()) {
            throw new IllegalArgumentException("Namespace must not be blank");
        }
        CURRENT.set(namespace);
    }

    /** Resets the current thread's namespace to {@link #DEFAULT}. */
    public static void clear() {
        CURRENT.set(DEFAULT);
    }

    /**
     * Returns {@code true} if the current thread is operating in the
     * {@link #DEFAULT} namespace (no explicit namespace set).
     */
    public static boolean isDefault() {
        return DEFAULT.equals(CURRENT.get());
    }
}
