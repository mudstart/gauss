package io.gauss.vela.model;

import java.util.List;

/**
 * Represents a scanned method on a {@code @MLEndpoint} class,
 * ready to be rendered as a TypeScript async function.
 */
public record EndpointMethod(
        String name,
        String httpMethod,
        String path,
        List<TsParameter> parameters,
        TsType returnType,
        ReactiveKind reactiveKind
) {
    /** Convenience constructor for non-streaming methods (backward-compatible). */
    public EndpointMethod(String name, String httpMethod, String path,
                          List<TsParameter> parameters, TsType returnType) {
        this(name, httpMethod, path, parameters, returnType, ReactiveKind.NONE);
    }
}
