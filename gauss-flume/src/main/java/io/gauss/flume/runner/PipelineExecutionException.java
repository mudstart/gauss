package io.gauss.flume.runner;

/**
 * Thrown when a pipeline step fails during reflection-based execution.
 */
public class PipelineExecutionException extends RuntimeException {

    public PipelineExecutionException(String message) {
        super(message);
    }

    public PipelineExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
