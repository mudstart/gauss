package io.gauss.vigil.experiment;

/**
 * Lifecycle state of an {@link ExperimentRun}.
 */
public enum RunStatus {

    /** The run is currently executing. */
    RUNNING,

    /** The run finished successfully and its results have been persisted. */
    COMPLETED,

    /** The run threw an exception and was recorded as failed. */
    FAILED
}
