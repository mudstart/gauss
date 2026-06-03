package io.gauss.stratum.store;

import io.gauss.stratum.feature.FeatureClass;
import io.gauss.stratum.feature.FeatureDescriptor;
import io.gauss.stratum.feature.FeatureEvaluator;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Offline feature materializer (HU-028).
 *
 * <p>Iterates over a set of entity IDs and pre-computes all feature values
 * declared on the given feature class, storing results in the provided
 * {@link FeatureStore}.  The materialization is <em>incremental</em>: entity/feature
 * combinations already present in the store are skipped.
 *
 * <p>Usage:
 * <pre>{@code
 * OfflineFeatureStore offline = new OfflineFeatureStore(cacheStore, myFeaturesBean);
 * MaterializationResult result = offline.materialize(
 *         "2024-01-01", "2024-12-31",
 *         ChurnFeatures.class,
 *         entityIdRepository.findAll());
 * }</pre>
 */
public final class OfflineFeatureStore {

    private static final Logger LOG = Logger.getLogger(OfflineFeatureStore.class.getName());

    private final FeatureStore     targetStore;
    private final Object           featureBean;
    private final FeatureEvaluator evaluator = new FeatureEvaluator();

    /**
     * @param targetStore the store to write materialized values into
     * @param featureBean the bean whose {@code @Feature} methods are invoked
     */
    public OfflineFeatureStore(FeatureStore targetStore, Object featureBean) {
        this.targetStore = targetStore;
        this.featureBean = featureBean;
    }

    // -------------------------------------------------------------------------
    // Materialization
    // -------------------------------------------------------------------------

    /**
     * Materializes all features in {@code featureClass} for each entity in
     * {@code entityIds}.
     *
     * <p>Already-cached values are skipped (incremental semantics).
     *
     * @param fromDate    ISO-8601 date string, used as metadata in the result
     * @param toDate      ISO-8601 date string, used as metadata in the result
     * @param featureClass the class declaring {@code @Feature} methods
     * @param entityIds   the entities to materialize; may be a large dataset
     * @return statistics about the completed materialization
     */
    public MaterializationResult materialize(String fromDate,
                                              String toDate,
                                              Class<?> featureClass,
                                              Iterable<String> entityIds) {
        Instant start  = Instant.now();
        FeatureClass fc = FeatureClass.scan(featureClass);
        List<FeatureDescriptor> features = fc.topologicalOrder();

        int entities = 0, computed = 0, skipped = 0;

        for (String entityId : entityIds) {
            entities++;
            for (FeatureDescriptor feature : features) {
                if (targetStore.contains(entityId, feature)) {
                    skipped++;
                    continue;
                }
                try {
                    Object value = evaluator.evaluate(
                            featureBean, feature, entityId, fc,
                            (dep, eid) -> targetStore.get(eid, dep));
                    Instant expiresAt = Instant.now().plus(feature.ttl());
                    targetStore.put(entityId, feature, value, expiresAt);
                    computed++;
                } catch (Exception e) {
                    LOG.warning("Stratum: failed to materialize feature '"
                            + feature.name() + "' for entity '" + entityId + "': " + e.getMessage());
                }
            }
        }

        Instant finish = Instant.now();
        MaterializationResult result = new MaterializationResult(
                featureClass, fromDate, toDate, entities, computed, skipped,
                java.time.Duration.between(start, finish), start);

        LOG.info("Stratum: materialization complete — " + entities + " entities, "
                + computed + " computed, " + skipped + " skipped in "
                + result.duration().toMillis() + " ms");
        return result;
    }
}
