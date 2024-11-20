package uk.gov.moj.cpp.service.insights.html;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generates an HTML file visualizing the hearing catalog using Cytoscape.js,
 * with nodes aligned vertically into five distinct columns based on their categories:
 * Command Handler, Aggregator, Event Processor, Event Listener, and Unknown.
 */
public class ServiceHtmlGenerator {

    // Node Classes
    private static final String CLASS_COMMAND_HANDLER = "command-handler";
    private static final String CLASS_AGGREGATE = "aggregate";
    private static final String CLASS_EVENT_PROCESSOR = "event-processor";
    private static final String CLASS_EVENT_LISTENER = "event-listener";
    private static final String CLASS_UNKNOWN = "unknown";

    // Colors
    private static final String COLOR_COMMAND_HANDLER = "#4da6ff";
    private static final String COLOR_AGGREGATE = "#ffcc99";
    private static final String COLOR_EVENT_PROCESSOR = "#ff9966";
    private static final String COLOR_EVENT_LISTENER = "#66cc66";
    private static final String COLOR_UNKNOWN = "#999999";
    private static final String COLOR_EDGE = "#666666";
    private static final String COLOR_EDGE_HIGHLIGHT = "#FFD700";
    private static final String COLOR_NODE_HIGHLIGHT = "red";

    // Shapes
    private static final String SHAPE_COMMAND_HANDLER = "roundrectangle";
    private static final String SHAPE_AGGREGATE = "roundrectangle";
    private static final String SHAPE_EVENT_PROCESSOR = "rectangle";
    private static final String SHAPE_EVENT_LISTENER = "ellipse";
    private static final String SHAPE_UNKNOWN = "diamond";

    // Layout Constants
    private static final int COLUMN_WIDTH = 300; // Horizontal spacing between columns
    private static final int ROW_HEIGHT = 100;   // Vertical spacing between nodes within a column
    private static final int START_X = 100;      // Starting X position for the first column
    private static final int START_Y = 100;      // Starting Y position for the first row

    private static final String HTML_FOOT = """
                </body>
                </html>
            """;

