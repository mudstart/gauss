package io.gauss.quarkus.interceptor;

/** Thrown by {@link GaussEndpointInterceptor} when no authenticated principal is present. */
public class NotAuthenticatedException extends SecurityException {
    public NotAuthenticatedException(String message) {
        super(message);
    }
}
