package io.gauss.augur.onnx;

import io.gauss.augur.model.OnnxModel;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe cache of loaded {@link OnnxModel} instances.
 *
 * <p>Each model is loaded once and reused across threads; the registry holds
 * a strong reference to open sessions.  Call {@link #close()} at application
 * shutdown to release all ONNX Runtime resources.
 *
 * <p>Usage:
 * <pre>{@code
 * ModelRegistry registry = new ModelRegistry();
 * OnnxModel model = registry.getOrLoad("models/churn.onnx");
 * }</pre>
 */
public class ModelRegistry implements AutoCloseable {

    private final List<ModelLoader>          loaders = new ArrayList<>();
    private final Map<String, OnnxModel>     cache   = new ConcurrentHashMap<>();

    public ModelRegistry() {
        loaders.add(new OnnxModelLoader());
        // Additional loaders may be added via ServiceLoader in future
    }

    /** Constructs a registry with a custom list of loaders (useful for tests). */
    public ModelRegistry(List<ModelLoader> loaders) {
        this.loaders.addAll(loaders);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns a cached model for {@code path}, loading it on first access.
     *
     * @param path model path as declared in {@link io.gauss.augur.annotation.InjectModel}
     * @return loaded model
     * @throws IllegalArgumentException if no loader supports the path
     * @throws RuntimeException         if loading fails
     */
    public OnnxModel getOrLoad(String path) {
        return cache.computeIfAbsent(path, this::doLoad);
    }

    /**
     * Returns {@code true} if a model has already been loaded for {@code path}.
     */
    public boolean isLoaded(String path) {
        return cache.containsKey(path);
    }

    /**
     * Registers an additional {@link ModelLoader}.
     * Registered loaders are checked in registration order before the default
     * {@link OnnxModelLoader}.
     */
    public void registerLoader(ModelLoader loader) {
        loaders.add(0, loader);
    }

    /**
     * Closes all cached models and releases ONNX Runtime sessions.
     */
    @Override
    public void close() {
        cache.values().forEach(OnnxModel::close);
        cache.clear();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private OnnxModel doLoad(String path) {
        ModelLoader loader = loaders.stream()
                .filter(l -> l.supports(path))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No ModelLoader supports path: '" + path + "'"));
        try {
            return loader.load(path);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load model: " + path, e);
        }
    }
}