    /**
     * Generates the complete HTML content as a String based on the provided model,
     * with nodes aligned vertically into five columns.
     *
     * @param commandHandler            Set of command handler names.
     * @param processHandler            Set of process handler names.
     * @param aggregateSimpleClassNames List of aggregator class names.
     * @param commandUseAggregatesNames Map linking command handlers to their associated aggregators.
     * @param commandGenerateEvents     Map linking command handlers to their associated event processors.
     * @param listenerHandlerAllValues  Set of event listener names.
     * @param processorGenerateEvents   Map linking process handlers to their associated event processors.
     * @return HTML content as a String.
     */
    public static String generateHTML(
            Set<String> commandHandler,
            Set<String> processHandler,
            List<String> aggregateSimpleClassNames,
            Map<String, Set<String>> commandUseAggregatesNames,
            Map<String, Set<String>> commandGenerateEvents,
            Set<String> listenerHandlerAllValues,
            Map<String, Set<String>> processorGenerateEvents) {

        StringBuilder nodesAndEdges = new StringBuilder();

        // Track if event-listener node is needed
        boolean needsEventListener = false;

        // Counters to keep track of node positions within each column
        int commandHandlerCount = 0;
        int aggregateCount = 0;
        int eventProcessorCount = 0;
        int eventListenerCount = 0;
        int unknownCount = 0;

        // Add Command Handler nodes
        for (String value : commandHandler) {
            String id = generateId("commandhandler", value);
            String position = getPosition(CLASS_COMMAND_HANDLER, commandHandlerCount++);
            nodesAndEdges.append(generateNodeData(id, CLASS_COMMAND_HANDLER, COLOR_COMMAND_HANDLER, SHAPE_COMMAND_HANDLER, position));
        }

        // Add Aggregator nodes
        for (String aggName : aggregateSimpleClassNames) {
            String aggregatorId = generateId("aggregator", aggName);
            String position = getPosition(CLASS_AGGREGATE, aggregateCount++);
            nodesAndEdges.append(generateNodeData(aggregatorId, CLASS_AGGREGATE, COLOR_AGGREGATE, SHAPE_AGGREGATE, position));
        }

        // Add Process Handler nodes
        for (String value : processHandler) {
            String id = generateId("eventprocessor", value);
            String position = getPosition(CLASS_EVENT_PROCESSOR, eventProcessorCount++);
            nodesAndEdges.append(generateNodeData(id, CLASS_EVENT_PROCESSOR, COLOR_EVENT_PROCESSOR, SHAPE_EVENT_PROCESSOR, position));
        }

        // Add Event Listener nodes
        for (String value : listenerHandlerAllValues) {
            String id = generateId("eventlistener", value);
            String position = getPosition(CLASS_EVENT_LISTENER, eventListenerCount++);
            nodesAndEdges.append(generateNodeData(id, CLASS_EVENT_LISTENER, COLOR_EVENT_LISTENER, SHAPE_EVENT_LISTENER, position));
        }

        // Create edges from command handlers to aggregators
        for (Map.Entry<String, Set<String>> entry : commandUseAggregatesNames.entrySet()) {
            String commandHandlerName = entry.getKey();
            Set<String> associatedAggregates = entry.getValue();

            boolean commandHandlerExists = commandHandler.contains(commandHandlerName);

            if (!commandHandlerExists) {
                needsEventListener = true; // Mark that event-listener node is needed
            }

            for (String aggregateName : associatedAggregates) {
                String aggregatorId = generateId("aggregator", aggregateName);
                if (commandHandlerExists) {
                    // Add edge from commandHandler to aggregator
                    nodesAndEdges.append(generateEdgeData(generateId("commandhandler", commandHandlerName), aggregatorId, ""));
                } else {
                    // Add edge from event-listener to aggregator
                    // nodesAndEdges.append(generateEdgeData(generateId("eventlistener", "event-listener"), aggregatorId, ""));
                }
            }
        }

        // Create edges from command handlers to event processors
        for (Map.Entry<String, Set<String>> entry : commandGenerateEvents.entrySet()) {
            String commandHandlerName = entry.getKey();
            Set<String> associatedEventProcessors = entry.getValue();

            boolean commandHandlerExists = commandHandler.contains(commandHandlerName);

            if (!commandHandlerExists) {
                needsEventListener = true; // Mark that event-listener node is needed
            }

            for (String eventProcessorName : associatedEventProcessors) {
                String eventProcessorId = generateId("eventprocessor", eventProcessorName);
                if (processHandler.contains(eventProcessorName)) {
                    if (commandHandlerExists) {
                        // Add edge from commandHandler to eventProcessor
                        nodesAndEdges.append(generateEdgeData(generateId("commandhandler", commandHandlerName), eventProcessorId, ""));
                    }
                }

                String eventListenerId = generateId("eventlistener", eventProcessorName);
                if (listenerHandlerAllValues.contains(eventProcessorName)) {
                    if (commandHandlerExists) {
                        // Add edge from commandHandler to eventListener
                        nodesAndEdges.append(generateEdgeData(generateId("commandhandler", commandHandlerName), eventListenerId, ""));
                    }
                } else if (!processHandler.contains(eventProcessorName)) {
                    // Add edge with 'not found' label
                    nodesAndEdges.append(generateEdgeData(generateId("commandhandler", commandHandlerName), generateId("unknown", "unknown"), "not found: " + eventProcessorName));
                }
            }
        }

        // Create edges from process handlers to event processors
        for (Map.Entry<String, Set<String>> entry : processorGenerateEvents.entrySet()) {
            String processHandlerName = entry.getKey();
            Set<String> associatedEventProcessors = entry.getValue();

            for (String eventProcessorName : associatedEventProcessors) {
                String eventProcessorId = generateId("eventprocessor", processHandlerName);
                if (processHandler.contains(processHandlerName)) {
                    if (commandHandler.contains(eventProcessorName)) {
                        // Add edge from processHandler to eventProcessor
                        nodesAndEdges.append(generateEdgeData(eventProcessorId, generateId("commandhandler", eventProcessorName), ""));
                    }
                }
            }
        }

        // Conditionally add event-listener node
        if (needsEventListener) {
            String id = generateId("eventlistener", "event-listener");
            String position = getPosition(CLASS_EVENT_LISTENER, eventListenerCount++);
            nodesAndEdges.append(generateNodeData(id, CLASS_EVENT_LISTENER, COLOR_EVENT_LISTENER, SHAPE_EVENT_LISTENER, position));
        }

        // Add "Unknown" node
        String unknownId = generateId("unknown", "unknown");
        String unknownPosition = getPosition(CLASS_UNKNOWN, unknownCount++);
        nodesAndEdges.append(generateNodeData(unknownId, CLASS_UNKNOWN, COLOR_UNKNOWN, SHAPE_UNKNOWN, unknownPosition));

        // Remove trailing comma and newline if present
        String nodesAndEdgesStr = nodesAndEdges.toString().trim();
        if (nodesAndEdgesStr.endsWith(",")) {
            nodesAndEdgesStr = nodesAndEdgesStr.substring(0, nodesAndEdgesStr.length() - 1);
        }

        // Generate node-specific styles
        String nodeStyles = generateNodeStyles();

        // Construct the full HTML content using text blocks and placeholders
        String htmlContent = String.format("""
                         <!DOCTYPE html>
                        <html lang="en">
                        <head>
                            <meta charset="UTF-8">
                            <meta name="viewport" content="width=device-width, initial-scale=1.0">
                            <title>Handler Graph</title>
                            <script src="https://unpkg.com/cytoscape@3.30.2/dist/cytoscape.min.js"></script>
                            <style>
                                body { margin: 0; font-family: Arial, sans-serif; }
                                #cy { height: 94vh; width: 100vw; border: 1px solid black; }
                                .controls { display: flex; align-items: center; gap: 10px; padding: 10px; background-color: #f4f4f4; }
                                .legend { display: flex; gap: 20px; align-items: center; }
                                .legend-color { width: 25px; height: 25px; margin-right: 8px; }
                                .search-box, .reset-button { padding: 5px; font-size: 16px; }
                            </style>
                        </head>
                        <body>
                        <div class="controls">
                            <input type="text" class="search-box" id="search" placeholder="Search nodes...">
                            <button class="reset-button" id="reset">Reset</button>
                            <div class="legend">
                                %s
                            </div>
                        </div>
                        <div id="cy"></div>
                        <script>
                            var cy = cytoscape({
                                container: document.getElementById('cy'),
                                elements: [
                                    %s
                                ],
                                style: [
                                    {
                                        selector: 'node',
                                        style: {
                                            'content': 'data(label)',
                                            'text-valign': 'center',
                                            'text-halign': 'center',
                                            'font-size': '14px',
                                            'text-wrap': 'wrap',
                                            'width': 'label',
                                            'height': 'label',
                                            'padding': '10px',
                                            'min-width': '100px',
                                            'min-height': '50px'
                                        }
                                    },
                                    %s
                                    ,{
                                        selector: 'edge',
                                        style: {
                                            'content': 'data(label)',
                                            'font-size': '12px',
                                            'text-background-color': '#ffffff',
                                            'text-background-opacity': 1,
                                            'text-rotation': 'autorotate',
                                            'text-margin-y': -10,
                                            'width': 2,
                                            'line-color': '%s',
                                            'target-arrow-color': '%s',
                                            'target-arrow-shape': 'triangle',
                                            'curve-style': 'bezier'
                                        }
                                    }
                                ],
                                layout: { 
                                    name: 'preset', 
                                    padding: 20, 
                                    fit: true
                                }
                            });
                        
                            // Search functionality
                            document.getElementById('search').addEventListener('input', function() {
                                var query = this.value.toLowerCase();
                                cy.nodes().forEach(function(node) {
                                    node.style('background-color', '');
                                });
                                if (query !== '') {
                                    cy.nodes().forEach(function(node) {
                                        var label = node.data('label').toLowerCase();
                                        if (label.includes(query)) {
                                            node.style('background-color', '%s'); /* Highlight color */
                                        }
                                    });
                                }
                            });
                        
                            // Node click functionality
                            cy.on('tap', 'node', function(event) {
                                cy.nodes().forEach(function(node) {
                                    node.style('background-color', '');
                                    node.style('border-color', '');
                                });
                                cy.edges().forEach(function(edge) {
                                    edge.style('line-color', '%s');
                                });
                        
                                var node = event.target;
                                node.style('border-color', '%s'); /* Highlight selected node border */
                                node.connectedEdges().forEach(edge => {
                                    edge.style('line-color', '%s'); /* Highlight connecting edges */
                                });
                        
                                // Copy node label to clipboard without alert
                                if (navigator.clipboard && window.isSecureContext) {
                                    navigator.clipboard.writeText(node.data('label')).catch(function(err) {
                                        console.error('Could not copy text: ', err);
                                    });
                                } else {
                                    var textArea = document.createElement('textarea');
                                    textArea.value = node.data('label');
                                    textArea.style.position = 'fixed';  // Avoid scrolling to bottom
                                    document.body.appendChild(textArea);
                                    textArea.focus();
                                    textArea.select();
                                    try {
                                        document.execCommand('copy');
                                    } catch (err) {
                                        console.error('Fallback: Unable to copy', err);
                                    }
                                    document.body.removeChild(textArea);
                                }
                            });
                        
                            // Reset functionality
                            document.getElementById('reset').addEventListener('click', function() {
                                document.getElementById('search').value = ''; // Clear the search box
                                cy.nodes().forEach(function(node) {
                                    node.style('background-color', '');
                                    node.style('border-color', '');
                                });
                                cy.edges().forEach(function(edge) {
                                    edge.style('line-color', '%s');
                                });
                            });
                        </script>
                        %s
                        """,

                generateLegend(),                     // 1
                nodesAndEdgesStr,                     // 2
                nodeStyles,                           // 3
                COLOR_EDGE,                           // 4
                COLOR_EDGE,                           // 5
                COLOR_NODE_HIGHLIGHT,                 // 6 (Highlight color in search)
                COLOR_EDGE,                           // 7 (Line-color in node click edges)
                COLOR_NODE_HIGHLIGHT,                 // 8 (Border-color in node click)
                COLOR_EDGE,                           // 9 (Line-color in node click connecting edges)
                COLOR_EDGE,                           // 10 (Line-color in reset functionality)
                HTML_FOOT                             // 11
        );

        return htmlContent;
    }

