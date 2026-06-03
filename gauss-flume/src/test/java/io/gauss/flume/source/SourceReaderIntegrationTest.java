package io.gauss.flume.source;

import io.gauss.core.annotation.DataPipeline;
import io.gauss.core.annotation.Ingest;
import io.gauss.core.annotation.Transform;
import io.gauss.flume.runner.PipelineExecutionException;
import io.gauss.flume.runner.PipelineRunner;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests verifying that {@link PipelineRunner} delegates to a
 * registered {@link SourceReader} for {@code @Ingest} steps.
 */
class SourceReaderIntegrationTest {

    // -------------------------------------------------------------------------
    // Fixture pipeline that relies on a SourceReader (method body returns null)
    // -------------------------------------------------------------------------

    @DataPipeline("reader-driven")
    static class ReaderDrivenPipeline {
        @Ingest(source = "custom://test/payload")
        public Map<String, Object> loadData() {
            return null;  // framework is expected to fill this via SourceReader
        }

        @Transform("upper")
        public String upper(Map<String, Object> data) {
            return data.get("name").toString().toUpperCase();
        }
    }

    // -------------------------------------------------------------------------
    // Stub SourceReader for "custom://" scheme
    // -------------------------------------------------------------------------

    static class StubSourceReader implements SourceReader {
        private final Object payload;

        StubSourceReader(Object payload) { this.payload = payload; }

        @Override public boolean supports(String uri) { return uri != null && uri.startsWith("custom://"); }
        @Override public Object read(String uri, Type t) { return payload; }
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    void runner_usesRegisteredSourceReader() {
        // Arrange: register a stub reader that returns a Map with a name
        SourceReaderRegistry.register(new StubSourceReader(Map.of("name", "gauss")));

        PipelineRunner runner = new PipelineRunner();
        runner.register(ReaderDrivenPipeline.class);

        // Act
        Object result = runner.run("reader-driven");

        // Assert: the transform upper-cased the value from the SourceReader
        assertThat(result).isEqualTo("GAUSS");
    }

    @Test
    void fileSourceReader_supportsJsonFile() throws Exception {
        Path tmp = Files.createTempFile("gauss-test-", ".json");
        try {
            Files.writeString(tmp, "[\"a\",\"b\",\"c\"]");

            FileSourceReader reader = new FileSourceReader();
            @SuppressWarnings("unchecked")
            List<String> result = (List<String>) reader.read(tmp.toUri().toString(), List.class);

            assertThat(result).containsExactly("a", "b", "c");
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void sourceReaderRegistry_findsBuiltinFileReader() {
        assertThat(SourceReaderRegistry.find("file:///data/input.json"))
                .isPresent()
                .get().isInstanceOf(FileSourceReader.class);
    }

    @Test
    void sourceReaderRegistry_findsBuiltinHttpReader() {
        assertThat(SourceReaderRegistry.find("https://api.example.com/data"))
                .isPresent()
                .get().isInstanceOf(HttpSourceReader.class);
    }

    @Test
    void sourceReaderRegistry_emptyForUnknownScheme() {
        assertThat(SourceReaderRegistry.find("ftp://legacy.example.com/data"))
                .isEmpty();
    }
}
