package io.gauss.vigil.experiment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Query and comparison service for experiment runs (HU-023).
 *
 * <p>Provides filtered, sorted, paged access to {@link ExperimentRun} records
 * and a diff view for comparing two runs side-by-side.
 */
public final class ExperimentQueryService {

    private final ExperimentStore store;

    public ExperimentQueryService(ExperimentStore store) {
        this.store = store;
    }

    // -------------------------------------------------------------------------
    // Filtered query
    // -------------------------------------------------------------------------

    /**
     * Returns experiment runs that match the given query, sorted and paged.
     *
     * @param query filter, sort, and pagination descriptor
     * @return matching runs (never {@code null})
     */
    public List<ExperimentRun> query(ExperimentQuery query) {
        List<ExperimentRun> all = new ArrayList<>(store.findAll());

        // --- filter by name ---
        if (query.nameFilter() != null && !query.nameFilter().isBlank()) {
            all.removeIf(r -> !r.experimentName().equals(query.nameFilter()));
        }

        // --- filter by tags (run must contain ALL query tags) ---
        if (!query.tags().isEmpty()) {
            all.removeIf(r -> {
                List<String> runTags = List.of(r.tags());
                return !runTags.containsAll(query.tags());
            });
        }

        // --- filter by date range ---
        if (query.from() != null) {
            all.removeIf(r -> r.startedAt().isBefore(query.from()));
        }
        if (query.to() != null) {
            all.removeIf(r -> r.finishedAt() != null && r.finishedAt().isAfter(query.to()));
        }

        // --- sort ---
        if (query.sortMetric() != null) {
            String metric = query.sortMetric();
            Comparator<ExperimentRun> cmp = Comparator.comparingDouble(
                    r -> r.latestMetric(metric).orElse(Double.MAX_VALUE));
            if (!query.sortAscending()) cmp = cmp.reversed();
            all.sort(cmp);
        }

        // --- paginate ---
        int from = query.page() * query.pageSize();
        int to   = Math.min(from + query.pageSize(), all.size());
        if (from >= all.size()) return List.of();
        return List.copyOf(all.subList(from, to));
    }

    // -------------------------------------------------------------------------
    // Diff view
    // -------------------------------------------------------------------------

    /**
     * Returns a side-by-side diff of the two runs identified by the given IDs.
     *
     * @param runId1 ID of the first run
     * @param runId2 ID of the second run
     * @return the diff
     * @throws IllegalArgumentException if either run is not found
     */
    public ExperimentDiff diff(String runId1, String runId2) {
        ExperimentRun r1 = store.findById(runId1)
                .orElseThrow(() -> new IllegalArgumentException("Run not found: " + runId1));
        ExperimentRun r2 = store.findById(runId2)
                .orElseThrow(() -> new IllegalArgumentException("Run not found: " + runId2));

        return new ExperimentDiff(r1, r2, metricsDiff(r1, r2), paramsDiff(r1, r2));
    }

    // -------------------------------------------------------------------------

    private static Map<String, double[]> metricsDiff(ExperimentRun r1, ExperimentRun r2) {
        Set<String> allMetrics = r1.metrics().stream()
                .map(ExperimentMetric::name)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        r2.metrics().stream().map(ExperimentMetric::name).forEach(allMetrics::add);

        Map<String, double[]> diff = new LinkedHashMap<>();
        for (String m : allMetrics) {
            double v1 = r1.latestMetric(m).orElse(Double.NaN);
            double v2 = r2.latestMetric(m).orElse(Double.NaN);
            diff.put(m, new double[]{v1, v2});
        }
        return Map.copyOf(diff);
    }

    private static Map<String, Object[]> paramsDiff(ExperimentRun r1, ExperimentRun r2) {
        Set<String> allKeys = new java.util.LinkedHashSet<>(r1.params().keySet());
        allKeys.addAll(r2.params().keySet());

        Map<String, Object[]> diff = new LinkedHashMap<>();
        for (String k : allKeys) {
            Object v1 = r1.params().get(k);
            Object v2 = r2.params().get(k);
            if (!java.util.Objects.equals(v1, v2)) {
                diff.put(k, new Object[]{v1, v2});
            }
        }
        return Map.copyOf(diff);
    }
}
