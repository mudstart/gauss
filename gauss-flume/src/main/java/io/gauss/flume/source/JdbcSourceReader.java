package io.gauss.flume.source;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.io.IOException;
import java.lang.reflect.Type;
import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads rows from a JDBC datasource using a {@code jdbc://} source URI.
 *
 * <p>URI format:
 * <pre>
 *   jdbc://&lt;datasource-name&gt;/&lt;table-or-view&gt;
 *   jdbc://ds-main/transactions
 * </pre>
 *
 * <p>Each row is returned as a {@link Map Map&lt;String,Object&gt;} with column
 * names as keys.  When {@code targetType} is a concrete class (not {@code Map}),
 * the row map is round-tripped through Jackson to produce the target type.
 *
 * <p>Unlike {@link FileSourceReader} and {@link HttpSourceReader}, this reader
 * requires an explicit {@link DataSource} supplied at construction time.
 * Register it via {@link SourceReaderRegistry#register(SourceReader)} after
 * configuration:
 * <pre>{@code
 * SourceReaderRegistry.register(new JdbcSourceReader(myDataSource));
 * }</pre>
 */
public class JdbcSourceReader implements SourceReader {

    private final DataSource   dataSource;
    private final ObjectMapper mapper;

    public JdbcSourceReader(DataSource dataSource) {
        this(dataSource, new ObjectMapper());
    }

    public JdbcSourceReader(DataSource dataSource, ObjectMapper mapper) {
        if (dataSource == null) throw new IllegalArgumentException("dataSource must not be null");
        this.dataSource = dataSource;
        this.mapper     = mapper;
    }

    @Override
    public boolean supports(String sourceUri) {
        return sourceUri != null && sourceUri.startsWith("jdbc://");
    }

    /**
     * Reads all rows from the table identified in the URI and returns them as
     * a {@link List} of objects of {@code targetType}.
     *
     * @throws IOException if the query fails or result mapping fails
     */
    @Override
    public Object read(String sourceUri, Type targetType) throws IOException {
        String table = parseTable(sourceUri);

        List<Map<String, Object>> rows = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             Statement  stmt = conn.createStatement();
             ResultSet  rs   = stmt.executeQuery("SELECT * FROM " + table)) {

            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();

            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    row.put(meta.getColumnLabel(i), rs.getObject(i));
                }
                rows.add(row);
            }

        } catch (SQLException e) {
            throw new IOException("JDBC read failed for source: " + sourceUri, e);
        }

        // If target is a raw Map list or Object, return rows as-is
        if (targetType instanceof Class<?> cls && (cls == Object.class || cls == List.class)) {
            return rows;
        }

        // Otherwise round-trip through Jackson to produce the target type
        byte[] json = mapper.writeValueAsBytes(rows);
        return mapper.readValue(json, mapper.getTypeFactory().constructType(targetType));
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Extracts the table name from {@code jdbc://datasource/table}.
     * Strips the scheme and datasource name; returns the path segment.
     */
    static String parseTable(String sourceUri) {
        // jdbc://ds-name/table-name  →  remove "jdbc://", split on first '/'
        String withoutScheme = sourceUri.substring("jdbc://".length()); // "ds-name/table"
        int slash = withoutScheme.indexOf('/');
        if (slash < 0 || slash == withoutScheme.length() - 1) {
            throw new IllegalArgumentException(
                    "Invalid jdbc:// source URI (expected jdbc://datasource/table): " + sourceUri);
        }
        return withoutScheme.substring(slash + 1);
    }
}
