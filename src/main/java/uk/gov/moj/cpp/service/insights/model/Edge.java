package uk.gov.moj.cpp.service.insights.model;

/**
 * Represents an edge between two nodes in the Cytoscape graph.
 *
 * @param source ID of the source node.
 * @param target ID of the target node.
 */
public record Edge(
        String source,
        String target
) {}
