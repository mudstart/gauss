package io.gauss.quarkus.oidc;

import java.util.List;

/**
 * Immutable configuration snapshot for an OAuth2/OIDC identity provider
 * (HU-032).
 *
 * <p>Built by {@link OidcProviderRegistry} from the properties
 * {@code dsml.auth.provider}, {@code dsml.auth.issuer-url}, etc.
 *
 * @param type           provider type (Keycloak, Auth0, Google, GitHub, Custom)
 * @param issuerUrl      base URL of the OIDC discovery endpoint
 * @param clientId       OAuth2 client identifier registered with the provider
 * @param scopes         requested OAuth2 scopes (e.g., {@code openid}, {@code email})
 * @param roleMappings   mappings from provider-specific roles to Gauss roles
 */
public record OidcProviderDescriptor(
        OidcProviderType   type,
        String             issuerUrl,
        String             clientId,
        List<String>       scopes,
        List<OidcRoleMapping> roleMappings
) {

    public OidcProviderDescriptor {
        scopes       = scopes       == null ? List.of() : List.copyOf(scopes);
        roleMappings = roleMappings == null ? List.of() : List.copyOf(roleMappings);
    }

    /**
     * Returns the OIDC discovery document URL (appends
     * {@code /.well-known/openid-configuration} to the issuer URL).
     */
    public String discoveryUrl() {
        String base = issuerUrl.endsWith("/")
                ? issuerUrl.substring(0, issuerUrl.length() - 1)
                : issuerUrl;
        return base + "/.well-known/openid-configuration";
    }

    /**
     * Maps an OIDC role to the corresponding Gauss framework role.
     * Returns the OIDC role unchanged if no explicit mapping is defined
     * (pass-through behaviour).
     */
    public String mapRole(String oidcRole) {
        return roleMappings.stream()
                .filter(m -> m.oidcRole().equals(oidcRole))
                .map(OidcRoleMapping::gaussRole)
                .findFirst()
                .orElse(oidcRole);
    }
}
