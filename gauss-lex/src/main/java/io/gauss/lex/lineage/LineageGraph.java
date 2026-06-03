package io.gauss.lex.lineage;

import java.util.List;
import java.util.Optional;

/**
 * Immutable directed acyclic graph representing the complete lineage of a
 * data artefact (HU-050).
 *
 * <p>Nodes are vertices (predictions, features, pipelines, data sources).
 * Edges represent causal dependencies from upstream to downstream.
 * The graph is always returned by {@link LineageService#trace(String)}.
 *
 * @param nodes all vertices in the lineage chain
 * @param edges all directed edges in the graph
 */
public record LineageGraph(List<LineageNode> nodes, List<LineageEdge> edges) {

    public LineageGraph {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
    }

    /** Returns the node with the given ID, or empty if not found. */
    public Optional<LineageNode> findNode(String id) {
        return nodes.stream().filter(n -> n.id().equals(id)).findFirst();
    }

    /**
     * Returns all nodes whose edges point TO {@code nodeId}
     * (i.e., direct upstream predecessors).
     */
    public List<LineageNode> predecessors(String nodeId) {
        return edges.stream()
                .filter(e -> e.toId().equals(nodeId))
                .map(LineageEdge::fromId)
                .flatMap(id -> findNode(id).stream())
                .toList();
    }

    /**
     * Returns all nodes that {@code nodeId} directly feeds into
     * (i.e., direct downstream successors).
     */
    public List<LineageNode> successors(String nodeId) {
        return edges.stream()
                .filter(e -> e.fromId().equals(nodeId))
                .map(LineageEdge::toId)
                .flatMap(id -> findNode(id).stream())
                .toList();
    }

    /** Returns all nodes of the given type. */
    public List<LineageNode> nodesByType(LineageNodeType type) {
        return nodes.stream().filter(n -> n.type() == type).toList();
    }

    /** Total number of nodes in the graph. */
    public int nodeCount() { return nodes.size(); }

    /** Total number of edges in the graph. */
    public int edgeCount() { return edges.size(); }

    /** An empty graph with no nodes or edges. */
    public static LineageGraph empty() {
        return new LineageGraph(List.of(), List.of());
    }
}
