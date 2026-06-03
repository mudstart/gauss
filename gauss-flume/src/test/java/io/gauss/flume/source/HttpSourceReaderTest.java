package io.gauss.flume.source;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link HttpSourceReader}.
 *
 * <p>Uses a mocked {@link HttpClient} to avoid real network calls.
 */
class HttpSourceReaderTest {

    // -------------------------------------------------------------------------
    // supports()
    // -------------------------------------------------------------------------

    @Test
    void supports_httpUri_returnsTrue() {
        HttpSourceReader reader = new HttpSourceReader();
        assertThat(reader.supports("http://api.example.com/data")).isTrue();
    }

    @Test
    void supports_httpsUri_returnsTrue() {
        HttpSourceReader reader = new HttpSourceReader();
        assertThat(reader.supports("https://api.example.com/data")).isTrue();
    }

    @Test
    void supports_fileUri_returnsFalse() {
        HttpSourceReader reader = new HttpSourceReader();
        assertThat(reader.supports("file:///data.json")).isFalse();
    }

    @Test
    void supports_nullUri_returnsFalse() {
        HttpSourceReader reader = new HttpSourceReader();
        assertThat(reader.supports(null)).isFalse();
    }

    // -------------------------------------------------------------------------
    // read() — mocked HTTP client
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    @Test
    void read_deserializesSuccessfulResponse() throws Exception {
        byte[] body = "{\"key\":\"value\"}".getBytes();

        HttpResponse<byte[]> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(body);

        HttpClient mockClient = mock(HttpClient.class);
        when(mockClient.send(any(HttpRequest.class),
                             any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        HttpSourceReader reader = new HttpSourceReader(mockClient, new ObjectMapper());

        @SuppressWarnings("rawtypes")
        Map result = (Map) reader.read("http://api.example.com/data", Map.class);

        assertThat(result).containsEntry("key", "value");
    }

    @SuppressWarnings("unchecked")
    @Test
    void read_throwsIOExceptionOnNon2xx() throws Exception {
        HttpResponse<byte[]> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(404);
        when(mockResponse.body()).thenReturn(new byte[0]);

        HttpClient mockClient = mock(HttpClient.class);
        when(mockClient.send(any(HttpRequest.class),
                             any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        HttpSourceReader reader = new HttpSourceReader(mockClient, new ObjectMapper());

        assertThatIOException()
                .isThrownBy(() -> reader.read("http://api.example.com/missing", Map.class))
                .withMessageContaining("404");
    }
}
