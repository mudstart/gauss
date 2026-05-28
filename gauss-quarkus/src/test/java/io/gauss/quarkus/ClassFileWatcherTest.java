package io.gauss.quarkus;

import io.gauss.quarkus.dev.ClassFileWatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers HU-004: ClassFileWatcher detects .class changes and invokes the callback.
 */
class ClassFileWatcherTest {

    @TempDir
    Path tempDir;

    // -----------------------------------------------------------------------
    // AC-1: .class changes trigger callback within timeout
    // -----------------------------------------------------------------------

    @Test
    void watcher_detectsNewClassFile() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        List<Path> detected = new ArrayList<>();

        try (ClassFileWatcher w = new ClassFileWatcher(tempDir, path -> {
            detected.add(path);
            latch.countDown();
        })) {
            // Create a .class file in the watched directory
            Path classFile = tempDir.resolve("Foo.class");
            Files.writeString(classFile, "dummy");

            boolean fired = latch.await(3, TimeUnit.SECONDS); // AC-1: < 3 seconds
            assertThat(fired).as("Callback must fire within 3 s of .class change").isTrue();
            assertThat(detected).anyMatch(p -> p.getFileName().toString().equals("Foo.class"));
        }
    }

    @Test
    void watcher_ignoresNonClassFiles() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        try (ClassFileWatcher w = new ClassFileWatcher(tempDir, path -> latch.countDown())) {
            Files.writeString(tempDir.resolve("notes.txt"), "ignored");
            // Give the watcher time to potentially (incorrectly) fire
            boolean fired = latch.await(500, TimeUnit.MILLISECONDS);
            assertThat(fired).as(".txt changes must NOT trigger the callback").isFalse();
        }
    }

    @Test
    void watcher_detectsModifiedClassFile() throws Exception {
        Path classFile = tempDir.resolve("Bar.class");
        Files.writeString(classFile, "v1");

        CountDownLatch latch = new CountDownLatch(1);
        try (ClassFileWatcher w = new ClassFileWatcher(tempDir, path -> {
            if (path.getFileName().toString().equals("Bar.class")) latch.countDown();
        })) {
            Thread.sleep(100); // ensure watcher is registered before we modify
            Files.writeString(classFile, "v2");

            boolean fired = latch.await(3, TimeUnit.SECONDS);
            assertThat(fired).as("Modification of existing .class must be detected").isTrue();
        }
    }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    @Test
    void watcher_isRunning_afterStart() throws Exception {
        try (ClassFileWatcher w = new ClassFileWatcher(tempDir, p -> {})) {
            assertThat(w.isRunning()).isTrue();
        }
    }

    @Test
    void watcher_isNotRunning_afterClose() throws Exception {
        ClassFileWatcher w = new ClassFileWatcher(tempDir, p -> {});
        w.close();
        assertThat(w.isRunning()).isFalse();
    }
}
