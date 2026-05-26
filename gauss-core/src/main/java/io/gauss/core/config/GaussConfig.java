package io.gauss.core.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;

/**
 * Central, typed configuration for Gauss applications.
 *
 * <p>All properties are loaded from the standard MicroProfile Config sources
 * (application.properties, environment variables, Vault, etc.).
 * Jakarta Bean Validation is applied at startup — missing required properties
 * cause a descriptive startup failure rather than a runtime NPE.
 *
 * <p>In Quarkus, inject with {@code @Inject GaussConfig config}.
 * In Spring Boot, inject with {@code @Autowired GaussConfig config}.
 *
 * <p>Example {@code application.properties}:
 * <pre>
 * dsml.namespace=my-team
 * dsml.auth.provider=jwt
 * dsml.models.base-path=models/
 * dsml.tracking.backend=internal
 * </pre>
 */
public class GaussConfig {

    // ── Namespace ─────────────────────────────────────────────────────────────

    /**
     * Logical namespace that isolates models, experiments, features and pipelines.
     * Required for multi-tenant deployments (HU-057).
     */
    @ConfigProperty(name = "dsml.namespace", defaultValue = "default")
    @NotBlank
    public String namespace;

    // ── Authentication ────────────────────────────────────────────────────────

    /** Authentication provider: {@code jwt}, {@code session}, {@code keycloak}, {@code auth0}. */
    @ConfigProperty(name = "dsml.auth.provider", defaultValue = "jwt")
    @NotBlank
    public String authProvider;

    /** OIDC issuer URL when {@code dsml.auth.provider} is an OIDC provider. */
    @ConfigProperty(name = "dsml.auth.issuer-url")
    public Optional<String> authIssuerUrl;

    // ── Model serving ─────────────────────────────────────────────────────────

    /** Base directory where ONNX model files are resolved from. */
    @ConfigProperty(name = "dsml.models.base-path", defaultValue = "models/")
    @NotBlank
    public String modelsBasePath;

    /** Maximum number of models loaded in memory simultaneously. */
    @ConfigProperty(name = "dsml.models.max-loaded", defaultValue = "10")
    @Positive
    public int modelsMaxLoaded;

    // ── Experiment tracking ───────────────────────────────────────────────────

    /** Tracking backend: {@code internal} (default H2), {@code postgres}, {@code mlflow}. */
    @ConfigProperty(name = "dsml.tracking.backend", defaultValue = "internal")
    @NotBlank
    public String trackingBackend;

    /** MLflow server URL when {@code dsml.tracking.backend=mlflow}. */
    @ConfigProperty(name = "dsml.tracking.url")
    public Optional<String> trackingUrl;

    // ── Feature store ─────────────────────────────────────────────────────────

    /** Feature store backend: {@code caffeine} (in-process), {@code redis}. */
    @ConfigProperty(name = "dsml.features.cache", defaultValue = "caffeine")
    @NotNull
    public String featuresCacheBackend;

    /** Redis URI when {@code dsml.features.cache=redis}. */
    @ConfigProperty(name = "dsml.features.redis-url")
    public Optional<String> featuresRedisUrl;

    // ── Secrets ───────────────────────────────────────────────────────────────

    /** Secrets provider: {@code none}, {@code vault}, {@code kubernetes}. */
    @ConfigProperty(name = "dsml.secrets.provider", defaultValue = "none")
    public String secretsProvider;

    /** HashiCorp Vault URL when {@code dsml.secrets.provider=vault}. */
    @ConfigProperty(name = "dsml.secrets.vault-url")
    public Optional<String> secretsVaultUrl;

    // ── Data retention (GDPR) ─────────────────────────────────────────────────

    /** Retention period for prediction logs. ISO-8601 duration (e.g. {@code "90d"}). */
    @ConfigProperty(name = "dsml.retention.predictions", defaultValue = "90d")
    public String retentionPredictions;

    /** Retention period for materialised features. */
    @ConfigProperty(name = "dsml.retention.features", defaultValue = "365d")
    public String retentionFeatures;

    /** Retention period for experiment artefacts. */
    @ConfigProperty(name = "dsml.retention.experiments", defaultValue = "365d")
    public String retentionExperiments;
}
