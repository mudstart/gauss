package io.gauss.lex.secret;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link InMemorySecretProvider}, {@link K8sSecretProvider}
 * and {@link SecretProviderRegistry}.
 * Covers HU-049 acceptance criteria.
 */
class SecretProviderTest {

    @TempDir
    Path tempDir;

    // -------------------------------------------------------------------------
    // InMemorySecretProvider
    // -------------------------------------------------------------------------

    @Test
    void inMemory_get_returnsStoredSecret() {
        InMemorySecretProvider p = new InMemorySecretProvider();
        p.set("api-key", "sk-abc123");
        assertThat(p.get("api-key")).hasValue("sk-abc123");
    }

    @Test
    void inMemory_get_returnsEmpty_whenAbsent() {
        InMemorySecretProvider p = new InMemorySecretProvider();
        assertThat(p.get("missing")).isEmpty();
    }

    @Test
    void inMemory_set_overwritesExistingValue() {
        InMemorySecretProvider p = new InMemorySecretProvider();
        p.set("db.pass", "old");
        p.set("db.pass", "new");
        assertThat(p.get("db.pass")).hasValue("new");
    }

    @Test
    void inMemory_remove_deletesSecret() {
        InMemorySecretProvider p = new InMemorySecretProvider();
        p.set("token", "xyz");
        p.remove("token");
        assertThat(p.get("token")).isEmpty();
    }

    @Test
    void inMemory_clear_removesAll() {
        InMemorySecretProvider p = new InMemorySecretProvider();
        p.set("a", "1");
        p.set("b", "2");
        p.clear();
        assertThat(p.get("a")).isEmpty();
    }

    @Test
    void inMemory_isAvailable_alwaysTrue() {
        assertThat(new InMemorySecretProvider().isAvailable()).isTrue();
    }

    @Test
    void inMemory_providerId_isMemory() {
        assertThat(new InMemorySecretProvider().providerId()).isEqualTo("memory");
    }

    @Test
    void inMemory_constructorWithMap_prePopulates() {
        InMemorySecretProvider p = new InMemorySecretProvider(
                Map.of("x", "10", "y", "20"));
        assertThat(p.get("x")).hasValue("10");
        assertThat(p.get("y")).hasValue("20");
    }

    // -------------------------------------------------------------------------
    // K8sSecretProvider
    // -------------------------------------------------------------------------

    @Test
    void k8s_get_readsFileContent() throws Exception {
        Files.writeString(tempDir.resolve("db-password"), "s3cr3t\n");
        K8sSecretProvider p = new K8sSecretProvider(tempDir);
        assertThat(p.get("db-password")).hasValue("s3cr3t");
    }

    @Test
    void k8s_get_stripsTrailingNewline() throws Exception {
        Files.writeString(tempDir.resolve("api-key"), "key-value\n");
        K8sSecretProvider p = new K8sSecretProvider(tempDir);
        assertThat(p.get("api-key")).hasValue("key-value");
    }

    @Test
    void k8s_get_returnsEmpty_whenFileAbsent() {
        K8sSecretProvider p = new K8sSecretProvider(tempDir);
        assertThat(p.get("nonexistent")).isEmpty();
    }

    @Test
    void k8s_isAvailable_trueWhenDirExists() {
        K8sSecretProvider p = new K8sSecretProvider(tempDir);
        assertThat(p.isAvailable()).isTrue();
    }

    @Test
    void k8s_isAvailable_falseWhenDirAbsent() {
        K8sSecretProvider p = new K8sSecretProvider(Path.of("/nonexistent/secrets/dir"));
        assertThat(p.isAvailable()).isFalse();
    }

    @Test
    void k8s_providerId_isK8s() {
        assertThat(new K8sSecretProvider(tempDir).providerId()).isEqualTo("k8s");
    }

    @Test
    void k8s_secretsRoot_isCorrect() {
        K8sSecretProvider p = new K8sSecretProvider(tempDir);
        assertThat(p.secretsRoot()).isEqualTo(tempDir);
    }

    // -------------------------------------------------------------------------
    // SecretProviderRegistry
    // -------------------------------------------------------------------------

    @Test
    void registry_get_delegatesToProvider() {
        InMemorySecretProvider p = new InMemorySecretProvider();
        p.set("key", "value");
        SecretProviderRegistry reg = new SecretProviderRegistry(p);
        assertThat(reg.get("key")).hasValue("value");
    }

    @Test
    void registry_getRequired_returnsValue_whenPresent() {
        InMemorySecretProvider p = new InMemorySecretProvider();
        p.set("required-secret", "abc");
        SecretProviderRegistry reg = new SecretProviderRegistry(p);
        assertThat(reg.getRequired("required-secret")).isEqualTo("abc");
    }

    @Test
    void registry_getRequired_throws_whenAbsent() {
        SecretProviderRegistry reg = new SecretProviderRegistry(new InMemorySecretProvider());
        assertThatIllegalStateException()
                .isThrownBy(() -> reg.getRequired("missing"))
                .withMessageContaining("missing");
    }

    @Test
    void registry_constructor_throws_whenProviderUnavailable() {
        K8sSecretProvider unavailable =
                new K8sSecretProvider(Path.of("/nonexistent/secrets"));
        assertThatIllegalStateException()
                .isThrownBy(() -> new SecretProviderRegistry(unavailable))
                .withMessageContaining("not available");
    }

    @Test
    void registry_activeProvider_returnsProviderId() {
        SecretProviderRegistry reg =
                new SecretProviderRegistry(new InMemorySecretProvider());
        assertThat(reg.activeProvider()).isEqualTo("memory");
    }
}
