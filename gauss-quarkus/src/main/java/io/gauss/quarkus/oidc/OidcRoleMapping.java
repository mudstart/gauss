package io.gauss.quarkus.oidc;

/**
 * Maps an OIDC provider role (claim value) to a Gauss framework role (HU-032).
 *
 * <p>Example: map Keycloak role {@code "ml-engineers"} to Gauss role
 * {@code "ML_ENGINEER"}.
 *
 * @param oidcRole   the role name as it appears in the provider's JWT token
 * @param gaussRole  the corresponding Gauss framework role
 */
public record OidcRoleMapping(String oidcRole, String gaussRole) {}
