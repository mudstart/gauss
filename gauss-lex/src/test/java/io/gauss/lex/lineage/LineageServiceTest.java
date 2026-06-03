package io.gauss.lex.lineage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link LineageService} and the lineage graph model.
 * Covers HU-050 acceptance criteria.
 */
class LineageServiceTest {

    private LineageService svc;

    @BeforeEach
    void setUp() {
        svc = new LineageService();
        // Build a typical lineage chain:
        //   postgres/customers (DATA_SOURCE)
        //     → etl-pipe-1 (PIPELINE)
        //       → feat-tx-count (FEATURE for entity e-42)
        //         → pred-1 (PREDICTION by churn-v2)
        svc.recordPipelineExecution("pipe-1", "etl-pipeline", "src-1", "postgres/customers");
        svc.recordFeatureComputation("feat-1", "txCount30d", "e-42", "pipe-1");
        svc.recordFeatureComputation("feat-2", "clv",        "e-42", "pipe-1");
        svc.recordPrediction("pred-1", "e-42", "model-1", "churn-v2",
                List.of("feat-1", "feat-2"));
    }

    // -------------------------------------------------------------------------
    // LineageNode factories
    // -------------------------------------------------------------------------

    @Test
    void lineageNode_prediction_hasCorrectType() {
        LineageNode n = LineageNode.prediction("p-1", "e-1", "m-1");
        assertThat(n.type()).isEqualTo(LineageNodeType.PREDICTION);
    }

    @Test
    void lineageNode_feature_containsEntityId() {
        LineageNode n = LineageNode.feature("f-1", "txCount", "e-99");
        assertThat(n.metadata()).containsEntry("entityId", "e-99");
    }

    @Test
    void lineageNode_dataSource_hasCorrectType() {
        assertThat(LineageNode.dataSource("s-1", "pg/table").type())
                .isEqualTo(LineageNodeType.DATA_SOURCE);
    }

    // -------------------------------------------------------------------------
    // LineageEdge factories
    // -------------------------------------------------------------------------

    @Test
    void lineageEdge_computedBy_directionIsCorrect() {
        LineageEdge e = LineageEdge.computedBy("feat-1", "pipe-1");
        assertThat(e.fromId()).isEqualTo("pipe-1");
        assertThat(e.toId()).isEqualTo("feat-1");
        assertThat(e.relation()).isEqualTo("computed_by");
    }

    @Test
    void lineageEdge_usedIn_directionIsCorrect() {
        LineageEdge e = LineageEdge.usedIn("feat-1", "pred-1");
        assertThat(e.fromId()).isEqualTo("feat-1");
        assertThat(e.toId()).isEqualTo("pred-1");
    }

    // -------------------------------------------------------------------------
    // LineageService — trace
    // -------------------------------------------------------------------------

    @Test
    void trace_unknownId_returnsEmptyGraph() {
        LineageGraph g = svc.trace("nonexistent");
        assertThat(g.nodeCount()).isZero();
        assertThat(g.edgeCount()).isZero();
    }

    @Test
    void trace_prediction_includesPredictionNode() {
        LineageGraph g = svc.trace("pred-1");
        assertThat(g.findNode("pred-1")).isPresent();
    }

    @Test
    void trace_prediction_includesModelNode() {
        LineageGraph g = svc.trace("pred-1");
        assertThat(g.findNode("model-1")).isPresent();
    }

    @Test
    void trace_prediction_includesFeatureNodes() {
        LineageGraph g = svc.trace("pred-1");
        assertThat(g.findNode("feat-1")).isPresent();
        assertThat(g.findNode("feat-2")).isPresent();
    }

    @Test
    void trace_prediction_includesPipelineNode() {
        LineageGraph g = svc.trace("pred-1");
        assertThat(g.findNode("pipe-1")).isPresent();
    }

    @Test
    void trace_prediction_includesDataSourceNode() {
        LineageGraph g = svc.trace("pred-1");
        assertThat(g.findNode("src-1")).isPresent();
    }

    @Test
    void trace_prediction_nodeTypes_allPresent() {
        LineageGraph g = svc.trace("pred-1");
        assertThat(g.nodesByType(LineageNodeType.PREDICTION)).hasSize(1);
        assertThat(g.nodesByType(LineageNodeType.MODEL)).hasSize(1);
        assertThat(g.nodesByType(LineageNodeType.FEATURE)).hasSize(2);
        assertThat(g.nodesByType(LineageNodeType.PIPELINE)).hasSize(1);
        assertThat(g.nodesByType(LineageNodeType.DATA_SOURCE)).hasSize(1);
    }

    @Test
    void trace_hasEdges() {
        LineageGraph g = svc.trace("pred-1");
        assertThat(g.edgeCount()).isGreaterThan(0);
    }

    // -------------------------------------------------------------------------
    // LineageGraph — navigation
    // -------------------------------------------------------------------------

    @Test
    void predecessors_ofPrediction_includesFeatures() {
        LineageGraph g = svc.trace("pred-1");
        List<LineageNode> preds = g.predecessors("pred-1");
        List<String> predIds = preds.stream().map(LineageNode::id).toList();
        assertThat(predIds).containsAnyOf("feat-1", "feat-2", "model-1");
    }

    @Test
    void predecessors_ofFeature_includesPipeline() {
        LineageGraph g = svc.trace("pred-1");
        List<LineageNode> preds = g.predecessors("feat-1");
        assertThat(preds).anyMatch(n -> n.id().equals("pipe-1"));
    }

    @Test
    void findNode_returnsCorrectNode() {
        LineageGraph g = svc.trace("pred-1");
        assertThat(g.findNode("pipe-1").orElseThrow().name()).isEqualTo("etl-pipeline");
    }

    // -------------------------------------------------------------------------
    // Isolation: separate predictions don't mix lineage
    // -------------------------------------------------------------------------

    @Test
    void trace_separatePredictions_independentGraphs() {
        svc.recordFeatureComputation("feat-x", "riskScore", "e-99", "pipe-1");
        svc.recordPrediction("pred-2", "e-99", "model-2", "risk-v1", List.of("feat-x"));

        LineageGraph g = svc.trace("pred-2");
        // pred-1 nodes should not appear in pred-2 graph
        assertThat(g.findNode("pred-1")).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Service state
    // -------------------------------------------------------------------------

    @Test
    void nodeCount_reflectsRegistrations() {
        // pipe-1, src-1, feat-1, feat-2, model-1, pred-1 = 6 unique nodes
        assertThat(svc.nodeCount()).isEqualTo(6);
    }

    @Test
    void reset_clearsAllLineage() {
        svc.reset();
        assertThat(svc.nodeCount()).isZero();
        assertThat(svc.trace("pred-1").nodeCount()).isZero();
    }
}
