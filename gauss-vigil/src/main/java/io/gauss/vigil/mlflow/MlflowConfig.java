package io.gauss.vigil.mlflow;

/**
 * Configuration for the MLflow external tracking backend (HU-026).
 *
 * @param trackingUri  base URL of the MLflow server (e.g., {@code http://mlflow:5000})
 * @param experimentName  default experiment name to use when creating runs
 */
public record MlflowConfig(String trackingUri, String experimentName) {

    /** Creates a config with a default experiment name of {@code "Default"}. */
    public static MlflowConfig of(String trackingUri) {
        return new MlflowConfig(trackingUri, "Default");
    }

    /** The MLflow REST endpoint for creating runs. */
    public String createRunUrl() {
        return base() + "/api/2.0/mlflow/runs/create";
    }

    /** The MLflow REST endpoint for logging metrics. */
    public String logMetricUrl() {
        return base() + "/api/2.0/mlflow/runs/log-metric";
    }

    /** The MLflow REST endpoint for listing experiments. */
    public String listExperimentsUrl() {
        return base() + "/api/2.0/mlflow/experiments/list";
    }

    private String base() {
        return trackingUri.endsWith("/")
                ? trackingUri.substring(0, trackingUri.length() - 1)
                : trackingUri;
    }
}
