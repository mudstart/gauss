package io.gauss.augur.batch;

/** Lifecycle states of a batch prediction job (HU-018). */
public enum BatchJobStatus {
    /** Accepted but not yet started. */
    PENDING,
    /** Currently processing inputs. */
    RUNNING,
    /** All inputs processed successfully. */
    COMPLETED,
    /** Cancelled by the client before completion. */
    CANCELLED,
    /** Terminated due to an unhandled exception. */
    FAILED
}
