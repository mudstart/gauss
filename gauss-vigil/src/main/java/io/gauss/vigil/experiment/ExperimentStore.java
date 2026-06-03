package io.gauss.vigil.experiment;

import java.util.List;
import java.util.Optional;

/**
 * SPI for persisting and querying {@link ExperimentRun} records.
 *
 * <p>Implementations are discovered via {@link java.util.ServiceLoader}.
 * If none is registered the framework uses {@link InMemoryExperimentStore}
 * as the default (suitable for local development and testing; data is lost
 * when the JVM exits).
 *
 * <p>All methods must be thread-safe — the store may be called concurrently
 * from multiple training threads.
 */
public interface ExperimentStore {

    /**
     * Persists a run.  If a run with the same {@code id} already exists it
     * is replaced (upsert semantics).
     *
     * @param run the run to persist
     */
    void save(ExperimentRun run);

    /**
     * Returns all persisted runs in insertion order.
     */
    List<ExperimentRun> findAll();

    /**
     * Returns the run with the given ID, or empty if not found.
     */
    Optional<ExperimentRun> findById(String id);

    /**
     * Returns all runs belonging to the given experiment group, in insertion
     * order.
     *
     * @param experimentName the value of {@link io.gauss.core.annotation.Experiment#name()}
     */
    List<ExperimentRun> findByName(String experimentName);

    /**
     * Removes all runs from the store.  Primarily intended for testing.
     */
    void clear();
}
