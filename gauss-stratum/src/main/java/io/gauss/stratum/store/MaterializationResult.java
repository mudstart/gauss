package io.gauss.stratum.store;

import java.time.Duration;
import java.time.Instant;

/**
 * Statistics from a completed offline feature materialization run (HU-028).
 *
 * @param featureClass     the class whose features were materialized
 * @param fromDate         inclusive start of the date range (ISO-8601 date string)
 * @param toDate           inclusive end of the date range (ISO-8601 date string)
 * @param entitiesTotal    total number of entities processed
 * @param featuresComputed number of individual feature values computed
 * @param featuresSkipped  number of values skipped because they were already cached
 * @param duration         wall-clock time the materialization took
 * @param startedAt        when the materialization started
 */
public record MaterializationResult(
        Class<?>  featureClass,
        String    fromDate,
        String    toDate,
        int       entitiesTotal,
        int       featuresComputed,
        int       featuresSkipped,
        Duration  duration,
        Instant   startedAt
) {

    /** Returns the total number of feature-entity combinations attempted. */
    public int totalAttempted() {
        return featuresComputed + featuresSkipped;
    }

    /** Returns {@code true} if every combination was freshly computed. */
    public boolean fullyComputed() {
        return featuresSkipped == 0;
    }
}
