package io.gauss.maven;

import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link VerifyTsContractsMojo}.
 * Covers HU-041 acceptance criteria.
 */
class VerifyTsContractsMojoTest {

    @TempDir
    Path tempDir;

    private Path tsDir;
    private Path contractsFile;

    @BeforeEach
    void setUp() throws Exception {
        tsDir         = tempDir.resolve("frontend/generated");
        contractsFile = tempDir.resolve(".gauss-ts-contracts");
        Files.createDirectories(tsDir);
    }

    // -------------------------------------------------------------------------
    // sha256Hex
    // -------------------------------------------------------------------------

    @Test
    void sha256Hex_deterministicForSameContent() {
        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);
        assertThat(VerifyTsContractsMojo.sha256Hex(content))
                .isEqualTo(VerifyTsContractsMojo.sha256Hex(content));
    }

    @Test
    void sha256Hex_differentForDifferentContent() {
        assertThat(VerifyTsContractsMojo.sha256Hex("hello".getBytes(StandardCharsets.UTF_8)))
                .isNotEqualTo(VerifyTsContractsMojo.sha256Hex("world".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void sha256Hex_is64HexChars() {
        String hash = VerifyTsContractsMojo.sha256Hex("x".getBytes(StandardCharsets.UTF_8));
        assertThat(hash).hasSize(64).matches("[0-9a-f]+");
    }

    // -------------------------------------------------------------------------
    // computeHashes
    // -------------------------------------------------------------------------

    @Test
    void computeHashes_emptyDir_returnsEmptyMap() throws Exception {
        VerifyTsContractsMojo mojo = mojo();
        assertThat(mojo.computeHashes()).isEmpty();
    }

    @Test
    void computeHashes_oneTsFile_returnsOneEntry() throws Exception {
        Files.writeString(tsDir.resolve("Churn.ts"), "export interface Churn {}");
        VerifyTsContractsMojo mojo = mojo();
        assertThat(mojo.computeHashes()).hasSize(1);
        assertThat(mojo.computeHashes()).containsKey("Churn.ts");
    }

    @Test
    void computeHashes_ignoresNonTsFiles() throws Exception {
        Files.writeString(tsDir.resolve("Churn.ts"),  "ts content");
        Files.writeString(tsDir.resolve("readme.md"), "markdown");
        VerifyTsContractsMojo mojo = mojo();
        assertThat(mojo.computeHashes()).hasSize(1);
    }

    @Test
    void computeHashes_subdirectory_includedWithRelativePath() throws Exception {
        Path sub = tsDir.resolve("models");
        Files.createDirectories(sub);
        Files.writeString(sub.resolve("Risk.ts"), "export type Risk = number;");
        VerifyTsContractsMojo mojo = mojo();
        Map<String, String> hashes = mojo.computeHashes();
        assertThat(hashes).containsKey("models/Risk.ts");
    }

    @Test
    void computeHashes_hashChanges_whenContentChanges() throws Exception {
        Path tsFile = tsDir.resolve("Model.ts");
        Files.writeString(tsFile, "export interface Model { id: number }");
        VerifyTsContractsMojo mojo = mojo();
        String hash1 = mojo.computeHashes().get("Model.ts");

        Files.writeString(tsFile, "export interface Model { id: number; name: string }");
        String hash2 = mojo.computeHashes().get("Model.ts");

        assertThat(hash1).isNotEqualTo(hash2);
    }

    // -------------------------------------------------------------------------
    // skip flag
    // -------------------------------------------------------------------------

    @Test
    void execute_skip_doesNotFail() throws Exception {
        VerifyTsContractsMojo mojo = mojoWithSkip(true);
        assertThatNoException().isThrownBy(mojo::execute);
    }

    // -------------------------------------------------------------------------
    // update mode
    // -------------------------------------------------------------------------

    @Test
    void execute_updateContracts_writesContractsFile() throws Exception {
        Files.writeString(tsDir.resolve("Churn.ts"), "export interface Churn {}");
        VerifyTsContractsMojo mojo = mojoWithUpdate(true);
        mojo.execute();
        assertThat(contractsFile).exists();
    }

    @Test
    void execute_updateContracts_fileContainsHashEntry() throws Exception {
        Files.writeString(tsDir.resolve("Churn.ts"), "export interface Churn {}");
        VerifyTsContractsMojo mojo = mojoWithUpdate(true);
        mojo.execute();

        Properties props = new Properties();
        try (var in = Files.newInputStream(contractsFile)) { props.load(in); }
        assertThat(props).containsKey("Churn.ts");
    }

    // -------------------------------------------------------------------------
    // verify mode
    // -------------------------------------------------------------------------

    @Test
    void execute_verify_noContractsFile_doesNotFail() throws Exception {
        // contracts file absent → warning only, not a failure
        VerifyTsContractsMojo mojo = mojo();
        assertThatNoException().isThrownBy(mojo::execute);
    }

    @Test
    void execute_verify_unchangedFiles_passes() throws Exception {
        Files.writeString(tsDir.resolve("Churn.ts"), "unchanged");
        // First: generate contracts
        mojoWithUpdate(true).execute();
        // Then: verify — should pass
        assertThatNoException().isThrownBy(() -> mojo().execute());
    }

    @Test
    void execute_verify_changedFile_throws() throws Exception {
        Files.writeString(tsDir.resolve("Churn.ts"), "original");
        mojoWithUpdate(true).execute();

        // Modify the file after saving contracts
        Files.writeString(tsDir.resolve("Churn.ts"), "modified content");
        assertThatExceptionOfType(MojoFailureException.class)
                .isThrownBy(() -> mojo().execute())
                .withMessageContaining("CHANGED");
    }

    @Test
    void execute_verify_newFile_throws() throws Exception {
        Files.writeString(tsDir.resolve("Old.ts"), "old");
        mojoWithUpdate(true).execute();

        Files.writeString(tsDir.resolve("New.ts"), "new");
        assertThatExceptionOfType(MojoFailureException.class)
                .isThrownBy(() -> mojo().execute())
                .withMessageContaining("NEW");
    }

    @Test
    void execute_verify_removedFile_throws() throws Exception {
        Files.writeString(tsDir.resolve("WillBeRemoved.ts"), "content");
        mojoWithUpdate(true).execute();

        Files.delete(tsDir.resolve("WillBeRemoved.ts"));
        assertThatExceptionOfType(MojoFailureException.class)
                .isThrownBy(() -> mojo().execute())
                .withMessageContaining("REMOVED");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private VerifyTsContractsMojo mojo() throws Exception {
        return mojoWithFlags(false, false);
    }

    private VerifyTsContractsMojo mojoWithUpdate(boolean update) throws Exception {
        return mojoWithFlags(update, false);
    }

    private VerifyTsContractsMojo mojoWithSkip(boolean skip) throws Exception {
        return mojoWithFlags(false, skip);
    }

    private VerifyTsContractsMojo mojoWithFlags(boolean update, boolean skip) throws Exception {
        VerifyTsContractsMojo m = new VerifyTsContractsMojo();
        setField(m, "tsOutputDir",     tsDir.toFile());
        setField(m, "contractsFile",   contractsFile.toFile());
        setField(m, "updateContracts", update);
        setField(m, "skip",            skip);
        return m;
    }

    private static void setField(Object obj, String name, Object value) throws Exception {
        Field f = obj.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(obj, value);
    }
}
