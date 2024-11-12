package uk.gov.moj.cpp.service.insights.model;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Utility class to build the graph model for Cytoscape.js visualization.
 */
public class ModelBuilder {

    /**
     * Builds the graph model with nodes and edges based on command handlers, process handlers, and aggregates.
     *
     * @param commandHandler         Map of command handler keys to their collections.
     * @param processHandler         Map of process handler keys to their collections.
     * @param aggregateSimpleClassNames List of aggregate class names.
     * @return The constructed graph model.
     */
    public static Model buildModel(Map<String, Set<String>> commandHandler,
                                   Map<String, Set<String>> processHandler,
                                   List<String> aggregateSimpleClassNames) {
        Model model = new Model();

        // 1. Create Aggregate Nodes
        for (String aggregate : aggregateSimpleClassNames) {
            String aggregateId = "aggregate_" + escapeId(aggregate);
            String aggregateLabel = aggregate; // You can customize the label if needed
            model.addNode(new Node(aggregateId, aggregateLabel, "aggregate"));
        }

        // 2. Create Event Processor Nodes (if you have distinct event processors)
        // If event processors are part of the aggregates or have their own list, handle accordingly.
        // For demonstration, let's assume they are part of aggregates and share the same naming convention.

        // 3. Create Command Handler Nodes and Connect to Aggregates
        for (Map.Entry<String, Set<String>> entry : commandHandler.entrySet()) {
            String handlerKey = entry.getKey();
            Set<String> handlerValues = entry.getValue();

            String handlerId = "commandHandler_" + escapeId(handlerKey);
            String handlerLabel = String.join(", ", handlerValues); // Combine the set into a single label

            // Add the handler node
            model.addNode(new Node(handlerId, handlerLabel, "command"));

            // Determine which aggregate this handler connects to
            // Assuming handlerKey corresponds to an aggregate name
            String correspondingAggregateId = "aggregate_" + escapeId(handlerKey);
            if (model.hasNode(correspondingAggregateId)) {
                // Create an edge from handler to aggregate
                model.addEdge(new Edge(handlerId, correspondingAggregateId));
            } else {
                // Handle the case where the aggregate node does not exist
                System.err.println("Aggregate node not found for handler: " + handlerKey);
            }
        }

        // 4. Create Process Handler Nodes and Connect to Event Processors
        for (Map.Entry<String, Set<String>> entry : processHandler.entrySet()) {
            String processorKey = entry.getKey();
            Set<String> processorValues = entry.getValue();

            String processorId = "processHandler_" + escapeId(processorKey);
            String processorLabel = String.join(", ", processorValues); // Combine the set into a single label

            // Add the processor node
            model.addNode(new Node(processorId, processorLabel, "process-handler"));

            // Determine which event processor this connects to
            // Assuming processorKey corresponds to an event processor or aggregate
            String correspondingAggregateId = "aggregate_" + escapeId(processorKey);
            if (model.hasNode(correspondingAggregateId)) {
                // Create an edge from processor to aggregate
                model.addEdge(new Edge(processorId, correspondingAggregateId));
            } else {
                // Handle the case where the aggregate node does not exist
                System.err.println("Aggregate node not found for processor: " + processorKey);
            }
        }

        return model;
    }

    /**
     * Escapes non-alphanumeric characters to create valid IDs.
     *
     * @param input The input string.
     * @return The escaped string.
     */
    private static String escapeId(String input) {
        return input.replaceAll("[^a-zA-Z0-9]", "_");
    }
}
