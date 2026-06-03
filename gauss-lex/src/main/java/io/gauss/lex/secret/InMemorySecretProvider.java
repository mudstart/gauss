package io.gauss.lex.secret;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link SecretProvider} for unit tests and local development (HU-049).
 *
 * <p>Secrets are set programmatically via {@link #set(String, String)}.
 * This provider is always available ({@link #isAvailable()} returns
 * {@code true}).
 *
 * <pre>{@code
 * InMemorySecretProvider secrets = new InMemorySecretProvider();
 * secrets.set("db.password",    "s3cret");
 * secrets.set("openai.api-key", "sk-...");
 *
 * assertThat(secrets.get("db.password")).hasValue("s3cret");
 * }</pre>
 */
public final class InMemorySecretProvider implements SecretProvider {

    private final ConcurrentHashMap<String, String> store = new ConcurrentHashMap<>();

    /** Pre-populates the store with the given secrets. */
    public InMemorySecretProvider(Map<String, String> secrets) {
        store.putAll(secrets);
    }

    public InMemorySecretProvider() {}

    // -------------------------------------------------------------------------

    /**
     * Stores or updates a secret.  Value must not be {@code null}; to remove
     * a secret use {@link #remove(String)}.
     */
    public void set(String secretName, String value) {
        store.put(secretName, value);
    }

    /** Removes a secret. No-op if it does not exist. */
    public void remove(String secretName) {
        store.remove(secretName);
    }

    /** Removes all secrets. */
    public void clear() {
        store.clear();
    }

    @Override
    public Optional<String> get(String secretName) {
        return Optional.ofNullable(store.get(secretName));
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String providerId() {
        return "memory";
    }
}
