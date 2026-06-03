package io.gauss.vigil.experiment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link InMemoryExperimentStore}.
 */
class InMemoryExperimentStoreTest {

    private InMemoryExperimentStore store;
    private ExperimentRunner runner;

    @BeforeEach
    void setUp() {
        store  = new InMemoryExperimentStore();
        runner = new ExperimentRunner(store);
    }

    private void makeRun(String name) {
        runner.run(name, new String[0], Map.of(), ctx -> {
            ctx.logMetric("auc", 0.9);
            return "result";
        });
    }

    @Test
    void save_andFindById_roundTrips() {
        makeRun("exp-a");
        String id = store.findAll().get(0).id();
        Optional<ExperimentRun> found = store.findById(id);
        assertThat(found).isPresent();
        assertThat(found.get().experimentName()).isEqualTo("exp-a");
    }

    @Test
    void findAll_returnsAllSavedRuns() {
        makeRun("exp-a");
        makeRun("exp-b");
        makeRun("exp-a");
        assertThat(store.findAll()).hasSize(3);
    }

    @Test
    void findByName_returnsOnlyMatchingRuns() {
        makeRun("exp-a");
        makeRun("exp-b");
        makeRun("exp-a");
        assertThat(store.findByName("exp-a")).hasSize(2);
        assertThat(store.findByName("exp-b")).hasSize(1);
        assertThat(store.findByName("exp-c")).isEmpty();
    }

    @Test
    void save_upserts_existingId() {
        makeRun("exp-a");
        ExperimentRun original = store.findAll().get(0);
        // Build a replacement with the same ID but a different name
        ExperimentContext ctx = new ExperimentContext();
        ExperimentRun replacement = ExperimentRun.completed(
                original.id(), "exp-replaced", new String[0],
                Map.of(), original.startedAt(), original.finishedAt(), ctx, null);
        store.save(replacement);
        assertThat(store.size()).isEqualTo(1);
        assertThat(store.findById(original.id()).get().experimentName()).isEqualTo("exp-replaced");
    }

    @Test
    void clear_removesAllRuns() {
        makeRun("exp-a");
        makeRun("exp-b");
        store.clear();
        assertThat(store.findAll()).isEmpty();
        assertThat(store.size()).isZero();
    }

    @Test
    void findById_returnsEmpty_forUnknownId() {
        assertThat(store.findById("nonexistent")).isEmpty();
    }
}
