package io.gauss.quarkus.interceptor;

import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * CDI interceptor binding applied automatically to every {@code @MLEndpoint} class
 * by the Gauss Quarkus adapter.
 *
 * <p>Triggers {@link GaussEndpointInterceptor}, which enforces authentication
 * and maps unhandled exceptions to Problem Details (RFC 9457).
 */
@Inherited
@Documented
@InterceptorBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface GaussEndpointInterceptorBinding {}
