package io.gauss.quarkus.oidc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link OidcProviderRegistry} and {@link OidcProviderDescriptor}.
 * Covers HU-032 acceptance criteria.
 */
class OidcProviderRegistryTest {

    private OidcProviderRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new OidcProviderRegistry();
    }

    // -------------------------------------------------------------------------
    // fromProperties factory
    // -------------------------------------------------------------------------

    @Test
    void fromProperties_keycloak_parsesType() {
        OidcProviderDescriptor d = OidcProviderRegistry.fromProperties(Map.of(
                "dsml.auth.provider",   "keycloak",
                "dsml.auth.issuer-url", "https://sso.acme.com/realms/main"));
        assertThat(d.type()).isEqualTo(OidcProviderType.KEYCLOAK);
    }

    @Test
    void fromProperties_auth0_parsesType() {
        OidcProviderDescriptor d = OidcProviderRegistry.fromProperties(Map.of(
                "dsml.auth.provider",   "auth0",
                "dsml.auth.issuer-url", "https://myapp.auth0.com/"));
        assertThat(d.type()).isEqualTo(OidcProviderType.AUTH0);
    }

    @Test
    void fromProperties_google_parsesType() {
        OidcProviderDescriptor d = OidcProviderRegistry.fromProperties(Map.of(
                "dsml.auth.provider",   "google",
                "dsml.auth.issuer-url", "https://accounts.google.com"));
        assertThat(d.type()).isEqualTo(OidcProviderType.GOOGLE);
    }

    @Test
    void fromProperties_unknownProvider_becomesCustom() {
        OidcProviderDescriptor d = OidcProviderRegistry.fromProperties(Map.of(
                "dsml.auth.provider",   "my-sso",
                "dsml.auth.issuer-url", "https://sso.internal.com"));
        assertThat(d.type()).isEqualTo(OidcProviderType.CUSTOM);
    }

    @Test
    void fromProperties_storesIssuerUrl() {
        OidcProviderDescriptor d = OidcProviderRegistry.fromProperties(Map.of(
                "dsml.auth.provider",   "keycloak",
                "dsml.auth.issuer-url", "https://sso.acme.com/realms/gauss"));
        assertThat(d.issuerUrl()).isEqualTo("https://sso.acme.com/realms/gauss");
    }

    @Test
    void fromProperties_storesClientId() {
        OidcProviderDescriptor d = OidcProviderRegistry.fromProperties(Map.of(
                "dsml.auth.provider",   "keycloak",
                "dsml.auth.issuer-url", "https://sso.acme.com",
                "dsml.auth.client-id",  "gauss-app"));
        assertThat(d.clientId()).isEqualTo("gauss-app");
    }

    @Test
    void fromProperties_parsesScopesFromCsv() {
        OidcProviderDescriptor d = OidcProviderRegistry.fromProperties(Map.of(
                "dsml.auth.provider",   "keycloak",
                "dsml.auth.issuer-url", "https://sso.acme.com",
                "dsml.auth.scopes",     "openid, email, profile"));
        assertThat(d.scopes()).containsExactlyInAnyOrder("openid", "email", "profile");
    }

    @Test
    void fromProperties_defaultScopes_whenNotSpecified() {
        OidcProviderDescriptor d = OidcProviderRegistry.fromProperties(Map.of(
                "dsml.auth.provider",   "keycloak",
                "dsml.auth.issuer-url", "https://sso.acme.com"));
        assertThat(d.scopes()).contains("openid", "email");
    }

    @Test
    void fromProperties_missingProvider_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> OidcProviderRegistry.fromProperties(Map.of(
                        "dsml.auth.issuer-url", "https://sso.acme.com")));
    }

    @Test
    void fromProperties_missingIssuerUrl_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> OidcProviderRegistry.fromProperties(Map.of(
                        "dsml.auth.provider", "keycloak")));
    }

    // -------------------------------------------------------------------------
    // OidcProviderDescriptor
    // -------------------------------------------------------------------------

    @Test
    void discoveryUrl_appendsWellKnownPath() {
        OidcProviderDescriptor d = new OidcProviderDescriptor(
                OidcProviderType.KEYCLOAK,
                "https://sso.acme.com/realms/main",
                "app", List.of("openid"), List.of());
        assertThat(d.discoveryUrl())
                .isEqualTo("https://sso.acme.com/realms/main/.well-known/openid-configuration");
    }

    @Test
    void discoveryUrl_stripsTrailingSlash() {
        OidcProviderDescriptor d = new OidcProviderDescriptor(
                OidcProviderType.KEYCLOAK,
                "https://sso.acme.com/realms/main/",
                "app", List.of("openid"), List.of());
        assertThat(d.discoveryUrl())
                .isEqualTo("https://sso.acme.com/realms/main/.well-known/openid-configuration");
    }

    @Test
    void mapRole_usesMappingWhenPresent() {
        OidcRoleMapping mapping = new OidcRoleMapping("ml-engineers", "ML_ENGINEER");
        OidcProviderDescriptor d = new OidcProviderDescriptor(
                OidcProviderType.KEYCLOAK, "https://sso.acme.com", "app",
                List.of("openid"), List.of(mapping));
        assertThat(d.mapRole("ml-engineers")).isEqualTo("ML_ENGINEER");
    }

    @Test
    void mapRole_passThroughWhenNoMapping() {
        OidcProviderDescriptor d = new OidcProviderDescriptor(
                OidcProviderType.KEYCLOAK, "https://sso.acme.com", "app",
                List.of("openid"), List.of());
        assertThat(d.mapRole("admin")).isEqualTo("admin");
    }

    @Test
    void mapRole_multipleMappings_selectsCorrectOne() {
        List<OidcRoleMapping> mappings = List.of(
                new OidcRoleMapping("ds-team",  "DATA_SCIENTIST"),
                new OidcRoleMapping("ml-team",  "ML_ENGINEER"));
        OidcProviderDescriptor d = new OidcProviderDescriptor(
                OidcProviderType.KEYCLOAK, "https://sso.acme.com", "app",
                List.of("openid"), mappings);
        assertThat(d.mapRole("ml-team")).isEqualTo("ML_ENGINEER");
        assertThat(d.mapRole("ds-team")).isEqualTo("DATA_SCIENTIST");
    }

    // -------------------------------------------------------------------------
    // OidcProviderRegistry
    // -------------------------------------------------------------------------

    @Test
    void registry_active_emptyWhenNoneRegistered() {
        assertThat(registry.active()).isEmpty();
    }

    @Test
    void registry_active_returnsFirstRegistered() {
        OidcProviderDescriptor d = OidcProviderRegistry.fromProperties(Map.of(
                "dsml.auth.provider",   "keycloak",
                "dsml.auth.issuer-url", "https://sso.acme.com"));
        registry.register(d);
        assertThat(registry.active()).hasValue(d);
    }

    @Test
    void registry_size_incrementsOnRegister() {
        OidcProviderDescriptor d = OidcProviderRegistry.fromProperties(Map.of(
                "dsml.auth.provider",   "keycloak",
                "dsml.auth.issuer-url", "https://sso.acme.com"));
        registry.register(d);
        assertThat(registry.size()).isEqualTo(1);
    }

    @Test
    void registry_all_returnsAllRegistered() {
        registry.register(OidcProviderRegistry.fromProperties(Map.of(
                "dsml.auth.provider", "keycloak", "dsml.auth.issuer-url", "https://a.com")));
        registry.register(OidcProviderRegistry.fromProperties(Map.of(
                "dsml.auth.provider", "auth0", "dsml.auth.issuer-url", "https://b.auth0.com")));
        assertThat(registry.all()).hasSize(2);
    }
}
