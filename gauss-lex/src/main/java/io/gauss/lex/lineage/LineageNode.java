package io.gauss.lex.lineage;

import java.util.Map;

/**
 * A vertex in the lineage directed acyclic graph (HU-050).
 *
 * @param id       unique identifier within the lineage graph
 * @param name     human-readable name (model name, feature name, table name, …)
 * @param type     category of this node
 * @param metadata optional extra properties (version, source URL, entity ID, …)
 */
public record LineageNode(
        String              id,
        String              name,
        LineageNodeType     type,
        Map<String, String> metadata
) {

    public LineageNode {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    // -------------------------------------------------------------------------
    // Named factories for common node types
    // -------------------------------------------------------------------------

    public static LineageNode prediction(String predictionId, String entityId, String modelId) {
        return new LineageNode(predictionId, "prediction:" + predictionId,
                LineageNodeType.PREDICTION,
                Map.of("entityId", entityId, "modelId", modelId));
    }

    public static LineageNode feature(String featureId, String featureName, String entityId) {
        return new LineageNode(featureId, featureName,
                LineageNodeType.FEATURE,
                Map.of("entityId", entityId));
    }

    public static LineageNode model(String modelId, String modelName, String version) {
        return new LineageNode(modelId, modelName,
                LineageNodeType.MODEL,
                Map.of("version", version));
    }

    public static LineageNode pipeline(String pipelineId, String pipelineName) {
        return new LineageNode(pipelineId, pipelineName,
                LineageNodeType.PIPELINE, Map.of());
    }

    public static LineageNode dataSource(String sourceId, String sourceName) {
        return new LineageNode(sourceId, sourceName,
                LineageNodeType.DATA_SOURCE, Map.of());
    }
}
