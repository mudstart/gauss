package io.gauss.vigil.experiment;

import java.util.Map;

/**
 * Side-by-side comparison of two {@link ExperimentRun} instances (HU-023).
 *
 * <p>Used by the experiment comparison dashboard to highlight differences in
 * hyper-parameters and evaluation metrics between two runs.
 *
 * @param run1        first run
 * @param run2        second run
 * @param metricsDiff metric name to a two-element array {@code [value1, value2]};
 *                    only contains metrics that appear in at least one of the runs
 * @param paramsDiff  param name to a two-element array {@code [value1, value2]};
 *                    only contains params that differ between the two runs
 */
public record ExperimentDiff(
        ExperimentRun       run1,
        ExperimentRun       run2,
        Map<String, double[]>  metricsDiff,
        Map<String, Object[]>  paramsDiff
) {}
