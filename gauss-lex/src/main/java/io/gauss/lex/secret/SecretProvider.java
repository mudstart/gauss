package io.gauss.lex.secret;

import java.util.Optional;

/**
 * SPI for reading secrets from an external secrets manager (HU-049).
 *
 * <p>Implementations resolve secret names to their plaintext values.
 * The Gauss framework reads the active provider via the configuration property
 * {@code dsml.secrets.provider}: {@code "memory"}, {@code "vault"}, or
 * {@code "k8s"}.
 *
 * <p>All methods must be thread-safe.
 */
public interface SecretProvider {

    /**
     * Returns the value of the named secret, or empty if the secret does not
     * exist or the provider is unavailable.
     *
     * @param secretName the logical name of the secret
     * @return the plaintext secret value, or empty
     */
    Optional<String> get(String secretName);

    /**
     * Returns {@code true} if the backing secrets store can be reached.
     * A provider that returns {@code false} will cause the application to
     * fail at startup with a clear error (per HU-049 criterion 5).
     */
    boolean isAvailable();

    /**
     * Returns the identifier used in configuration
     * (e.g., {@code "vault"}, {@code "k8s"}, {@code "memory"}).
     */
    String providerId();
}
