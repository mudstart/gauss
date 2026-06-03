package io.gauss.flume.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gauss.flume.source.SourceReader;

import java.io.IOException;
import java.lang.reflect.Type;

/**
 * A {@link SourceReader} that serves pre-configured in-memory JSON as mock data.
 * Used internally by {@link PipelineTestRunner} and {@link GaussPipelineExtension}.
 */
class MockSourceReader implements SourceReader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String source;
    private final String json;

    MockSourceReader(String source, String json) {
        this.source = source;
        this.json   = json;
    }

    @Override
    public boolean supports(String sourceUri) {
        return source.equals(sourceUri);
    }

    @Override
    public Object read(String sourceUri, Type targetType) throws IOException {
        return MAPPER.readValue(json, MAPPER.getTypeFactory().constructType(targetType));
    }
}