    /**
     * Saves the provided HTML content to the specified file.
     *
     * @param html     HTML content as a String.
     * @param filePath Path to the output HTML file.
     * @throws IOException If an I/O error occurs.
     */
    public static void saveToFile(String html, Path filePath) throws IOException {
        try (FileWriter writer = new FileWriter(filePath.toFile())) {
            writer.write(html);
        }
    }

    /**
     * Escapes characters in a string to prevent JavaScript injection and
     * escape '%' characters to avoid format specifier issues.
     *
     * @param input The input string.
     * @return Escaped string safe for JavaScript and String.format.
     */
    private static String escapeJavaScript(String input) {
        if (input == null) {return "";}
        return input.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("%", "%%"); // Escape '%' to prevent String.format issues
    }

    /**
     * Generates the HTML for a single node with a specified position.
     *
     * @param id       The unique identifier for the node.
     * @param clazz    The class/category of the node.
     * @param color    The background color of the node.
     * @param shape    The shape of the node.
     * @param position The fixed position of the node in the format "x,y".
     * @return A String representing the node in Cytoscape.js format with position.
     */
    private static String generateNodeData(String id, String clazz, String color, String shape, String position) {
        String label = id.replaceFirst("^(aggregator_|eventprocessor_|eventlistener_|commandhandler_|unknown_)", "").replace("_", " ");
        String[] coords = position.split(",");
        String x = coords[0];
        String y = coords[1];
        return String.format("{ data: { id: '%s', label: '%s' }, position: { x: %s, y: %s }, classes: '%s' },\n",
                escapeJavaScript(id),
                escapeJavaScript(label),
                x,
                y,
                clazz);
    }

