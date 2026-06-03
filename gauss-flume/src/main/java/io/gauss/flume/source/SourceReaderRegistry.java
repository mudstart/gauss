package io.gauss.flume.source;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Central registry for {@link SourceReader} instances.
 *
 * <p>On first use, loads all implementations registered via
 * {@link ServiceLoader} ({@code META-INF/services/io.gauss.flume.source.SourceReader}).
 * Additional readers can be registered at runtime via {@link #register(SourceReader)}.
 *
 * <p>Lookup is performed in insertion order; the first reader whose
 * {@link SourceReader#supports(String)} returns {@code true} wins.
 */
public final class SourceReaderRegistry {

    private static final List<SourceReader> READERS = new CopyOnWriteArrayList<>();

    static {
        // Load SPI implementations
        ServiceLoader.load(SourceReader.class).forEach(READERS::add);
        // Always ensure built-ins are present (in case SPI file is missing)
        ensureBuiltIns();
    }

    private SourceReaderRegistry() {}

    /**
     * Registers an additional reader at the <em>front</em> of the lookup list,
     * so it takes precedence over built-ins (useful for tests and custom schemes).
     *
     * @param reader reader to register; must not be {@code null}
     */
    public static void register(SourceReader reader) {
        if (reader == null) throw new IllegalArgumentException("reader must not be null");
        READERS.add(0, reader);
    }

    /**
     * Returns the first reader that {@link SourceReader#supports supports} the
     * given URI, or an empty {@link Optional} if none matches.
     *
     * @param sourceUri URI declared in {@code @Ingest(source = "...")}
     */
    public static Optional<SourceReader> find(String sourceUri) {
        return READERS.stream()
                .filter(r -> r.supports(sourceUri))
                .findFirst();
    }

    /**
     * Returns all currently registered readers (defensive copy).
     */
    public static List<SourceReader> all() {
        return new ArrayList<>(READERS);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private static void ensureBuiltIns() {
        boolean hasFile = READERS.stream().anyMatch(r -> r instanceof FileSourceReader);
        boolean hasHttp = READERS.stream().anyMatch(r -> r instanceof HttpSourceReader);

        if (!hasFile) READERS.add(new FileSourceReader());
        if (!hasHttp) READERS.add(new HttpSourceReader());
        // JdbcSourceReader requires a DataSource — not added automatically
    }
}
