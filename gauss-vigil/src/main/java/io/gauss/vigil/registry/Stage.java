package io.gauss.vigil.registry;

/**
 * Lifecycle stage of a registered model (HU-025).
 *
 * <p>Models progress through stages via
 * {@link ModelRegistry#promote(String, Stage)} or
 * {@link ModelRegistry#promote(String, Stage, String)}.
 */
public enum Stage {

    /** Model is under evaluation and not yet serving production traffic. */
    STAGING,

    /**
     * Model is live and serves production traffic.  Endpoints annotated with
     * {@code @MLEndpoint} automatically load the {@code PRODUCTION} version.
     */
    PRODUCTION,

    /** Model has been retired; no longer eligible for promotion. */
    ARCHIVED
}
