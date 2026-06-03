package io.gauss.flume.source;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads JSON data from a local file identified by a {@code file://} URI.
 *
 * <p>Supported URIs:
 * <pre>
 *   file:///absolute/path/to/data.json
 *   file:///C:/windows/path/to/data.json
 * </pre>
 *
 * <p>The file content must be valid JSON that can be deserialized into the
 * {@code targetType} provided by the caller (supports generics via Jackson's
 * {@link com.fasterxml.jackson.databind.type.TypeFactory}).
 */
public class FileSourceReader implements SourceReader {

    private final ObjectMapper mapper;

    /** Creates a reader with a default {@link ObjectMapper}. */
    public FileSourceReader() {
        this(new ObjectMapper());
    }

    /** Creates a reader with a custom {@link ObjectMapper} (useful for tests). */
    public FileSourceReader(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean supports(String sourceUri) {
        return sourceUri != null && sourceUri.startsWith("file://");
    }

    @Override
    public Object read(String sourceUri, Type targetType) throws IOException {
        Path path = toPath(sourceUri);
        byte[] bytes = Files.readAllBytes(path);
        return mapper.readValue(bytes,
                mapper.getTypeFactory().constructType(targetType));
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    static Path toPath(String sourceUri) {
        URI uri = URI.create(sourceUri);
        return Path.of(uri);
    }
}
