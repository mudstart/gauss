package io.gauss.augur.model;

import java.util.List;

/**
 * Immutable metadata for a loaded ONNX model.
 *
 * @param name        logical model name (derived from the file path if not set)
 * @param path        classpath path used to load the model
 * @param inputNames  ordered list of ONNX input-node names
 * @param outputNames ordered list of ONNX output-node names
 */
public record ModelDescriptor(
        String       name,
        String       path,
        List<String> inputNames,
        List<String> outputNames
) {}
