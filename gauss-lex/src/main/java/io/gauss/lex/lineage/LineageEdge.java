package io.gauss.lex.lineage;

/**
 * A directed edge in the lineage graph expressing a causal relationship
 * between two nodes (HU-050).
 *
 * <p>Edges point <em>from cause to effect</em>:
 * {@code DATA_SOURCE → PIPELINE → FEATURE → PREDICTION}.
 *
 * @param fromId   ID of the source (upstream / cause) node
 * @param toId     ID of the target (downstream / effect) node
 * @param relation human-readable label describing the relationship
 *                 (e.g., {@code "computed_by"}, {@code "used_in"}, {@code "served_by"})
 */
public record LineageEdge(String fromId, String toId, String relation) {

    public static LineageEdge computedBy(String featureId, String pipelineId) {
        return new LineageEdge(pipelineId, featureId, "computed_by");
    }

    public static LineageEdge usedIn(String featureId, String predictionId) {
        return new LineageEdge(featureId, predictionId, "used_in");
    }

    public static LineageEdge servedBy(String predictionId, String modelId) {
        return new LineageEdge(modelId, predictionId, "served_by");
    }

    public static LineageEdge readsFrom(String pipelineId, String sourceId) {
        return new LineageEdge(sourceId, pipelineId, "reads_from");
    }
}
