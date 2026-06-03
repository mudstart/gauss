package io.gauss.lex.secret;

import java.util.Optional;

/**
 * Selects and delegates to the active {@link SecretProvider} (HU-049).
 *
 * <p>The active provider is chosen at construction time from the configured
 * {@code dsml.secrets.provider} property.  If the provider reports
 * {@link SecretProvider#isAvailable()} as {@code false}, construction fails
 * immediately with an {@link IllegalStateException} — the application will not
 * start with an empty or unavailable secrets backend.
 */
public final class SecretProviderRegistry {

    private final SecretProvider provider;

    /**
     * Creates a registry backed by {@code provider}, validating availability
     * eagerly.
     *
     * @throws IllegalStateException if the provider is not available
     */
    public SecretProviderRegistry(SecretProvider provider) {
        if (!provider.isAvailable()) {
            throw new IllegalStateException(
                    "Secret provider '" + provider.providerId()
                    + "' is not available. Application cannot start without a secrets backend.");
        }
        this.provider = provider;
    }

    /**
     * Returns the value of the named secret, or empty if it is not present.
     *
     * @param secretName logical secret name
     */
    public Optional<String> get(String secretName) {
        return provider.get(secretName);
    }

    /**
     * Returns the value of the named secret, throwing if it is not present.
     * Use this for mandatory secrets that must exist at startup.
     *
     * @throws IllegalStateException if the secret is not found
     */
    public String getRequired(String secretName) {
        return provider.get(secretName).orElseThrow(() ->
                new IllegalStateException("Required secret not found: " + secretName));
    }

    /** Returns the identifier of the active provider. */
    public String activeProvider() {
        return provider.providerId();
    }
}