    /**
     * Generates the HTML for a single edge.
     *
     * @param source The source node ID.
     * @param target The target node ID.
     * @param label  The label for the edge (optional).
     * @return A String representing the edge in Cytoscape.js format.
     */
    private static String generateEdgeData(String source, String target, String label) {
        if (label == null || label.isEmpty()) {
            return String.format("{ data: { source: '%s', target: '%s' } },\n",
                    escapeJavaScript(source),
                    escapeJavaScript(target));
        } else {
            return String.format("{ data: { source: '%s', target: '%s', label: '%s' } },\n",
                    escapeJavaScript(source),
                    escapeJavaScript(target),
                    escapeJavaScript(label));
        }
    }

    /**
     * Generates the HTML for a legend item.
     *
     * @return A String representing the legend items in HTML.
     */
    private static String generateLegend() {
        return String.join("\n",
                generateLegendItem("Command Handler", COLOR_COMMAND_HANDLER),
                generateLegendItem("Aggregator", COLOR_AGGREGATE),
                generateLegendItem("Event Processor", COLOR_EVENT_PROCESSOR),
                generateLegendItem("Event Listener", COLOR_EVENT_LISTENER),
                generateLegendItem("Unknown", COLOR_UNKNOWN)
        );
    }

    /**
     * Generates the HTML for a single legend item.
     *
     * @param label The label for the legend item.
     * @param color The color associated with the legend item.
     * @return A String representing the legend item in HTML.
     */
    private static String generateLegendItem(String label, String color) {
        return String.format("""
                <div class="legend-item">
                    <div class="legend-color" style="background-color: %s;"></div>%s
                </div>
                """, color, escapeJavaScript(label));
    }

