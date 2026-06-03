package io.gauss.augur.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gauss.augur.model.ModelDescriptor;
import io.gauss.augur.model.OnnxModel;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * In-memory {@link OnnxModel} that returns a fixed output map for every
 * {@link #run} call — no ONNX Runtime required.
 *
 * <p>Constructed from a JSON string describing the output:
 * <pre>{@code
 * // Returns {"probability": [0.9, 0.1]} for every run() call
 * MockOnnxModel mock = new MockOnnxModel(
 *         "models/churn.onnx",
 *         "{\"probability\":[0.9,0.1]}");
 * }</pre>
 *
 * <p>Tracks how many times {@link #run} was called via {@link #callCount()}.
 */
public class MockOnnxModel implements OnnxModel {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String              path;
    private final Map<String, Object> fixedOutput;
    private int                       callCount = 0;
    private boolean                   closed    = false;

    /**
     * Constructs a mock model for the given path with a fixed JSON output.
     *
     * @param path       model path (used in the descriptor, must match {@code @InjectModel})
     * @param outputJson JSON object whose entries become the output map
     * @throws IllegalArgumentException if {@code outputJson} is not valid JSON
     */
    @SuppressWarnings("unchecked")
    public MockOnnxModel(String path, String outputJson) {
        this.path = path;
        try {
            this.fixedOutput = MAPPER.readValue(outputJson, Map.class);
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "MockOnnxModel: invalid output JSON for path '" + path + "': " + outputJson, e);
        }
    }

    // -------------------------------------------------------------------------
    // OnnxModel
    // -------------------------------------------------------------------------

    /**
     * Returns the pre-configured output map, ignoring the supplied inputs.
     * Increments {@link #callCount()} on every invocation.
     */
    @Override
    public Map<String, Object> run(Map<String, Object> inputs) {
        callCount++;
        return fixedOutput;
    }

    @Override
    public ModelDescriptor descriptor() {
        return new ModelDescriptor(
                deriveName(path), path,
                List.of("input"),
                List.of(fixedOutput.keySet().toArray(String[]::new)));
    }

    @Override
    public void close() {
        closed = true;
    }

    // -------------------------------------------------------------------------
    // Test introspection helpers
    // -------------------------------------------------------------------------

    /** Returns the number of times {@link #run} has been called. */
    public int callCount() { return callCount; }

    /** Returns {@code true} if {@link #close()} has been called. */
    public boolean isClosed() { return closed; }

    /** Resets the call counter to zero. */
    public void reset() { callCount = 0; }

    // -------------------------------------------------------------------------

    private static String deriveName(String path) {
        String filename = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        return filename.endsWith(".onnx") ? filename.substring(0, filename.length() - 5) : filename;
    }
}
