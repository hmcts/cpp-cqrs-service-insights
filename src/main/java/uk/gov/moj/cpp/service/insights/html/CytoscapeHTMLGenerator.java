package uk.gov.moj.cpp.service.insights.html;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class CytoscapeHTMLGenerator {

    // Define constants for CSS classes
    private static final String CLASS_PUBLIC_COMMAND = "public-command";
    private static final String CLASS_COMMAND = "command";
    private static final String CLASS_QUERY = "query";
    private static final String CLASS_UNKNOWN = "unknown";

    /**
     * Generates an HTML file visualizing events and their associated commands/queries using Cytoscape.js.
     *
     * @param eventCommandMapP Map of events to their corresponding commands/queries.
     * @param outputPath       Path where the HTML file will be generated.
     * @param primaryText      The primary heading text for the sidebar.
     * @param allTitle         The title for the "Show All" link in the sidebar.
     * @throws IOException If an I/O error occurs.
     */
    public static void generateHTMLFile(Map<String, Set<String>> eventCommandMapP, String outputPath,
                                        final String primaryText, final String allTitle) throws IOException {
        // Sort the map by keys using LinkedHashMap to maintain order
        Map<String, Set<String>> eventCommandMap = eventCommandMapP.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(LinkedHashMap::new,
                        (m, e) -> m.put(e.getKey(), e.getValue()),
                        LinkedHashMap::putAll);

        // Initialize a StringBuilder to build the HTML content
        StringBuilder htmlBuilder = new StringBuilder();

        // Append the initial HTML structure using StringBuilder's append method
        htmlBuilder.append("<!DOCTYPE html>\n");
        htmlBuilder.append("<html lang=\"en\">\n");
        htmlBuilder.append("<head>\n");
        htmlBuilder.append("    <meta charset=\"UTF-8\">\n");
        htmlBuilder.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        htmlBuilder.append("    <title>Graph Visualization</title>\n");
        htmlBuilder.append("    <script src=\"https://cdnjs.cloudflare.com/ajax/libs/cytoscape/3.30.2/cytoscape.min.js\"></script>\n");
        htmlBuilder.append("    <link href=\"https://stackpath.bootstrapcdn.com/bootstrap/4.5.2/css/bootstrap.min.css\" rel=\"stylesheet\">\n");
        htmlBuilder.append("    <style>\n");
        htmlBuilder.append("        body { display: flex; }\n");
        htmlBuilder.append("        #sidebar { width: 20%; padding: 20px; background-color: #f4f4f4; border-right: 1px solid #ccc; overflow-y: auto; height: 93vh; word-wrap: break-word; white-space: normal; }\n");
        htmlBuilder.append("        #main { width: 80%; padding: 20px; }\n");
        htmlBuilder.append("        #cy { width: 100%; height: 950px; border: 1px solid #ccc; }\n");
        htmlBuilder.append("        #search { margin-bottom: 10px; padding: 5px; width: 100%; }\n");
        htmlBuilder.append("        ul { list-style: none; padding-left: 0; }\n");
        htmlBuilder.append("        li { margin: 5px 0; }\n");
        htmlBuilder.append("        a { text-decoration: none; color: #007bff; cursor: pointer; }\n");
        htmlBuilder.append("        a:hover { text-decoration: underline; }\n");
        htmlBuilder.append("        .public-command { background-color: #F28B82; color: white; padding: 5px; border-radius: 5px; }\n");
        htmlBuilder.append("        .command { background-color: #1f77b4; color: #1f77b4; padding: 5px; border-radius: 5px; }\n");
        htmlBuilder.append("        .query { background-color: #2ca02c; color: #2ca02c; padding: 5px; border-radius: 5px; }\n");
        htmlBuilder.append("        .unknown { background-color: #ff7f0e; color: #ff7f0e; padding: 5px; border-radius: 5px; }\n");
        htmlBuilder.append("        .event-link { margin: 5px 0; padding: 5px; background-color: #f8f9fa; border-radius: 5px; }\n");
        htmlBuilder.append("        .public-link { color: #64B5F6; font-weight: bold; }\n");
        htmlBuilder.append("    </style>\n");
        htmlBuilder.append("</head>\n");
        htmlBuilder.append("<body>\n");
        htmlBuilder.append("    <div id=\"sidebar\" class=\"bg-light\">\n");
        htmlBuilder.append("        <h2 class=\"text-primary\">" + escapeHtml(primaryText) + "</h2>\n");
        htmlBuilder.append("        <ul class=\"list-group\">\n");
        htmlBuilder.append("            <li class=\"list-group-item\"><a onclick=\"showGraph('all')\">" + escapeHtml(allTitle) + "</a></li>\n");


        // Append event names to the sidebar
        for (String event : eventCommandMap.keySet()) {
            if (event.startsWith("public")) {
                htmlBuilder.append(String.format(
                        "            <li class=\"list-group-item\"><a class=\"public-link\" onclick=\"showGraph('%s')\">%s</a></li><br>",
                        escapeJs(event), escapeHtml(event)));
            } else {
                htmlBuilder.append(String.format(
                        "            <li class=\"list-group-item\"><a onclick=\"showGraph('%s')\">%s</a></li><br>",
                        escapeJs(event), escapeHtml(event)));
            }
        }

        // Continue building the main section
        htmlBuilder.append("""
                                </ul>
                            </div>
                            <div id="main" class="container-fluid">
                                <input type="text" id="search" placeholder="Search node labels..." class="form-control mb-3" />
                                <div id="cy"></div>
                                <div id="event-links" class="mt-4"></div>
                            </div>
                            <script>
                                function showGraph(event) {
                                    let elements = [];
                                    document.getElementById('event-links').innerHTML = '';
                                    if (event === 'all') {
                        """);

        // Append elements for all events
        for (Map.Entry<String, Set<String>> entry : eventCommandMap.entrySet()) {
            String key = escapeJs(entry.getKey());
            String labelKey = escapeJs(entry.getKey());
            htmlBuilder.append(String.format(
                    "                elements.push({ data: { id: '%s', label: '%s' } });%n",
                    key, labelKey));
            for (String value : entry.getValue()) {
                String escapedValue = escapeJs(value);
                String labelValue = escapeJs(value);
                htmlBuilder.append(String.format(
                        "                elements.push({ data: { id: '%s', label: '%s' } });%n",
                        escapedValue, labelValue));
                String cssClass = determineCssClass(value);
                htmlBuilder.append(String.format(
                        "                elements.push({ data: { source: '%s', target: '%s' }, classes: '%s' });%n",
                        key, escapedValue, cssClass));
            }
        }

        // Continue with the else block for individual events
        htmlBuilder.append("""
                                    } else {
                        """);

        // Append elements for individual events
        for (Map.Entry<String, Set<String>> entry : eventCommandMap.entrySet()) {
            String key = escapeJs(entry.getKey());
            String labelKey = escapeJs(entry.getKey());
            htmlBuilder.append(String.format(
                    "                if (event === '%s') {%n", key));
            htmlBuilder.append(String.format(
                    "                    elements.push({ data: { id: '%s', label: '%s' } });%n",
                    key, labelKey));
            htmlBuilder.append("                    let eventLinksHTML = '';\n");
            for (String value : entry.getValue()) {
                String escapedValue = escapeJs(value);
                String labelValue = escapeJs(value);
                htmlBuilder.append(String.format(
                        "                    elements.push({ data: { id: '%s', label: '%s' } });%n",
                        escapedValue, labelValue));
                htmlBuilder.append(String.format(
                        "                    eventLinksHTML += '<div class=\"event-link\">%s</div>';%n",
                        escapeHtml(value)));
                String cssClass = determineCssClass(value);
                htmlBuilder.append(String.format(
                        "                    elements.push({ data: { source: '%s', target: '%s' }, classes: '%s' });%n",
                        key, escapedValue, cssClass));
            }
            htmlBuilder.append("                    document.getElementById('event-links').innerHTML = eventLinksHTML;\n");
            htmlBuilder.append("                }\n");
        }

        // Continue with Cytoscape initialization and other scripts
        htmlBuilder.append("""
                                    }
                                    var cy = cytoscape({
                                        container: document.getElementById('cy'),
                                        elements: elements,
                                        style: [
                                            {
                                                selector: 'node',
                                                style: {
                                                    'background-color': '#3498DB',
                                                    'label': 'data(label)',
                                                    'width': 20,
                                                    'height': 20,
                                                    'font-size': 10,
                                                    'text-valign': 'center',
                                                    'text-halign': 'center',
                                                    'color': '#2C3E50'
                                                }
                                            },
                                            {
                                                selector: 'edge',
                                                style: {
                                                    'width': 1.5,
                                                    'line-color': '#BDC3C7',
                                                    'target-arrow-color': '#BDC3C7',
                                                    'target-arrow-shape': 'triangle'
                                                }
                                            },
                                            {
                                                selector: 'node.highlighted',
                                                style: {
                                                    'background-color': '#F1C40F',
                                                    'border-color': '#F1C40F',
                                                    'border-width': 2
                                                }
                                            },
                                            {
                                                selector: 'edge.highlighted',
                                                style: {
                                                    'line-color': '#F1C40F',
                                                    'width': 2
                                                }
                                            },
                                            {
                                                selector: '.public-command',
                                                style: {
                                                    'line-color': '#E74C3C',
                                                    'target-arrow-color': '#E74C3C',
                                                    'width': 2
                                                }
                                            },
                                            {
                                                selector: '.unknown',
                                                style: {
                                                    'line-color': '#95A5A6',
                                                    'target-arrow-color': '#95A5A6',
                                                    'width': 1.5
                                                }
                                            },
                                            {
                                                selector: '.command',
                                                style: {
                                                    'line-color': '#2ECC71',
                                                    'target-arrow-color': '#2ECC71',
                                                    'width': 2
                                                }
                                            },
                                            {
                                                selector: '.query',
                                                style: {
                                                    'line-color': '#9B59B6',
                                                    'target-arrow-color': '#9B59B6',
                                                    'width': 2
                                                }
                                            }
                                        ],
                                        layout: {
                                            name: 'cose',
                                            fit: true,
                                            padding: 50,
                                            nodeOverlap: 10,
                                            idealEdgeLength: 100,
                                            edgeElasticity: 100,
                                            nodeRepulsion: 4000,
                                            gravity: 0.8,
                                            numIter: 1000
                                        }
                                    });

                                    // Highlight selected node and its edges
                                    cy.on('tap', 'node', function(evt) {
                                        const node = evt.target;
                                        cy.elements().removeClass('highlighted');
                                        node.addClass('highlighted');
                                        node.connectedEdges().addClass('highlighted').connectedNodes().addClass('highlighted');
                                    });

                                    // Search functionality
                                    document.getElementById('search').addEventListener('input', function(e) {
                                        const query = e.target.value.toLowerCase();
                                        cy.elements().removeClass('highlighted');
                                        if (query !== '') {
                                            const matchingNodes = cy.nodes().filter(node => node.data('label').toLowerCase().includes(query));
                                            matchingNodes.addClass('highlighted');
                                        }
                                    });
                                }

                                // Automatically display the first event graph
                                showGraph('%s');
                            </script>
                        </body>
                        </html>
                        """.formatted(escapeJs(eventCommandMap.keySet().iterator().next())));

        // Write the HTML content to the output file using try-with-resources
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath))) {
            writer.write(htmlBuilder.toString());
        }
    }

    /**
     * Determines the CSS class based on the value's characteristics.
     *
     * @param value The value string to evaluate.
     * @return The corresponding CSS class.
     */
    private static String determineCssClass(String value) {
        if (value.startsWith("public") || value.toLowerCase().contains("system users")) {
            return CLASS_PUBLIC_COMMAND;
        } else if (value.contains(".command") || value.startsWith("Permission:")) {
            return CLASS_COMMAND;
        } else if (value.contains(".query")) {
            return CLASS_QUERY;
        } else {
            return CLASS_UNKNOWN;
        }
    }

    /**
     * Escapes special characters in HTML to prevent injection.
     *
     * @param text The text to escape.
     * @return The escaped HTML string.
     */
    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /**
     * Escapes special characters in JavaScript strings to prevent injection.
     *
     * @param text The text to escape.
     * @return The escaped JavaScript string.
     */
    private static String escapeJs(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