    /**
     * Generates unique IDs for nodes based on type and name.
     * Replaces all non-word characters with underscores to ensure consistency.
     *
     * @param prefix The prefix indicating the node type.
     * @param name   The name of the node.
     * @return A unique ID string.
     */
    private static String generateId(String prefix, String name) {
        return prefix + "_" + name.replaceAll("\\W+", "_");
    }

    /**
     * Generates the CSS styles for node classes.
     *
     * @return A String representing the CSS styles for nodes.
     */
    private static String generateNodeStyles() {
        return String.join(",\n",
                String.format("""
                        {
                            selector: '.%s',
                            style: {
                                'background-color': '%s',
                                'shape': '%s',
                                'border-width': 2,
                                'border-color': '#0059b3'
                            }
                        }""", CLASS_COMMAND_HANDLER, COLOR_COMMAND_HANDLER, SHAPE_COMMAND_HANDLER),
                String.format("""
                        {
                            selector: '.%s',
                            style: {
                                'background-color': '%s',
                                'shape': '%s',
                                'border-width': 2,
                                'border-color': '#cc6600'
                            }
                        }""", CLASS_AGGREGATE, COLOR_AGGREGATE, SHAPE_AGGREGATE),
                String.format("""
                        {
                            selector: '.%s',
                            style: {
                                'background-color': '%s',
                                'shape': '%s',
                                'border-width': 2,
                                'border-color': '#cc3333'
                            }
                        }""", CLASS_EVENT_PROCESSOR, COLOR_EVENT_PROCESSOR, SHAPE_EVENT_PROCESSOR),
                String.format("""
                        {
                            selector: '.%s',
                            style: {
                                'background-color': '%s',
                                'shape': '%s',
                                'border-width': 2,
                                'border-color': '#339933'
                            }
                        }""", CLASS_EVENT_LISTENER, COLOR_EVENT_LISTENER, SHAPE_EVENT_LISTENER),
                String.format("""
                        {
                            selector: '.%s',
                            style: {
                                'background-color': '%s',
                                'shape': '%s',
                                'border-width': 2,
                                'border-color': '#666666'
                            }
                        }""", CLASS_UNKNOWN, COLOR_UNKNOWN, SHAPE_UNKNOWN)
        );
    }

    /**
     * Calculates the fixed position for a node based on its class and index.
     *
     * @param clazz The class/category of the node.
     * @param index The index of the node within its class.
     * @return A String representing the position in "x,y" format.
     */
    private static String getPosition(String clazz, int index) {
        int x;
        switch (clazz) {
            case CLASS_COMMAND_HANDLER:
                x = START_X;
                break;
            case CLASS_AGGREGATE:
                x = START_X + COLUMN_WIDTH;
                break;
            case CLASS_EVENT_PROCESSOR:
                x = START_X + 2 * COLUMN_WIDTH;
                break;
            case CLASS_EVENT_LISTENER:
                x = START_X + 3 * COLUMN_WIDTH;
                break;
            case CLASS_UNKNOWN:
                x = START_X + 4 * COLUMN_WIDTH;
                break;
            default:
                x = START_X + 5 * COLUMN_WIDTH; // Extra column if needed
        }
        int y = START_Y + index * ROW_HEIGHT;
        return x + "," + y;
    }
}
