package io.gauss.quarkus.oidc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds and stores {@link OidcProviderDescriptor}s from application
 * configuration properties (HU-032).
 *
 * <p>Typical setup via properties:
 * <pre>
 *   dsml.auth.provider   = keycloak
 *   dsml.auth.issuer-url = https://sso.company.com/realms/gauss
 *   dsml.auth.client-id  = gauss-app
 *   dsml.auth.scopes     = openid,email,profile
 * </pre>
 *
 * <p>Usage:
 * <pre>{@code
 * OidcProviderRegistry registry = new OidcProviderRegistry();
 * registry.register(OidcProviderRegistry.fromProperties(Map.of(
 *         "dsml.auth.provider",   "keycloak",
 *         "dsml.auth.issuer-url", "https://sso.acme.com/realms/main",
 *         "dsml.auth.client-id",  "gauss")));
 *
 * OidcProviderDescriptor desc = registry.active().orElseThrow();
 * }</pre>
 */
public final class OidcProviderRegistry {

    private final List<OidcProviderDescriptor> descriptors = new ArrayList<>();

    // -------------------------------------------------------------------------

    /**
     * Registers a provider descriptor.
     *
     * @param descriptor the descriptor to add
     */
    public void register(OidcProviderDescriptor descriptor) {
        descriptors.add(descriptor);
    }

    /**
     * Returns the first registered descriptor, or empty if none has been
     * registered.  In a single-tenant deployment there is typically one
     * active provider.
     */
    public Optional<OidcProviderDescriptor> active() {
        return descriptors.isEmpty() ? Optional.empty()
                : Optional.of(descriptors.get(0));
    }

    /** Returns all registered descriptors. */
    public List<OidcProviderDescriptor> all() {
        return List.copyOf(descriptors);
    }

    /** Number of registered providers. */
    public int size() {
        return descriptors.size();
    }

    // -------------------------------------------------------------------------
    // Factory — build from flat properties map
    // -------------------------------------------------------------------------

    /**
     * Builds an {@link OidcProviderDescriptor} from a flat properties map
     * using the {@code dsml.auth.*} key convention.
     *
     * <p>Supported keys:
     * <ul>
     *   <li>{@code dsml.auth.provider}   — provider type (case-insensitive)</li>
     *   <li>{@code dsml.auth.issuer-url} — OIDC issuer URL</li>
     *   <li>{@code dsml.auth.client-id}  — OAuth2 client ID</li>
     *   <li>{@code dsml.auth.scopes}     — comma-separated scope list</li>
     * </ul>
     *
     * @param properties the application property map
     * @return parsed descriptor
     * @throws IllegalArgumentException if required properties are missing
     */
    public static OidcProviderDescriptor fromProperties(Map<String, String> properties) {
        String providerStr = require(properties, "dsml.auth.provider");
        String issuerUrl   = require(properties, "dsml.auth.issuer-url");
        String clientId    = properties.getOrDefault("dsml.auth.client-id", "");

        OidcProviderType type;
        try {
            type = OidcProviderType.valueOf(providerStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            type = OidcProviderType.CUSTOM;
        }

        List<String> scopes = new ArrayList<>();
        String scopesStr = properties.get("dsml.auth.scopes");
        if (scopesStr != null && !scopesStr.isBlank()) {
            for (String s : scopesStr.split(",")) {
                String trimmed = s.trim();
                if (!trimmed.isEmpty()) scopes.add(trimmed);
            }
        }
        if (scopes.isEmpty()) {
            scopes.add("openid");
            scopes.add("email");
        }

        return new OidcProviderDescriptor(type, issuerUrl, clientId,
                scopes, List.of());
    }

    private static String require(Map<String, String> props, String key) {
        String v = props.get(key);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Missing required property: " + key);
        }
        return v;
    }
}
