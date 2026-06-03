package io.gauss.stratum.catalog;

import io.gauss.stratum.feature.FeatureDescriptor;

import java.time.Duration;
import java.util.List;

/**
 * A read-only entry in the feature catalogue (HU-030).
 *
 * <p>Shown in the Gauss UI: name, type, TTL, description, version, and
 * which other features this one depends on.
 *
 * @param name            feature name (method name)
 * @param description     from {@link io.gauss.core.annotation.Feature#description()}
 * @param returnTypeName  simple name of the Java return type
 * @param ttl             cache TTL duration
 * @param version         feature version
 * @param dependencies    names of directly-depended-upon features
 * @param featureClass    fully-qualified class name that declares this feature
 */
public record FeatureCatalogEntry(
        String       name,
        String       description,
        String       returnTypeName,
        Duration     ttl,
        int          version,
        List<String> dependencies,
        String       featureClass
) {

    public static FeatureCatalogEntry from(FeatureDescriptor desc) {
        return new FeatureCatalogEntry(
                desc.name(),
                desc.description(),
                desc.returnType().getSimpleName(),
                desc.ttl(),
                desc.version(),
                desc.dependencies(),
                desc.featureClass().getName());
    }

    /**
     * Returns {@code true} if this entry's name or description contains the
     * given query string (case-insensitive).
     */
    public boolean matches(String query) {
        String q = query.toLowerCase();
        return name().toLowerCase().contains(q)
                || description().toLowerCase().contains(q)
                || returnTypeName().toLowerCase().contains(q);
    }
}
