package io.gauss.quarkus.oidc;

/**
 * Supported OAuth2/OIDC identity providers (HU-032).
 */
public enum OidcProviderType {
    KEYCLOAK,
    AUTH0,
    GOOGLE,
    GITHUB,
    /** Any OIDC-compliant provider not listed above. */
    CUSTOM
}
