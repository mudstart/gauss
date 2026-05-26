package io.gauss.cli.command;

import io.gauss.cli.template.ProjectGenerator;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

/**
 * {@code gauss new <project-name>} — scaffolds a new Gauss DS/ML project.
 *
 * <pre>
 * gauss new my-model --runtime=quarkus
 * gauss new my-model --runtime=spring --output-dir=/workspace
 * </pre>
 */
@Command(
    name = "new",
    description = "Scaffold a new Gauss DS/ML project.",
    mixinStandardHelpOptions = true
)
public class NewCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Project name (used as artifact ID and directory name).")
    String projectName;

    @Option(
        names = {"--runtime", "-r"},
        description = "JVM runtime: quarkus (default) or spring.",
        defaultValue = "quarkus"
    )
    String runtime;

    @Option(
        names = {"--group", "-g"},
        description = "Maven groupId for the generated project.",
        defaultValue = "com.example"
    )
    String groupId;

    @Option(
        names = {"--output-dir", "-o"},
        description = "Directory where the project folder will be created. Defaults to current directory.",
        defaultValue = "."
    )
    String outputDir;

    @Override
    public Integer call() {
        if (!runtime.equals("quarkus") && !runtime.equals("spring")) {
            System.err.println("[gauss] Unknown runtime '" + runtime + "'. Use quarkus or spring.");
            return 1;
        }

        Path target = Paths.get(outputDir).resolve(projectName).toAbsolutePath();

        System.out.println("[gauss] Creating project '" + projectName + "' with runtime=" + runtime);
        System.out.println("[gauss] Output: " + target);

        try {
            ProjectGenerator generator = new ProjectGenerator(projectName, groupId, runtime, target);
            generator.generate();
        } catch (IOException e) {
            System.err.println("[gauss] Failed to generate project: " + e.getMessage());
            return 2;
        }

        printSuccessMessage(target);
        return 0;
    }

    private void printSuccessMessage(Path target) {
        System.out.println();
        System.out.println("✓ Project '" + projectName + "' created successfully!");
        System.out.println();
        System.out.println("  Next steps:");
        System.out.println();
        System.out.println("  1. Install the Gauss framework locally (once):");
        System.out.println("       cd <gauss-repo> && mvn install -DskipTests");
        System.out.println();
        System.out.println("  2. Go to your new project:");
        System.out.println("       cd " + target);
        System.out.println();

        if ("quarkus".equals(runtime)) {
            System.out.println("  3. Start in dev mode (live reload):");
            System.out.println("       mvn quarkus:dev");
        } else {
            System.out.println("  3. Start in dev mode:");
            System.out.println("       mvn spring-boot:run");
        }

        System.out.println();
        System.out.println("  4. Build a production JAR:");
        System.out.println("       mvn package");
        System.out.println("       java -jar target/" + projectName + "-1.0.0-SNAPSHOT-runner.jar");
        System.out.println();
        System.out.println("  See README.md for full documentation.");
        System.out.println();
    }
}
