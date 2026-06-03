package io.gauss.lex.lineage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Records and queries end-to-end data lineage (Lex module, HU-050).
 *
 * <p>Every prediction links backwards through the models, features, pipelines,
 * and raw data sources that produced it.  The full lineage chain satisfies the
 * EU AI Act traceability requirement and enables rapid root-cause analysis.
 *
 * <p>Recording methods are designed to be called from existing framework hooks
 * without impacting inference latency:
 * <ul>
 *   <li>{@link #recordPrediction} — called after each {@code @MLEndpoint} response.</li>
 *   <li>{@link #recordFeatureComputation} — called by the feature store on cache miss.</li>
 *   <li>{@link #recordPipelineExecution} — called by {@code PipelineRunner} on completion.</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * LineageService svc = new LineageService();
 * svc.recordPipelineExecution("pipe-1", "etl-pipeline", "src-1", "postgres/customers");
 * svc.recordFeatureComputation("feat-1", "txCount30d", "e-42", "pipe-1");
 * svc.recordPrediction("pred-1", "e-42", "model-1", "churn-v2", List.of("feat-1"));
 *
 * LineageGraph graph = svc.trace("pred-1");
 * }</pre>
 */
public final class LineageService {

    // node registry: id → node
    private final Map<String, LineageNode> nodeIndex = new ConcurrentHashMap<>();
    // edge list
    private final List<LineageEdge>        edges     = new ArrayList<>();

    // -------------------------------------------------------------------------
    // Recording
    // -------------------------------------------------------------------------

    /**
     * Records that a prediction was produced for {@code entityId} by a model
     * using the listed features.
     *
     * @param predictionId unique ID for this prediction (e.g., UUID)
     * @param entityId     the entity being scored
     * @param modelId      the model registration ID
     * @param modelName    human-readable model name / version
     * @param featureIds   IDs of features consumed (must have been registered via
     *                     {@link #recordFeatureComputation} beforehand)
     */
    public synchronized void recordPrediction(String predictionId,
                                               String entityId,
                                               String modelId,
                                               String modelName,
                                               List<String> featureIds) {
        LineageNode predNode  = LineageNode.prediction(predictionId, entityId, modelId);
        LineageNode modelNode = LineageNode.model(modelId, modelName, "");

        put(predNode);
        put(modelNode);
        edges.add(LineageEdge.servedBy(predictionId, modelId));

        for (String featureId : featureIds) {
            if (nodeIndex.containsKey(featureId)) {
                edges.add(LineageEdge.usedIn(featureId, predictionId));
            }
        }
    }

    /**
     * Records that a feature was computed for {@code entityId} by a pipeline.
     *
     * @param featureId    unique ID for this computed feature value
     * @param featureName  the {@code @Feature} method name
     * @param entityId     the entity the feature was computed for
     * @param pipelineId   the pipeline (or store) that computed the feature
     */
    public synchronized void recordFeatureComputation(String featureId,
                                                       String featureName,
                                                       String entityId,
                                                       String pipelineId) {
        LineageNode featureNode = LineageNode.feature(featureId, featureName, entityId);
        put(featureNode);
        if (nodeIndex.containsKey(pipelineId)) {
            edges.add(LineageEdge.computedBy(featureId, pipelineId));
        }
    }

    /**
     * Records that a pipeline ran and read from a data source.
     *
     * @param pipelineId   unique pipeline execution ID
     * @param pipelineName the {@code @DataPipeline} name
     * @param sourceId     unique ID for the data source
     * @param sourceName   human-readable source description (table, file path, URL)
     */
    public synchronized void recordPipelineExecution(String pipelineId,
                                                      String pipelineName,
                                                      String sourceId,
                                                      String sourceName) {
        LineageNode pipeNode   = LineageNode.pipeline(pipelineId, pipelineName);
        LineageNode sourceNode = LineageNode.dataSource(sourceId, sourceName);
        put(pipeNode);
        put(sourceNode);
        edges.add(LineageEdge.readsFrom(pipelineId, sourceId));
    }

    // -------------------------------------------------------------------------
    // Querying
    // -------------------------------------------------------------------------

    /**
     * Returns the complete lineage graph for {@code predictionId}, tracing
     * backwards from the prediction to all connected features, pipelines, and
     * data sources.
     *
     * @param predictionId the prediction whose lineage to trace
     * @return the full lineage graph, or an empty graph if the ID is unknown
     */
    public synchronized LineageGraph trace(String predictionId) {
        if (!nodeIndex.containsKey(predictionId)) {
            return LineageGraph.empty();
        }

        List<LineageNode> reachable = new ArrayList<>();
        List<LineageEdge> relevant  = new ArrayList<>();
        collectAncestors(predictionId, reachable, relevant);

        return new LineageGraph(reachable, relevant);
    }

    /** Returns the node registered under {@code id}, or empty. */
    public Optional<LineageNode> findNode(String id) {
        return Optional.ofNullable(nodeIndex.get(id));
    }

    /** Total number of nodes registered. */
    public int nodeCount() { return nodeIndex.size(); }

    /** Resets all recorded lineage (for tests). */
    public synchronized void reset() {
        nodeIndex.clear();
        edges.clear();
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private void put(LineageNode node) {
        nodeIndex.putIfAbsent(node.id(), node);
    }

    /** DFS — collects all ancestors of {@code nodeId} (inclusive). */
    private void collectAncestors(String nodeId,
                                   List<LineageNode> nodes,
                                   List<LineageEdge> collected) {
        LineageNode node = nodeIndex.get(nodeId);
        if (node == null || nodes.contains(node)) return;
        nodes.add(node);

        for (LineageEdge edge : edges) {
            if (edge.toId().equals(nodeId)) {
                collected.add(edge);
                collectAncestors(edge.fromId(), nodes, collected);
            }
            if (edge.fromId().equals(nodeId) && !nodes.contains(nodeIndex.get(edge.toId()))) {
                // also include downstream nodes directly connected
                LineageNode downstream = nodeIndex.get(edge.toId());
                if (downstream != null && !nodes.contains(downstream)) {
                    // only add the node already in scope for the root call
                }
            }
        }
    }
}
