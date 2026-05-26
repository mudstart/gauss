package io.gauss.maven;

import io.gauss.vela.generator.BarrelGenerator;
import io.gauss.vela.generator.ClientFunctionGenerator;
import io.gauss.vela.generator.ZodSchemaGenerator;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates TypeScript client functions and Zod schemas from annotated Java classes.
 *
 * <p>Binds to {@code process-classes} so the project's compiled bytecode is available.
 * Supports incremental generation: a file is skipped when the output {@code .ts} is
 * newer than the corresponding {@code .class} file.
 *
 * <p>Usage in a project POM:
 * <pre>{@code
 * <plugin>
 *   <groupId>io.gauss</groupId>
 *   <artifactId>gauss-maven-plugin</artifactId>
 *   <version>0.1.0-SNAPSHOT</version>
 *   <executions>
 *     <execution>
 *       <goals><goal>generate-ts</goal></goals>
 *     </execution>
 *   </executions>
 *   <configuration>
 *     <outputDirectory>${project.basedir}/frontend/generated</outputDirectory>
 *     <endpointClasses>
 *       <endpointClass>com.example.ml.ChurnEndpoint</endpointClass>
 *     </endpointClasses>
 *     <dtoClasses>
 *       <dtoClass>com.example.ml.ChurnInput</dtoClass>
 *       <dtoClass>com.example.ml.ChurnResult</dtoClass>
 *     </dtoClasses>
 *   </configuration>
 * </plugin>
 * }</pre>
 */
@Mojo(
        name = "generate-ts",
        defaultPhase = LifecyclePhase.PROCESS_CLASSES,
        requiresDependencyResolution = ResolutionScope.COMPILE,
        threadSafe = true
)
public class GenerateTsMojo extends AbstractMojo {

    /** Directory containing the project's compiled classes. */
    @Parameter(defaultValue = "${project.build.outputDirectory}", readonly = true)
    File classesDirectory;

    /**
     * Destination directory for generated {@code .ts} files.
     * Can be overridden with {@code -Dgauss.outputDirectory=...}.
     */
    @Parameter(
            defaultValue = "${project.basedir}/frontend/generated",
            property = "gauss.outputDirectory"
    )
    File outputDirectory;

    /** Fully-qualified names of {@code @MLEndpoint} classes to generate client functions for. */
    @Parameter
    List<String> endpointClasses = new ArrayList<>();

    /** Fully-qualified names of DTO classes to generate Zod schemas for. */
    @Parameter
    List<String> dtoClasses = new ArrayList<>();

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    MavenProject project;

    @Override
    public void execute() throws MojoExecutionException {
        if (endpointClasses.isEmpty() && dtoClasses.isEmpty()) {
            getLog().info("gauss:generate-ts — no classes configured, skipping.");
            return;
        }

        outputDirectory.mkdirs();

        try {
            ClassLoader cl = buildClassLoader();
            ClientFunctionGenerator clientGen = new ClientFunctionGenerator();
            List<String> generatedNames = new ArrayList<>();

            for (String className : endpointClasses) {
                Class<?> cls = cl.loadClass(className);
                String fileName = clientGen.fileName(cls);
                File outFile = new File(outputDirectory, fileName);

                if (!needsRegeneration(className, outFile)) {
                    getLog().debug("Up-to-date, skipping: " + fileName);
                    generatedNames.add(fileName);
                    continue;
                }

                writeFile(outFile, clientGen.generate(cls));
                generatedNames.add(fileName);
                getLog().info("Generated: " + fileName);
            }

            for (String className : dtoClasses) {
                Class<?> cls = cl.loadClass(className);
                String fileName = ZodSchemaGenerator.INSTANCE.fileName(cls);
                File outFile = new File(outputDirectory, fileName);

                if (!needsRegeneration(className, outFile)) {
                    getLog().debug("Up-to-date, skipping: " + fileName);
                    generatedNames.add(fileName);
                    continue;
                }

                writeFile(outFile, ZodSchemaGenerator.INSTANCE.generate(cls));
                generatedNames.add(fileName);
                getLog().info("Generated: " + fileName);
            }

            // Always regenerate the barrel to reflect the current file list
            File barrelFile = new File(outputDirectory, "index.ts");
            writeFile(barrelFile, BarrelGenerator.INSTANCE.generate(generatedNames));
            getLog().info("Generated: index.ts");

        } catch (ClassNotFoundException e) {
            throw new MojoExecutionException("Class not found: " + e.getMessage()
                    + " — ensure the class is compiled before running gauss:generate-ts", e);
        } catch (Exception e) {
            throw new MojoExecutionException("TypeScript generation failed: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Package-private for unit testing
    // -------------------------------------------------------------------------

    boolean needsRegeneration(String className, File outputFile) {
        if (!outputFile.exists()) return true;
        String classRelPath = className.replace('.', '/') + ".class";
        File classFile = new File(classesDirectory, classRelPath);
        return !classFile.exists() || classFile.lastModified() > outputFile.lastModified();
    }

    void writeFile(File file, String content) throws MojoExecutionException {
        try {
            Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new MojoExecutionException("Cannot write " + file.getPath(), e);
        }
    }

    private ClassLoader buildClassLoader() throws Exception {
        List<URL> urls = new ArrayList<>();
        urls.add(classesDirectory.toURI().toURL());
        for (String element : project.getCompileClasspathElements()) {
            urls.add(new File(element).toURI().toURL());
        }
        return new URLClassLoader(urls.toArray(new URL[0]), getClass().getClassLoader());
    }
}
