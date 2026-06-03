package io.gauss.vigil.experiment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link ExperimentQueryService}.
 * Covers HU-023 acceptance criteria.
 */
class ExperimentQueryServiceTest {

    private InMemoryExperimentStore store;
    private ExperimentRunner        runner;
    private ExperimentQueryService  svc;

    @BeforeEach
    void setUp() {
        store  = new InMemoryExperimentStore();
        runner = new ExperimentRunner(store);
        svc    = new ExperimentQueryService(store);
    }

    private void seed() {
        runner.run("churn", new String[]{"xgboost"}, Map.of("lr", 0.1), ctx -> {
            ctx.logMetric("auc", 0.91);
            return null;
        });
        runner.run("churn", new String[]{"xgboost", "v2"}, Map.of("lr", 0.05), ctx -> {
            ctx.logMetric("auc", 0.95);
            return null;
        });
        runner.run("nlp", new String[]{"bert"}, Map.of("lr", 0.001), ctx -> {
            ctx.logMetric("auc", 0.88);
            return null;
        });
    }

    // -------------------------------------------------------------------------
    // Filtering
    // -------------------------------------------------------------------------

    @Test
    void query_noFilter_returnsAll() {
        seed();
        assertThat(svc.query(ExperimentQuery.all())).hasSize(3);
    }

    @Test
    void query_nameFilter_returnsOnlyMatching() {
        seed();
        List<ExperimentRun> result = svc.query(
                ExperimentQuery.builder().name("churn").build());
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(r -> r.experimentName().equals("churn"));
    }

    @Test
    void query_tagFilter_requiresAllTags() {
        seed();
        // "xgboost" matches runs 1 and 2; "v2" only matches run 2
        List<ExperimentRun> result = svc.query(
                ExperimentQuery.builder().tags(List.of("xgboost", "v2")).build());
        assertThat(result).hasSize(1);
        assertThat(List.of(result.get(0).tags())).contains("v2");
    }

    @Test
    void query_dateRangeFilter_excludesOutOfRange() {
        seed();
        // Ask for runs that started after now + 1h — should be empty
        ExperimentQuery q = ExperimentQuery.builder()
                .from(Instant.now().plusSeconds(3600))
                .build();
        assertThat(svc.query(q)).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Sorting
    // -------------------------------------------------------------------------

    @Test
    void query_sortByMetricDescending_highestFirst() {
        seed();
        List<ExperimentRun> result = svc.query(
                ExperimentQuery.builder()
                        .sortByMetric("auc", false)
                        .build());
        assertThat(result.get(0).latestMetric("auc")).hasValue(0.95);
        assertThat(result.get(result.size() - 1).latestMetric("auc")).hasValue(0.88);
    }

    @Test
    void query_sortByMetricAscending_lowestFirst() {
        seed();
        List<ExperimentRun> result = svc.query(
                ExperimentQuery.builder()
                        .sortByMetric("auc", true)
                        .build());
        assertThat(result.get(0).latestMetric("auc")).hasValue(0.88);
    }

    // -------------------------------------------------------------------------
    // Pagination
    // -------------------------------------------------------------------------

    @Test
    void query_paginationPage0_returnsFirstN() {
        seed();
        List<ExperimentRun> result = svc.query(
                ExperimentQuery.builder().page(0, 2).build());
        assertThat(result).hasSize(2);
    }

    @Test
    void query_paginationPage1_returnsRemaining() {
        seed();
        List<ExperimentRun> result = svc.query(
                ExperimentQuery.builder().page(1, 2).build());
        assertThat(result).hasSize(1);
    }

    @Test
    void query_outOfBoundsPage_returnsEmpty() {
        seed();
        List<ExperimentRun> result = svc.query(
                ExperimentQuery.builder().page(10, 5).build());
        assertThat(result).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Diff
    // -------------------------------------------------------------------------

    @Test
    void diff_highlightsMetricDifferences() {
        seed();
        List<ExperimentRun> runs = svc.query(ExperimentQuery.all());
        ExperimentRun r1 = runs.get(0);
        ExperimentRun r2 = runs.get(1);

        ExperimentDiff diff = svc.diff(r1.id(), r2.id());

        assertThat(diff.run1()).isSameAs(r1);
        assertThat(diff.run2()).isSameAs(r2);
        assertThat(diff.metricsDiff()).containsKey("auc");
        assertThat(diff.metricsDiff().get("auc")[0]).isEqualTo(0.91);
        assertThat(diff.metricsDiff().get("auc")[1]).isEqualTo(0.95);
    }

    @Test
    void diff_highlightsParamDifferences() {
        seed();
        List<ExperimentRun> runs = svc.query(
                ExperimentQuery.builder().name("churn").build());
        ExperimentDiff diff = svc.diff(runs.get(0).id(), runs.get(1).id());

        // lr differs: 0.1 vs 0.05
        assertThat(diff.paramsDiff()).containsKey("lr");
    }

    @Test
    void diff_unknownId_throwsIllegalArgument() {
        seed();
        String validId = svc.query(ExperimentQuery.all()).get(0).id();
        assertThatIllegalArgumentException()
                .isThrownBy(() -> svc.diff(validId, "nonexistent"));
    }
}
