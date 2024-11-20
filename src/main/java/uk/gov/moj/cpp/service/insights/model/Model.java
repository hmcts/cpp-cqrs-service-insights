package uk.gov.moj.cpp.service.insights.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents the graph model containing nodes and edges.
 */
public class Model {
    private final List<Node> nodes;
    private final List<Edge> edges;
    private final Map<String, Node> nodeMap; // For quick lookup

    public Model() {
        nodes = new ArrayList<>();
        edges = new ArrayList<>();
        nodeMap = new HashMap<>();
    }

    /**
     * Adds a node to the model.
     *
     * @param node The node to add.
     */
    public void addNode(Node node) {
        nodes.add(node);
        nodeMap.put(node.id(), node);
    }

    /**
     * Adds an edge to the model.
     *
     * @param edge The edge to add.
     */
    public void addEdge(Edge edge) {
        edges.add(edge);
    }

    /**
     * Checks if a node with the specified ID exists in the model.
     *
     * @param id The ID of the node to check.
     * @return true if the node exists, false otherwise.
     */
    public boolean hasNode(String id) {
        return nodeMap.containsKey(id);
    }

    // Getters for nodes and edges
    public List<Node> getNodes() {
        return nodes;
    }

    public List<Edge> getEdges() {
        return edges;
    }
}
