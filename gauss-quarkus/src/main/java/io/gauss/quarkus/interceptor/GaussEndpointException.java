package io.gauss.quarkus.interceptor;

/** Wraps an unhandled exception from a {@code @MLEndpoint} method. */
public class GaussEndpointException extends RuntimeException {
    public GaussEndpointException(String message, Throwable cause) {
        super(message, cause);
    }
}
