package io.gauss.augur.onnx;

import ai.onnxruntime.*;
import io.gauss.augur.model.ModelDescriptor;
import io.gauss.augur.model.OnnxModel;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Default {@link ModelLoader} that reads {@code .onnx} model bytes from the
 * application classpath and creates an {@link OnnxModelAdapter}.
 *
 * <p>Supports classpath-relative paths:
 * <pre>
 *   models/churn.onnx
 *   ml/models/text-classifier.onnx
 * </pre>
 */
public class OnnxModelLoader implements ModelLoader {

    private final OrtEnvironment env;

    public OnnxModelLoader() {
        this(OrtEnvironment.getEnvironment());
    }

    /** Visible for testing — allows injecting a custom (or mocked) environment. */
    OnnxModelLoader(OrtEnvironment env) {
        this.env = env;
    }

    @Override
    public boolean supports(String path) {
        return path != null && path.endsWith(".onnx");
    }

    @Override
    public OnnxModel load(String path) throws IOException {
        byte[] modelBytes = readClasspathResource(path);
        try {
            OrtSession session = env.createSession(modelBytes, new OrtSession.SessionOptions());
            ModelDescriptor desc = buildDescriptor(path, session);
            return new OnnxModelAdapter(env, session, desc);
        } catch (OrtException e) {
            throw new IOException("Failed to load ONNX model from path: " + path, e);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static byte[] readClasspathResource(String path) throws IOException {
        String normalized = path.startsWith("/") ? path : "/" + path;
        try (InputStream is = OnnxModelLoader.class.getResourceAsStream(normalized)) {
            if (is == null) {
                throw new IOException(
                        "ONNX model not found on classpath: " + path);
            }
            return is.readAllBytes();
        }
    }

    private static ModelDescriptor buildDescriptor(String path, OrtSession session)
            throws OrtException {
        String name = deriveModelName(path);
        List<String> inputNames  = new ArrayList<>(session.getInputNames());
        List<String> outputNames = new ArrayList<>(session.getOutputNames());
        return new ModelDescriptor(name, path, inputNames, outputNames);
    }

    static String deriveModelName(String path) {
        String filename = path.contains("/")
                ? path.substring(path.lastIndexOf('/') + 1)
                : path;
        // Strip ".onnx" suffix
        return filename.endsWith(".onnx")
                ? filename.substring(0, filename.length() - 5)
                : filename;
    }
}
