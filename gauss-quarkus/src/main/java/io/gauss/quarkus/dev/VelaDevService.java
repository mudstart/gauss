package io.gauss.quarkus.dev;

import io.gauss.core.annotation.MLEndpoint;
import io.gauss.vela.generator.BarrelGenerator;
import io.gauss.vela.generator.ClientFunctionGenerator;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * CDI bean that starts the live-reload pipeline in Quarkus dev mode.
 *
 * <p>On startup it registers a {@link ClassFileWatcher} on the application's
 * class output directory. When a {@code .class} file changes the service:
 * <ol>
 *   <li>Loads the class and checks for {@code @MLEndpoint}.</li>
 *   <li>Re-generates the corresponding TypeScript client file.</li>
 *   <li>Re-writes the {@code index.ts} barrel.</li>
 * </ol>
 * Vite (running with {@code vite --watch}) detects the changed {@code .ts}
 * files and performs hot-module-replacement automatically.
 *
 * <p>This bean is only active in Quarkus dev mode. In production the Quarkus
 * runtime strips beans annotated {@code @io.quarkus.runtime.annotations.RegisterForReflection}
 * from the bean archive automatically; in this service we use a simpler
 * system-property guard for portability.
 */
@ApplicationScoped
public class VelaDevService {

    private static final Logger LOG = Logger.getLogger(VelaDevService.class.getName());

    private final ClientFunctionGenerator clientGen = new ClientFunctionGenerator();
    private final List<String> trackedFileNames = new ArrayList<>();

    private ClassFileWatcher watcher;

    /**
     * CDI startup observer. Activate only when the {@code gauss.dev} system
     * property is {@code true} (set automatically by the Quarkus dev mode launcher).
     */
    void onStart(@Observes StartupEvent ev) {
        if (!isDevMode()) {
            LOG.fine("VelaDevService: not in dev mode, skipping.");
            return;
        }

        Path classesDir = resolveClassesDir();
        if (classesDir == null) {
            LOG.warning("VelaDevService: could not locate classes directory, live-reload disabled.");
            return;
        }

        Path outputDir = classesDir.getParent().getParent()
                .resolve("src/main/webapp/generated"); // Quarkus default for frontend assets

        try {
            Files.createDirectories(outputDir);
            watcher = new ClassFileWatcher(classesDir, changed ->
                    handleClassChange(changed, classesDir, outputDir));
            LOG.info("Gauss live-reload active — watching " + classesDir);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "VelaDevService: failed to start watcher", e);
        }
    }

    void onStop(@Observes ShutdownEvent ev) {
        if (watcher != null) {
            try { watcher.close(); } catch (IOException ignored) {}
        }
    }

    // -------------------------------------------------------------------------

    private void handleClassChange(Path classFile, Path classesDir, Path outputDir) {
        String className = toClassName(classFile, classesDir);
        if (className == null) return;

        try {
            Class<?> cls = Thread.currentThread().getContextClassLoader().loadClass(className);
            if (!cls.isAnnotationPresent(MLEndpoint.class)) return;

            String content  = clientGen.generate(cls);
            String fileName = clientGen.fileName(cls);
            Path   outFile  = outputDir.resolve(fileName);

            Files.writeString(outFile, content, StandardCharsets.UTF_8);
            LOG.info("Gauss regenerated: " + fileName);

            if (!trackedFileNames.contains(fileName)) trackedFileNames.add(fileName);
            Files.writeString(outputDir.resolve("index.ts"),
                    BarrelGenerator.INSTANCE.generate(trackedFileNames),
                    StandardCharsets.UTF_8);

        } catch (ClassNotFoundException | IOException e) {
            LOG.log(Level.WARNING, "Failed to regenerate TS for " + className, e);
        }
    }

    private static String toClassName(Path classFile, Path classesDir) {
        String relative = classesDir.relativize(classFile).toString();
        if (!relative.endsWith(".class")) return null;
        return relative.replace('\\', '/').replace('/', '.').replace(".class", "");
    }

    private static boolean isDevMode() {
        return "true".equalsIgnoreCase(System.getProperty("gauss.dev",
                System.getProperty("quarkus.launch.devmode", "false")));
    }

    private static Path resolveClassesDir() {
        try {
            URL location = VelaDevService.class.getProtectionDomain().getCodeSource().getLocation();
            if (location != null) return Path.of(location.toURI());
        } catch (URISyntaxException ignored) {}
        return null;
    }
}
