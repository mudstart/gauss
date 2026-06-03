package io.gauss.augur.model;

import java.util.Map;

/**
 * Thin façade over an ONNX Runtime session, exposing tensor-level inference.
 *
 * <p>Typical usage:
 * <pre>{@code
 * float[][] inputBatch = {{0.1f, 0.5f, 0.8f}};
 * Map<String, Object> result = model.run(Map.of("features", inputBatch));
 * float[][] scores = (float[][]) result.get("output");
 * }</pre>
 *
 * <p>Implementations must be thread-safe; ONNX Runtime sessions support
 * concurrent inference from multiple threads.
 */
public interface OnnxModel extends AutoCloseable {

    /**
     * Runs inference with the given named input tensors.
     *
     * <p>Input values must be numeric arrays in one of the shapes supported by
     * ONNX Runtime Java:
     * <ul>
     *   <li>{@code float[]} / {@code float[][]}</li>
     *   <li>{@code long[]}  / {@code long[][]}</li>
     *   <li>{@code int[]}   / {@code int[][]}</li>
     *   <li>{@code double[]}/ {@code double[][]}</li>
     * </ul>
     *
     * @param inputs map of ONNX input-node name → array value
     * @return map of ONNX output-node name → array value
     * @throws OnnxInferenceException if the ONNX Runtime call fails
     */
    Map<String, Object> run(Map<String, Object> inputs);

    /**
     * Returns the metadata (name, input/output shapes) for this model.
     */
    ModelDescriptor descriptor();

    /** Releases the underlying ONNX Runtime session. */
    @Override
    void close();
}
