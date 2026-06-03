package io.gauss.augur.onnx;

import io.gauss.augur.model.OnnxModel;

import java.io.IOException;

/**
 * SPI for loading {@link OnnxModel} instances from a model path.
 *
 * <p>The default implementation ({@link OnnxModelLoader}) reads model bytes
 * from the classpath.  Custom implementations may load from object storage,
 * a model registry (MLflow, BentoML), or a remote HTTP endpoint.
 *
 * <p>Implementations are discovered via
 * {@link java.util.ServiceLoader ServiceLoader} or registered programmatically
 * on {@link ModelRegistry}.
 */
public interface ModelLoader {

    /**
     * Returns {@code true} if this loader can handle the given path.
     *
     * @param path classpath-relative or absolute path declared in
     *             {@link io.gauss.augur.annotation.InjectModel @InjectModel}
     */
    boolean supports(String path);

    /**
     * Loads and returns an {@link OnnxModel} ready for inference.
     *
     * @param path model path
     * @return loaded model (caller is responsible for closing it)
     * @throws IOException if the model cannot be read or is malformed
     */
    OnnxModel load(String path) throws IOException;
}
