package io.gauss.lex.secret;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * {@link SecretProvider} that reads Kubernetes Secrets mounted as files in
 * the standard secrets volume ({@code /var/run/secrets/}) (HU-049).
 *
 * <p>Kubernetes mounts each secret key as a separate file under the configured
 * {@code secretsRoot}.  This provider simply reads the file whose name matches
 * the requested secret name and strips trailing whitespace/newlines.
 *
 * <p>Configuration property: {@code dsml.secrets.provider=k8s}
 *
 * <pre>{@code
 * K8sSecretProvider secrets = new K8sSecretProvider(Path.of("/var/run/secrets"));
 * Optional<String> apiKey = secrets.get("openai-api-key");
 * }</pre>
 */
public final class K8sSecretProvider implements SecretProvider {

    private static final Logger LOG = Logger.getLogger(K8sSecretProvider.class.getName());

    /** Default mount path used by Kubernetes secret volumes. */
    public static final Path DEFAULT_SECRETS_ROOT = Path.of("/var/run/secrets");

    private final Path secretsRoot;

    public K8sSecretProvider() {
        this(DEFAULT_SECRETS_ROOT);
    }

    public K8sSecretProvider(Path secretsRoot) {
        this.secretsRoot = secretsRoot;
    }

    // -------------------------------------------------------------------------

    @Override
    public Optional<String> get(String secretName) {
        Path secretFile = secretsRoot.resolve(secretName);
        if (!Files.exists(secretFile)) {
            LOG.fine(() -> "K8s secret not found: " + secretFile);
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(secretFile).strip());
        } catch (IOException e) {
            LOG.warning(() -> "Failed to read K8s secret " + secretName + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public boolean isAvailable() {
        return Files.isDirectory(secretsRoot);
    }

    @Override
    public String providerId() {
        return "k8s";
    }

    /** Returns the secrets root path configured for this provider. */
    public Path secretsRoot() {
        return secretsRoot;
    }
}
