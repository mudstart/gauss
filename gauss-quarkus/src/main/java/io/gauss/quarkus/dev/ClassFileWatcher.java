package io.gauss.quarkus.dev;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Watches a directory for {@code .class} file changes and notifies a callback.
 *
 * <p>Runs on a dedicated daemon thread. Call {@link #close()} to stop watching.
 * Used by {@link VelaDevService} to trigger TypeScript regeneration in dev mode.
 */
public final class ClassFileWatcher implements Closeable {

    private static final Logger LOG = Logger.getLogger(ClassFileWatcher.class.getName());

    private final WatchService watchService;
    private final Thread thread;
    private volatile boolean running = true;

    /**
     * @param watchDir  directory to watch (typically {@code target/classes})
     * @param onChange  called with the changed {@code .class} {@link Path} on each event
     */
    public ClassFileWatcher(Path watchDir, Consumer<Path> onChange) throws IOException {
        this.watchService = FileSystems.getDefault().newWatchService();
        watchDir.register(watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY);

        this.thread = new Thread(() -> {
            while (running) {
                WatchKey key;
                try {
                    key = watchService.take(); // blocks until an event arrives
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (ClosedWatchServiceException e) {
                    break;
                }

                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) continue;
                    @SuppressWarnings("unchecked")
                    Path changed = watchDir.resolve(((WatchEvent<Path>) event).context());
                    if (changed.toString().endsWith(".class")) {
                        LOG.fine("Class file changed: " + changed);
                        onChange.accept(changed);
                    }
                }

                if (!key.reset()) {
                    LOG.warning("Watch key no longer valid, stopping watcher.");
                    break;
                }
            }
        }, "gauss-class-watcher");
        this.thread.setDaemon(true);
        this.thread.start();
    }

    public boolean isRunning() { return running; }

    @Override
    public void close() throws IOException {
        running = false;
        watchService.close();
        thread.interrupt();
    }
}
