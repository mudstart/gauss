package io.gauss.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Verifies that generated TypeScript contract files have not drifted from the
 * checksums committed to version control (Vela module, HU-041).
 *
 * <p>The goal reads SHA-256 hashes stored in {@code .gauss-ts-contracts}
 * (in the project root) and recomputes them from the current {@code .ts} files.
 * If any file differs the build fails with a clear message indicating which
 * field or type changed.
 *
 * <p>Usage in a project POM:
 * <pre>{@code
 * <plugin>
 *   <groupId>io.gauss</groupId>
 *   <artifactId>gauss-maven-plugin</artifactId>
 *   <executions>
 *     <execution>
 *       <id>verify-ts</id>
 *       <goals><goal>verify-ts-contracts</goal></goals>
 *     </execution>
 *   </executions>
 * </plugin>
 * }</pre>
 */
@Mojo(name = "verify-ts-contracts", defaultPhase = LifecyclePhase.VERIFY)
public class VerifyTsContractsMojo extends AbstractMojo {

    /** Directory containing the generated {@code .ts} files to verify. */
    @Parameter(defaultValue = "${project.basedir}/frontend/generated",
               property = "gauss.tsOutputDir")
    private File tsOutputDir;

    /**
     * Path to the checksum reference file.  This file must be committed to
     * version control and updated via {@code dsml:update-ts-contracts}.
     */
    @Parameter(defaultValue = "${project.basedir}/.gauss-ts-contracts",
               property = "gauss.contractsFile")
    private File contractsFile;

    /** If {@code true}, generate/update the contracts file instead of verifying. */
    @Parameter(defaultValue = "false", property = "gauss.updateContracts")
    private boolean updateContracts;

    /** Skip the execution entirely. */
    @Parameter(defaultValue = "false", property = "gauss.skipVerifyTs")
    private boolean skip;

    // -------------------------------------------------------------------------

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Gauss: verify-ts-contracts skipped.");
            return;
        }

        if (!tsOutputDir.exists()) {
            getLog().info("Gauss: TypeScript output dir not found — skipping contract verification.");
            return;
        }

        try {
            if (updateContracts) {
                writeContractsFile();
            } else {
                verifyContractsFile();
            }
        } catch (IOException e) {
            throw new MojoExecutionException("I/O error during TS contract verification", e);
        }
    }

    // -------------------------------------------------------------------------

    private void writeContractsFile() throws IOException {
        Map<String, String> hashes = computeHashes();
        Properties props = new Properties();
        hashes.forEach(props::setProperty);
        try (var out = Files.newOutputStream(contractsFile.toPath())) {
            props.store(out, "Gauss TypeScript contract checksums — DO NOT edit manually");
        }
        getLog().info("Gauss: updated .gauss-ts-contracts with " + hashes.size() + " entries.");
    }

    private void verifyContractsFile() throws IOException, MojoFailureException {
        if (!contractsFile.exists()) {
            getLog().warn("Gauss: .gauss-ts-contracts not found. Run with -Dgauss.updateContracts=true to generate it.");
            return;
        }

        Properties stored = new Properties();
        try (var in = Files.newInputStream(contractsFile.toPath())) {
            stored.load(in);
        }

        Map<String, String> current = computeHashes();
        List<String> violations = new ArrayList<>();

        // Check for changed or removed files
        for (Map.Entry<Object, Object> entry : stored.entrySet()) {
            String file = (String) entry.getKey();
            String expectedHash = (String) entry.getValue();
            String actualHash = current.get(file);
            if (actualHash == null) {
                violations.add("  REMOVED: " + file);
            } else if (!actualHash.equals(expectedHash)) {
                violations.add("  CHANGED: " + file
                        + "\n    expected: " + expectedHash
                        + "\n    actual:   " + actualHash);
            }
        }

        // Check for new files not in the reference
        for (String file : current.keySet()) {
            if (!stored.containsKey(file)) {
                violations.add("  NEW (not in contracts): " + file);
            }
        }

        if (!violations.isEmpty()) {
            String msg = "TypeScript contract violation(s) detected!\n"
                    + String.join("\n", violations)
                    + "\n\nRun with -Dgauss.updateContracts=true to update the reference checksums.";
            throw new MojoFailureException(msg);
        }

        getLog().info("Gauss: all " + current.size() + " TypeScript contract(s) verified OK.");
    }

    // -------------------------------------------------------------------------

    /** Computes SHA-256 hashes for all {@code .ts} files in {@link #tsOutputDir}. */
    Map<String, String> computeHashes() throws IOException {
        Map<String, String> result = new TreeMap<>();
        Path root = tsOutputDir.toPath();
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(p -> p.toString().endsWith(".ts"))
                 .forEach(p -> {
                     try {
                         byte[] content = Files.readAllBytes(p);
                         String hash = sha256Hex(content);
                         // Store path relative to output dir
                         result.put(root.relativize(p).toString().replace('\\', '/'), hash);
                     } catch (IOException e) {
                         throw new RuntimeException(e);
                     }
                 });
        }
        return result;
    }

    static String sha256Hex(byte[] content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
