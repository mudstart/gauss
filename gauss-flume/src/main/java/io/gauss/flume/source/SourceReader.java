package io.gauss.flume.source;

import java.io.IOException;
import java.lang.reflect.Type;

/**
 * SPI for reading raw data from an external source into a Java object.
 *
 * <p>Implementations are discovered via {@link java.util.ServiceLoader} and may
 * also be registered programmatically through {@link SourceReaderRegistry}.
 *
 * <p>Built-in implementations:
 * <ul>
 *   <li>{@link FileSourceReader}  — {@code file:///path/to/file.json}</li>
 *   <li>{@link HttpSourceReader}  — {@code http://host/path} / {@code https://host/path}</li>
 *   <li>{@link JdbcSourceReader}  — {@code jdbc://datasource/table}</li>
 * </ul>
 */
public interface SourceReader {

    /**
     * Returns {@code true} if this reader can handle the given source URI.
     *
     * @param sourceUri URI declared in {@code @Ingest(source = "...")}
     */
    boolean supports(String sourceUri);

    /**
     * Reads the data at {@code sourceUri} and deserializes it into an object of
     * the given {@code targetType}.
     *
     * <p>The {@code targetType} is the (possibly generic) return type of the
     * {@code @Ingest} method as obtained from {@link java.lang.reflect.Method#getGenericReturnType()}.
     *
     * @param sourceUri  URI of the data source
     * @param targetType target Java type to deserialize into
     * @return deserialized object; never {@code null} unless the source is empty
     * @throws IOException if the source is unreachable or deserialization fails
     */
    Object read(String sourceUri, Type targetType) throws IOException;
}
