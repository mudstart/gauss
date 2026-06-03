package io.gauss.flume.source;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link FileSourceReader}.
 */
class FileSourceReaderTest {

    private final FileSourceReader reader = new FileSourceReader();

    // -------------------------------------------------------------------------
    // supports()
    // -------------------------------------------------------------------------

    @Test
    void supports_fileUri_returnsTrue() {
        assertThat(reader.supports("file:///data/input.json")).isTrue();
    }

    @Test
    void supports_httpUri_returnsFalse() {
        assertThat(reader.supports("http://api.example.com/data")).isFalse();
    }

    @Test
    void supports_nullUri_returnsFalse() {
        assertThat(reader.supports(null)).isFalse();
    }

    // -------------------------------------------------------------------------
    // read()
    // -------------------------------------------------------------------------

    @Test
    void read_deserializesJsonObjectToMap(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("data.json");
        Files.writeString(file, "{\"name\":\"Alice\",\"score\":42}");

        String uri = file.toUri().toString();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) reader.read(uri, Map.class);

        assertThat(result).containsEntry("name", "Alice").containsEntry("score", 42);
    }

    @Test
    void read_deserializesJsonArrayToList(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("list.json");
        Files.writeString(file, "[1,2,3]");

        String uri = file.toUri().toString();
        @SuppressWarnings("unchecked")
        List<Integer> result = (List<Integer>) reader.read(uri, List.class);

        assertThat(result).containsExactly(1, 2, 3);
    }

    @Test
    void read_deserializesJsonIntoPojoViaJackson(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("person.json");
        Files.writeString(file, "{\"name\":\"Bob\",\"age\":30}");

        String uri = file.toUri().toString();
        ObjectMapper mapper = new ObjectMapper();
        FileSourceReader typedReader = new FileSourceReader(mapper);

        Person person = (Person) typedReader.read(uri, Person.class);

        assertThat(person.name).isEqualTo("Bob");
        assertThat(person.age).isEqualTo(30);
    }

    @Test
    void toPath_parsesFileUri() {
        // Just verify the static helper doesn't throw for well-formed URIs
        assertThatCode(() -> FileSourceReader.toPath("file:///tmp/test.json"))
                .doesNotThrowAnyException();
    }

    // -------------------------------------------------------------------------
    // Fixture
    // -------------------------------------------------------------------------

    static class Person {
        public String name;
        public int    age;
    }
}
