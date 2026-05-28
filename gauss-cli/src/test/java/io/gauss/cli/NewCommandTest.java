package io.gauss.cli;

import io.gauss.cli.template.ProjectGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HU-001 acceptance criteria tests.
 */
class NewCommandTest {

    @TempDir
    Path tmp;

    @Test
    void quarkusProject_hasRequiredDirectories() throws IOException {
        Path projectRoot = tmp.resolve("my-model");
        new ProjectGenerator("my-model", "com.example", "quarkus", projectRoot).generate();

        // AC: structure includes src/, frontend/, pipelines/, models/
        assertThat(projectRoot.resolve("src/main/java")).isDirectory();
        assertThat(projectRoot.resolve("frontend")).isDirectory();
        assertThat(projectRoot.resolve("pipelines")).isDirectory();
        assertThat(projectRoot.resolve("models")).isDirectory();
    }

    @Test
    void quarkusProject_hasPomXml() throws IOException {
        Path projectRoot = tmp.resolve("churn-model");
        new ProjectGenerator("churn-model", "com.acme", "quarkus", projectRoot).generate();

        Path pom = projectRoot.resolve("pom.xml");
        assertThat(pom).isRegularFile();

        String content = Files.readString(pom);
        // AC: generated project uses the Quarkus BOM
        assertThat(content).contains("quarkus-bom");
        assertThat(content).contains("io.gauss");
        assertThat(content).contains("gauss-core");
        assertThat(content).contains("<artifactId>churn-model</artifactId>");
        assertThat(content).contains("<groupId>com.acme</groupId>");
    }

    @Test
    void springProject_hasPomXmlWithSpringParent() throws IOException {
        Path projectRoot = tmp.resolve("spring-model");
        new ProjectGenerator("spring-model", "com.example", "spring", projectRoot).generate();

        String pom = Files.readString(projectRoot.resolve("pom.xml"));
        // AC: --runtime=spring generates a Spring Boot project
        assertThat(pom).contains("spring-boot-starter-parent");
        assertThat(pom).contains("gauss-core");
    }

    @Test
    void generatedProject_hasReadme() throws IOException {
        Path projectRoot = tmp.resolve("readme-test");
        new ProjectGenerator("readme-test", "com.example", "quarkus", projectRoot).generate();

        // AC: README.md with startup instructions
        Path readme = projectRoot.resolve("README.md");
        assertThat(readme).isRegularFile();

        String content = Files.readString(readme);
        assertThat(content).contains("mvn package");
        assertThat(content).contains("mvn quarkus:dev");
        assertThat(content).contains("Prerequisites");
    }

    @Test
    void generatedProject_hasExamplesForEachModule() throws IOException {
        Path projectRoot = tmp.resolve("full-example");
        new ProjectGenerator("full-example", "com.example", "quarkus", projectRoot).generate();

        Path javaSrc = projectRoot.resolve("src/main/java/com/example/fullexample");

        // AC: examples commented for each module
        assertThat(javaSrc.resolve("GreetingEndpoint.java")).isRegularFile();          // @MLEndpoint
        assertThat(javaSrc.resolve("pipeline/ChurnPipeline.java")).isRegularFile();    // @DataPipeline
        assertThat(javaSrc.resolve("features/CustomerFeatures.java")).isRegularFile(); // @Feature
        assertThat(javaSrc.resolve("training/ChurnExperiment.java")).isRegularFile();  // @Experiment
    }

    @Test
    void generatedProject_hasFrontendScaffold() throws IOException {
        Path projectRoot = tmp.resolve("fe-test");
        new ProjectGenerator("fe-test", "com.example", "quarkus", projectRoot).generate();

        assertThat(projectRoot.resolve("frontend/package.json")).isRegularFile();
        assertThat(projectRoot.resolve("frontend/vite.config.ts")).isRegularFile();
        assertThat(projectRoot.resolve("frontend/tsconfig.json")).isRegularFile();
        assertThat(projectRoot.resolve("frontend/src/generated")).isDirectory();
    }

    @Test
    void generatedProject_pomReferencesGaussCore() throws IOException {
        Path projectRoot = tmp.resolve("dep-test");
        new ProjectGenerator("dep-test", "io.myorg", "quarkus", projectRoot).generate();

        String pom = Files.readString(projectRoot.resolve("pom.xml"));
        assertThat(pom)
            .contains("<groupId>io.gauss</groupId>")
            .contains("<artifactId>gauss-core</artifactId>")
            .contains("<version>0.1.0-SNAPSHOT</version>");
    }

    // -----------------------------------------------------------------------
    // HU-005: Fat JAR with embedded frontend
    // -----------------------------------------------------------------------

    @Test
    void quarkusPom_hasExecPluginForNpmBuild() throws IOException {
        Path projectRoot = tmp.resolve("fatjar-test");
        new ProjectGenerator("fatjar-test", "com.example", "quarkus", projectRoot).generate();

        String pom = Files.readString(projectRoot.resolve("pom.xml"));
        // AC: Vite is compiled during generate-resources phase
        assertThat(pom).contains("exec-maven-plugin");
        assertThat(pom).contains("npm-install");
        assertThat(pom).contains("npm-build");
        assertThat(pom).contains("generate-resources");
    }

    @Test
    void quarkusPom_copiesFrontendDistToMetaInfResources() throws IOException {
        Path projectRoot = tmp.resolve("static-test");
        new ProjectGenerator("static-test", "com.example", "quarkus", projectRoot).generate();

        String pom = Files.readString(projectRoot.resolve("pom.xml"));
        // AC: frontend/dist → META-INF/resources (Quarkus static file serving)
        assertThat(pom).contains("META-INF/resources");
        assertThat(pom).contains("copy-frontend");
        assertThat(pom).contains("frontend/dist");
    }

    @Test
    void quarkusPom_hasOsAwareNpmProfile() throws IOException {
        Path projectRoot = tmp.resolve("npm-profile-test");
        new ProjectGenerator("npm-profile-test", "com.example", "quarkus", projectRoot).generate();

        String pom = Files.readString(projectRoot.resolve("pom.xml"));
        // AC: Windows / Unix profiles for npm.cmd vs npm
        assertThat(pom).contains("npm.cmd");
        assertThat(pom).contains("<family>windows</family>");
    }
}
