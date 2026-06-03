package io.gauss.augur.model;

/**
 * Thrown when an ONNX Runtime inference call fails.
 */
public class OnnxInferenceException extends RuntimeException {

    public OnnxInferenceException(String message) {
        super(message);
    }

    public OnnxInferenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
