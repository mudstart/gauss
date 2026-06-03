package io.gauss.stratum.catalog;

import io.gauss.stratum.feature.FeatureClass;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Query service for the feature catalogue (HU-030).
 *
 * <p>Scans one or more feature classes and exposes the resulting
 * {@link FeatureCatalogEntry} list for display in the Gauss admin UI and
 * for programmatic discovery.
 *
 * <p>Usage:
 * <pre>{@code
 * FeatureCatalogService catalog = new FeatureCatalogService(
 *         ChurnFeatures.class, NlpFeatures.class);
 *
 * List<FeatureCatalogEntry> all     = catalog.listAll();
 * List<FeatureCatalogEntry> found   = catalog.search("tx");
 * }</pre>
 */
public final class FeatureCatalogService {

    private final List<FeatureCatalogEntry> entries;

    /**
     * Creates a catalogue by scanning the given feature classes.
     */
    public FeatureCatalogService(Class<?>... featureClasses) {
        this.entries = Arrays.stream(featureClasses)
                .flatMap(cls -> FeatureClass.scan(cls).descriptors().stream())
                .map(FeatureCatalogEntry::from)
                .collect(Collectors.toUnmodifiableList());
    }

    // -------------------------------------------------------------------------
    // Query API
    // -------------------------------------------------------------------------

    /**
     * Returns all catalogue entries.
     */
    public List<FeatureCatalogEntry> listAll() {
        return entries;
    }

    /**
     * Returns entries whose name, description, or return type contains the
     * given query string (case-insensitive).
     *
     * @param query the search term
     * @return matching entries (never {@code null})
     */
    public List<FeatureCatalogEntry> search(String query) {
        if (query == null || query.isBlank()) return listAll();
        return entries.stream()
                .filter(e -> e.matches(query))
                .toList();
    }

    /**
     * Returns the entry with the given feature name, or empty.
     */
    public java.util.Optional<FeatureCatalogEntry> find(String featureName) {
        return entries.stream().filter(e -> e.name().equals(featureName)).findFirst();
    }

    /**
     * Returns all entries declared by the given feature class (fully-qualified name).
     */
    public List<FeatureCatalogEntry> findByClass(String className) {
        return entries.stream()
                .filter(e -> e.featureClass().equals(className))
                .toList();
    }
}
