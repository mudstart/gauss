package io.gauss.vigil.experiment;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Default {@link ExperimentStore} implementation backed by an in-memory list.
 *
 * <p>All data is lost when the JVM exits.  Suitable for local development,
 * unit tests, and demos.  Use a JDBC-backed store for persistent tracking.
 *
 * <p>Thread-safe: uses {@link CopyOnWriteArrayList} internally.
 */
public class InMemoryExperimentStore implements ExperimentStore {

    private final CopyOnWriteArrayList<ExperimentRun> runs = new CopyOnWriteArrayList<>();

    @Override
    public void save(ExperimentRun run) {
        // Upsert: replace if the same ID already exists
        runs.removeIf(r -> r.id().equals(run.id()));
        runs.add(run);
    }

    @Override
    public List<ExperimentRun> findAll() {
        return List.copyOf(runs);
    }

    @Override
    public Optional<ExperimentRun> findById(String id) {
        return runs.stream()
                .filter(r -> r.id().equals(id))
                .findFirst();
    }

    @Override
    public List<ExperimentRun> findByName(String experimentName) {
        return runs.stream()
                .filter(r -> r.experimentName().equals(experimentName))
                .toList();
    }

    @Override
    public void clear() {
        runs.clear();
    }

    /** Returns the total number of stored runs. */
    public int size() {
        return runs.size();
    }
}
