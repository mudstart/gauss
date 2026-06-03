package io.gauss.flume.source;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Reads JSON data from an HTTP or HTTPS endpoint.
 *
 * <p>Supported URI schemes: {@code http://} and {@code https://}.
 *
 * <p>Issues a plain GET request and deserializes the JSON response body into
 * the provided {@code targetType}.  A 2xx status is required; anything else
 * causes an {@link IOException}.
 */
public class HttpSourceReader implements SourceReader {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient  httpClient;
    private final ObjectMapper mapper;

    /** Creates a reader with default {@link HttpClient} and {@link ObjectMapper}. */
    public HttpSourceReader() {
        this(HttpClient.newBuilder()
                     .connectTimeout(DEFAULT_TIMEOUT)
                     .build(),
             new ObjectMapper());
    }

    /** Creates a reader with custom HTTP client and mapper (useful for tests). */
    public HttpSourceReader(HttpClient httpClient, ObjectMapper mapper) {
        this.httpClient = httpClient;
        this.mapper     = mapper;
    }

    @Override
    public boolean supports(String sourceUri) {
        return sourceUri != null &&
               (sourceUri.startsWith("http://") || sourceUri.startsWith("https://"));
    }

    @Override
    public Object read(String sourceUri, Type targetType) throws IOException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(sourceUri))
                .timeout(DEFAULT_TIMEOUT)
                .GET()
                .build();

        HttpResponse<byte[]> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("HTTP request interrupted for: " + sourceUri, e);
        }

        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new IOException(
                    "HTTP " + status + " from source: " + sourceUri);
        }

        return mapper.readValue(response.body(),
                mapper.getTypeFactory().constructType(targetType));
    }
}
