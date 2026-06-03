package io.gauss.flume.source;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.*;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link JdbcSourceReader}.
 */
@ExtendWith(MockitoExtension.class)
class JdbcSourceReaderTest {

    // -------------------------------------------------------------------------
    // supports()
    // -------------------------------------------------------------------------

    @Test
    void supports_jdbcUri_returnsTrue() {
        JdbcSourceReader reader = new JdbcSourceReader(mock(DataSource.class));
        assertThat(reader.supports("jdbc://ds-main/customers")).isTrue();
    }

    @Test
    void supports_fileUri_returnsFalse() {
        JdbcSourceReader reader = new JdbcSourceReader(mock(DataSource.class));
        assertThat(reader.supports("file:///data.json")).isFalse();
    }

    @Test
    void supports_nullUri_returnsFalse() {
        JdbcSourceReader reader = new JdbcSourceReader(mock(DataSource.class));
        assertThat(reader.supports(null)).isFalse();
    }

    // -------------------------------------------------------------------------
    // parseTable()
    // -------------------------------------------------------------------------

    @Test
    void parseTable_extractsTableFromUri() {
        assertThat(JdbcSourceReader.parseTable("jdbc://ds-main/transactions"))
                .isEqualTo("transactions");
    }

    @Test
    void parseTable_throwsForMissingTable() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> JdbcSourceReader.parseTable("jdbc://ds-main/"))
                .withMessageContaining("jdbc://");
    }

    // -------------------------------------------------------------------------
    // read() — mocked JDBC
    // -------------------------------------------------------------------------

    @Test
    void read_returnsRowsAsListOfMaps(@Mock DataSource dataSource,
                                       @Mock Connection connection,
                                       @Mock Statement statement,
                                       @Mock ResultSet resultSet,
                                       @Mock ResultSetMetaData metaData) throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SELECT * FROM orders")).thenReturn(resultSet);
        when(resultSet.getMetaData()).thenReturn(metaData);
        when(metaData.getColumnCount()).thenReturn(2);
        when(metaData.getColumnLabel(1)).thenReturn("id");
        when(metaData.getColumnLabel(2)).thenReturn("amount");

        // Two rows
        when(resultSet.next()).thenReturn(true, true, false);
        when(resultSet.getObject(1)).thenReturn(1, 2);
        when(resultSet.getObject(2)).thenReturn(100, 200);

        JdbcSourceReader reader = new JdbcSourceReader(dataSource);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result =
                (List<Map<String, Object>>) reader.read("jdbc://ds-main/orders", List.class);

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).containsEntry("id", 1).containsEntry("amount", 100);
        assertThat(result.get(1)).containsEntry("id", 2).containsEntry("amount", 200);
    }

    @Test
    void read_wrapsJdbcExceptionInIOException(@Mock DataSource dataSource) throws Exception {
        when(dataSource.getConnection())
                .thenThrow(new SQLException("connection refused"));

        JdbcSourceReader reader = new JdbcSourceReader(dataSource);

        assertThatIOException()
                .isThrownBy(() -> reader.read("jdbc://ds-main/customers", List.class))
                .withMessageContaining("JDBC read failed")
                .withCauseInstanceOf(SQLException.class);
    }

    // -------------------------------------------------------------------------
    // Construction guards
    // -------------------------------------------------------------------------

    @Test
    void constructor_throwsForNullDataSource() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new JdbcSourceReader(null))
                .withMessageContaining("dataSource");
    }
}
