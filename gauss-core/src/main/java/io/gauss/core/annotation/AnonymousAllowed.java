package io.gauss.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an {@link MLEndpoint} class or a single method as publicly accessible
 * without authentication.
 *
 * <p>By default, all Gauss endpoints require a valid JWT or session token.
 * Annotate with {@code @AnonymousAllowed} only for genuinely public resources
 * (e.g., health probes, public model demos).
 *
 * <pre>{@code
 * @MLEndpoint
 * @AnonymousAllowed          // whole class is public
 * public class PublicDemoService { ... }
 *
 * @MLEndpoint
 * public class MixedService {
 *
 *     @AnonymousAllowed      // only this method is public
 *     public ModelInfo info() { ... }
 *
 *     public Result predict(Input in) { ... }  // still requires auth
 * }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface AnonymousAllowed {
}
