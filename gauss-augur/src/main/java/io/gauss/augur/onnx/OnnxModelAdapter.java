package io.gauss.augur.onnx;

import ai.onnxruntime.*;
import io.gauss.augur.model.ModelDescriptor;
import io.gauss.augur.model.OnnxInferenceException;
import io.gauss.augur.model.OnnxModel;

import java.util.*;

/**
 * {@link OnnxModel} implementation backed by an ONNX Runtime {@link OrtSession}.
 *
 * <p>Thread-safe: ONNX Runtime sessions support concurrent inference from
 * multiple threads after the session is created.
 *
 * <p>Supported input/output Java types (mapped to ONNX element types):
 * <table>
 *   <tr><th>Java type</th><th>ONNX element type</th></tr>
 *   <tr><td>float[] / float[][]</td><td>FLOAT</td></tr>
 *   <tr><td>double[] / double[][]</td><td>DOUBLE</td></tr>
 *   <tr><td>long[] / long[][]</td><td>INT64</td></tr>
 *   <tr><td>int[] / int[][]</td><td>INT32</td></tr>
 * </table>
 */
public class OnnxModelAdapter implements OnnxModel {

    private final OrtEnvironment   env;
    private final OrtSession       session;
    private final ModelDescriptor  descriptor;

    OnnxModelAdapter(OrtEnvironment env, OrtSession session, ModelDescriptor descriptor) {
        this.env        = env;
        this.session    = session;
        this.descriptor = descriptor;
    }

    // -------------------------------------------------------------------------
    // OnnxModel
    // -------------------------------------------------------------------------

    @Override
    public Map<String, Object> run(Map<String, Object> inputs) {
        Map<String, OnnxTensor> tensors = new HashMap<>();
        try {
            for (Map.Entry<String, Object> entry : inputs.entrySet()) {
                tensors.put(entry.getKey(), toTensor(entry.getValue()));
            }

            try (OrtSession.Result result = session.run(tensors)) {
                Map<String, Object> outputs = new LinkedHashMap<>();
                for (Map.Entry<String, OnnxValue> e : result) {
                    outputs.put(e.getKey(), e.getValue().getValue());
                }
                return outputs;
            }

        } catch (OrtException e) {
            throw new OnnxInferenceException(
                    "Inference failed for model '" + descriptor.name() + "'", e);
        } finally {
            tensors.values().forEach(OnnxTensor::close);
        }
    }

    @Override
    public ModelDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public void close() {
        try {
            session.close();
        } catch (OrtException e) {
            throw new OnnxInferenceException("Failed to close ONNX session", e);
        }
    }

    // -------------------------------------------------------------------------
    // Tensor conversion helpers
    // -------------------------------------------------------------------------

    private OnnxTensor toTensor(Object value) throws OrtException {
        return switch (value) {
            case float[]   v -> OnnxTensor.createTensor(env, v);
            case float[][] v -> OnnxTensor.createTensor(env, v);
            case double[]  v -> OnnxTensor.createTensor(env, v);
            case double[][] v -> OnnxTensor.createTensor(env, v);
            case long[]    v -> OnnxTensor.createTensor(env, v);
            case long[][]  v -> OnnxTensor.createTensor(env, v);
            case int[]     v -> OnnxTensor.createTensor(env, java.nio.IntBuffer.wrap(v),
                                        new long[]{v.length});
            default -> throw new OnnxInferenceException(
                    "Unsupported tensor input type: " + value.getClass().getName() +
                    ". Supported: float[], float[][], double[], double[][], long[], long[][]");
        };
    }
}
