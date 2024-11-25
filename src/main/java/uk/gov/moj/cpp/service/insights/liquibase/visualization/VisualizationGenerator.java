package uk.gov.moj.cpp.service.insights.liquibase.visualization;

import uk.gov.moj.cpp.service.insights.liquibase.model.Column;
import uk.gov.moj.cpp.service.insights.liquibase.model.ForeignKey;
import uk.gov.moj.cpp.service.insights.liquibase.model.Table;
import uk.gov.moj.cpp.service.insights.util.JsonUtil;
import uk.gov.moj.cpp.service.insights.util.StringListSorter;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class VisualizationGenerator implements IVisualizationGenerator {
    public static final String STRING_SEPARATOR = "~~";
    private static final Set<String> AUDIT_COLUMNS = Set.of(
            "created_at",
            "created_by",
            "payload",
            "last_modified_ts",
            "last_updated_ts",
            "updated_by",
            "deleted_at",
            "deleted_by"
            // Add any other audit columns as needed
    );

    @Override
    public void generateHTMLVisualization(Map<String, Table> tables, String filePath, String serviceName) throws IOException {
        // Sort table names alphabetically
        List<String> sortedTableNames = new ArrayList<>(tables.keySet());
        Collections.sort(sortedTableNames, String.CASE_INSENSITIVE_ORDER);

        // Assign unique node IDs based on sorted order
        Map<String, String> nodeIds = assignNodeIds(tables, sortedTableNames);

        try (Writer writer = new FileWriter(filePath)) {
            writeHTMLHead(writer, serviceName);
            writeHTMLBody(writer, tables, nodeIds, sortedTableNames, serviceName);
            writeHTMLScripts(writer, tables, nodeIds, sortedTableNames);
            writeHTMLEnd(writer);
        }

    }

    private Map<String, String> assignNodeIds(Map<String, Table> tables, List<String> sortedTableNames) {
        Map<String, String> nodeIds = new HashMap<>();
        int nodeCount = 0;
        for (String tableName : sortedTableNames) {
            nodeIds.put(tableName, "n" + nodeCount++);
        }
        return nodeIds;
    }

    private void writeHTMLHead(Writer writer, String serviceName) throws IOException {
        String htmlHead = """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                        <meta charset="UTF-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <title>%s Database Schema Visualization</title>
                        <link rel="stylesheet" href="https://unpkg.com/tippy.js@6.3.7/dist/tippy.css" />
                        <style>
                            body {
                                font-family: Arial, sans-serif;
                                margin: 0;
                                padding: 0;
                                display: flex;
                                flex-direction: column;
                                height: 100vh;
                            }
                            #header {
                                background-color: #2C3E50;
                                color: white;
                                padding: 15px;
                                text-align: center;
                                font-size: 1.5em;
                            }
                            #controls {
                                background-color: #ECF0F1;
                                padding: 10px 20px;
                                display: flex;
                                align-items: center;
                                justify-content: space-between;
                            }
                            #controls > div {
                                display: flex;
                                align-items: center;
                            }
                            #controls label {
                                margin-right: 15px;
                                font-size: 0.9em;
                            }
                            #searchBox {
                                padding: 5px 10px;
                                font-size: 1em;
                                border: 1px solid #BDC3C7;
                                border-radius: 4px;
                            }
                            #cy {
                                flex-grow: 1;
                                display: block;
                                border-top: 1px solid #bdc3c7;
                            }
                            #legend {
                                background: #F8F9FA;
                                padding: 10px 20px;
                                border-top: 1px solid #bdc3c7;
                                display: flex;
                                justify-content: space-between;
                                align-items: center;
                                flex-wrap: wrap;
                            }
                            #legend ul {
                                list-style: none;
                                padding: 0;
                                margin: 0;
                                display: flex;
                                flex-wrap: wrap;
                            }
                            #legend li {
                                margin-right: 20px;
                                display: flex;
                                align-items: center;
                                font-size: 0.9em;
                            }
                            #legend .legend-item {
                                display: flex;
                                align-items: center;
                            }
                            #legend .legend-color {
                                width: 15px;
                                height: 15px;
                                margin-right: 5px;
                                border: 1px solid #bdc3c7;
                            }
                
                            /* Highlight Styles */
                            .highlighted-node {
                                background-color: #4CAF50; /* Professional Green */
                                border-width: 3px;
                                border-color: #388E3C; /* Dark Green */
                                transition: background-color 0.3s, border-color 0.3s;
                            }
                
                            /* Highlighted Edge Style */
                            .highlighted-edge {
                                line-color: #F57F17; /* Dark Amber */
                                width: 3px;
                                target-arrow-color: #F57F17; /* Dark Amber */
                                transition: line-color 0.3s, width 0.3s, target-arrow-color 0.3s;
                            }
                
                            /* Highlighted Selected Node */
                            .selected-node {
                                background-color: #4CAF50; /* Green */
                                border-width: 3px;
                                border-color: #388E3C; /* Dark Green */
                                transition: background-color 0.3s, border-color 0.3s;
                            }
                
                            /* Tooltip Content */
                            .tooltip-content {
                                max-height: 350px;
                                overflow-y: auto;
                                width: 350px;
                                font-size: 14px;
                                line-height: 1.4;
                                padding-right: 10px;
                            }
                
                            /* Table List Styles */
                            #tableListContainer {
                                background-color: #F8F9FA;
                                border-top: 1px solid #bdc3c7;
                            }
                            #tableListContainer h2 {
                                margin-top: 0;
                            }
                        </style>
                    </head>
                    <body>
                        <div id="header">
                            %s Database Schema Visualization
                        </div>
                        <div id="controls">
                            <div>
                                <label><input type="checkbox" id="toggleForeignKey" checked> Show Foreign Key Relationships</label>
                                <label><input type="checkbox" id="toggleReference" checked> Show Reference Relationships</label>
                            </div>
                            <div>
                                <input type="text" id="searchBox" placeholder="Search for a table..." />
                                <button id="resetHighlight" style="margin-left: 10px; padding: 5px 10px;">Reset Highlights</button>
                            </div>
                        </div>
                        <div id="cy"></div>
                        <div id="legend">
                            <ul>
                                <li><span class="legend-item"><span class="legend-color" style="background-color: #3498DB;"></span> Table Nodes</span></li>
                                <li><span class="legend-item"><span class="legend-color" style="background-color: #E74C3C;"></span> Foreign Key Relationships</span></li>
                                <li><span class="legend-item"><span class="legend-color" style="background-color: #F1C40F; border-style: dashed;"></span> Reference Relationships</span></li>
                                <li><span class="legend-item"><span class="legend-color" style="background-color: #FFEB3B;"></span> Highlighted Nodes</span></li>
                                <li><span class="legend-item"><span class="legend-color" style="background-color: #F57F17;"></span> Highlighted Edges</span></li>
                                <li><span class="legend-item"><span class="legend-color" style="background-color: #4CAF50;"></span> Selected Node</span></li>
                            </ul>
                            <!-- Add the toggle link/button -->
                            <a href="#" id="toggleTableList" style="font-size: 0.9em; color: #2980B9; text-decoration: underline; cursor: pointer;">Show All Tables</a>
                            <a href="#" id="toggleReferenceDetails" style="font-size: 0.9em; color: #2980B9; text-decoration: underline; cursor: pointer;">Show References</a>
                
                        </div>
                """.formatted(serviceName.toUpperCase(), serviceName.toUpperCase());

        writer.write(htmlHead);
    }

    private void writeHTMLBody(Writer writer, Map<String, Table> tables, Map<String, String> nodeIds, List<String> sortedTableNames, String serviceName) throws IOException {
        // Add a hidden div for the table list
        String tableListDiv = """
                    <div id="tableListContainer" style="display: none; padding: 20px;">
                        <div class="tooltip-content">
                            <strong>All Tables</strong></br>
                                %s
                        </div>
                    </div>
                """.formatted(buildTableListHtml(sortedTableNames));

        // Add a hidden div for the reference list
        String referenceListDiv = """
                    <div id="referenceListContainer" style="display: none; padding: 20px;">
                        <div class="tooltip-content">
                            <strong>Table References</strong></br>
                                %s
                        </div>
                    </div>
                """.formatted(buildReferenceListHtml(tables, nodeIds, sortedTableNames));

        writer.write(tableListDiv);
        writer.write(referenceListDiv);
    }

    private String buildReferenceListHtml(Map<String, Table> tables, Map<String, String> nodeIds, List<String> sortedTableNames) {
        StringBuilder sb = new StringBuilder();
        int referenceCount = 1;

        for (String tableName : sortedTableNames) {
            Table table = tables.get(tableName);
            for (ForeignKey fk : table.foreignKeys()) {
                String targetTable = fk.referencedTable();
                String columnName = fk.columnName();

                sb.append(referenceCount++)
                        .append(". ")
                        .append(escapeHtml(tableName)) // table name
                        .append(" - ")
                        .append(escapeHtml(columnName)) // field name
                        .append(" -> ")
                        .append(escapeHtml(targetTable)) // target table name
                        .append("</br>\n");
            }
        }

        return sb.toString();
    }


    private String buildTableListHtml(List<String> sortedTableNames) {
        StringBuilder sb = new StringBuilder();
        int tableNumber = 1;
        for (String tableName : sortedTableNames) {
            sb.append(tableNumber++).append(". ").append(escapeHtml(tableName)).append("</br>\n");
        }
        return sb.toString();
    }

    // Utility method to escape HTML
    private String escapeHtml(String input) {
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private void writeHTMLScripts(Writer writer, Map<String, Table> tables, Map<String, String> nodeIds, List<String> sortedTableNames) throws IOException {
        String scripts = """
                <script src="https://cdn.jsdelivr.net/npm/cytoscape@3.24.0/dist/cytoscape.min.js"></script>
                <script src="https://unpkg.com/cytoscape-dagre@2.3.0/cytoscape-dagre.js"></script>
                <script src="https://cdn.jsdelivr.net/npm/cytoscape-cose-bilkent@4.0.0/cytoscape-cose-bilkent.min.js"></script>
                <script src="https://cdn.jsdelivr.net/npm/@popperjs/core@2.11.8/dist/umd/popper.min.js"></script>
                <script src="https://cdn.jsdelivr.net/npm/cytoscape-popper@1.0.7/cytoscape-popper.min.js"></script>
                <script src="https://unpkg.com/tippy.js@6.3.7/dist/tippy-bundle.umd.min.js"></script>
                <script>
                    window.onload = function() {
                        try {
                            console.log('Initializing Cytoscape.js and Tippy.js...');
                
                            // Register Cytoscape.js extensions
                            cytoscape.use(cytoscapeDagre);
                            cytoscape.use(cytoscapeCoseBilkent);
                            cytoscape.use(cytoscapePopper);
                
                            var cy = cytoscape({
                                container: document.getElementById('cy'),
                                elements: [
                                    %s
                                ],
                                style: [
                                    {
                                        selector: 'node',
                                        style: {
                                            'shape': 'roundrectangle',
                                            'background-color': '#3498DB',
                                            'label': 'data(label)',
                                            'text-valign': 'center',
                                            'color': '#000',
                                            'height': 'label',
                                            'width': 'label',
                                            'font-size': '12px',
                                            'padding': '10px',
                                            'text-wrap': 'wrap',
                                            'text-max-width': '100px',
                                            'border-width': 2,
                                            'border-color': '#2980B9'
                                        }
                                    },
                                    {
                                        selector: 'edge[type="foreignKey"]',
                                        style: {
                                            'width': 2,
                                            'line-color': '#E74C3C',
                                            'target-arrow-color': '#E74C3C',
                                            'target-arrow-shape': 'triangle',
                                            'curve-style': 'bezier',
                                            'label': 'data(label)',
                                            'font-size': '10px',
                                            'color': '#E74C3C',
                                            'text-rotation': 'autorotate',
                                            'text-margin-y': -10
                                        }
                                    },
                                    {
                                        selector: 'edge[type="reference"]',
                                        style: {
                                            'width': 2,
                                            'line-color': '#F1C40F',
                                            'line-style': 'dashed',
                                            'target-arrow-color': '#F1C40F',
                                            'target-arrow-shape': 'triangle',
                                            'curve-style': 'bezier',
                                            'label': 'data(label)',
                                            'font-size': '10px',
                                            'color': '#F1C40F',
                                            'text-rotation': 'autorotate',
                                            'text-margin-y': -10
                                        }
                                    },
                                    {
                                        selector: ':selected',
                                        style: {
                                            'background-color': '#2ECC71',
                                            'line-color': '#2ECC71',
                                            'target-arrow-color': '#2ECC71',
                                            'source-arrow-color': '#2ECC71'
                                        }
                                    },
                                    /* Highlighted Node Style */
                                    {
                                        selector: '.highlighted-node',
                                        style: {
                                            'background-color': '#4CAF50', /* Professional Green */
                                            'border-width': '3px',
                                            'border-color': '#388E3C', /* Dark Green */
                                            'transition-property': 'background-color, border-color',
                                            'transition-duration': '0.3s'
                                        }
                                    },
                                    /* Highlighted Edge Style */
                                    {
                                        selector: '.highlighted-edge',
                                        style: {
                                            'line-color': '#F57F17', /* Dark Amber */
                                            'width': '3px',
                                            'target-arrow-color': '#F57F17',
                                            'transition-property': 'line-color, width, target-arrow-color',
                                            'transition-duration': '0.3s'
                                        }
                                    },
                                    /* Highlighted Selected Node */
                                    {
                                        selector: '.selected-node',
                                        style: {
                                            'background-color': '#4CAF50', /* Green */
                                            'border-width': '3px',
                                            'border-color': '#388E3C', /* Dark Green */
                                            'transition-property': 'background-color, border-color',
                                            'transition-duration': '0.3s'
                                        }
                                    }
                                ],
                                layout: {
                                    name: 'cose-bilkent',
                                    animate: true,
                                    animationDuration: 1000,
                                    fit: true,
                                    padding: 30,
                                    randomize: false
                                }
                            });
                
                            // Initialize tooltips using Tippy.js
                            cy.nodes().forEach(function(node) {
                                var tableName = node.data('label');
                                var columns = node.data('columns'); // Retrieve the columns array
                                var indexes = node.data('indexes'); // Retrieve the indexes array
                
                                // Start constructing the HTML content for the tooltip
                                var tooltipContent = "<div class='tooltip-content'>" +
                                                     "<strong>Table:</strong> " + tableName + "<br/><br/>" +
                                                     "<strong>Columns:</strong><br/>" +
                                                     columns.map(function(col) { return "- " + col; }).join("<br/>");
                
                                // Check if there are any indexes
                                if (indexes.length > 0) {
                                    tooltipContent += "<br/><br/><strong>Indexes:</strong><br/>" +
                                                indexes.map(function(index) { return "- " + index; }).join("<br/>");
                                    }
                
                                // Close the tooltip container
                                tooltipContent += "</div>";
                
                                if (typeof tippy === 'undefined') {
                                    console.error('Tippy.js is not loaded.');
                                    return;
                                }
                
                                var ref = node.popperRef();
                                var dummy = document.createElement('div');
                                dummy.style.display = 'none';
                                document.body.appendChild(dummy);
                                var tip = tippy(dummy, {
                                    getReferenceClientRect: () => ref.getBoundingClientRect(),
                                    content: tooltipContent,
                                    trigger: 'manual',
                                    placement: 'top',
                                    hideOnClick: false,
                                    allowHTML: true,
                                    interactive: true,
                                    arrow: true
                                });
                                node.data('tooltip', tip);
                            });
                                 // New Tippy.js for "Show References"
                             var toggleReferenceDetails = document.getElementById('toggleReferenceDetails');
                             var referenceListContent = document.getElementById('referenceListContainer').innerHTML;
                
                             tippy(toggleReferenceDetails, {
                                 content: referenceListContent,
                                 allowHTML: true,
                                 interactive: true,
                                 trigger: 'click',
                                 placement: 'bottom',
                                 theme: 'light-border',
                                 arrow: true,
                                 onShow(instance) {
                                     toggleReferenceDetails.textContent = 'Hide References';
                                 },
                                 onHide(instance) {
                                     toggleReferenceDetails.textContent = 'Show References';
                                 }
                             });           
                            // Show tooltip on mouseover and hide on mouseout
                            cy.on('mouseover', 'node', function(evt) {
                                var node = evt.target;
                                if (node.data('tooltip')) {
                                    node.data('tooltip').show();
                                }
                            });
                
                            cy.on('mouseout', 'node', function(evt) {
                                var node = evt.target;
                                if (node.data('tooltip')) {
                                    node.data('tooltip').hide();
                                }
                            });
                
                            // Highlight Linked Nodes on Click
                            cy.on('tap', 'node', function(evt){
                                var clickedNode = evt.target;
                
                                // Remove existing highlights
                                cy.elements().removeClass('highlighted-node highlighted-edge selected-node');
                
                                // Highlight the clicked node
                                clickedNode.addClass('selected-node');
                
                                // Get all connected edges
                                var connectedEdges = clickedNode.connectedEdges();
                
                                // Highlight connected edges
                                connectedEdges.addClass('highlighted-edge');
                
                                // Get all connected nodes (neighbors)
                                var connectedNodes = connectedEdges.connectedNodes();
                
                                // Highlight connected nodes
                                connectedNodes.addClass('highlighted-node');
                            });
                
                            // Toggle Foreign Key Relationships
                            document.getElementById('toggleForeignKey').addEventListener('change', function(e) {
                                var show = e.target.checked;
                                cy.edges('[type="foreignKey"]').style('display', show ? 'element' : 'none');
                            });
                
                            // Toggle Reference Relationships
                            document.getElementById('toggleReference').addEventListener('change', function(e) {
                                var show = e.target.checked;
                                cy.edges('[type="reference"]').style('display', show ? 'element' : 'none');
                            });
                
                            // Search Functionality
                            document.getElementById('searchBox').addEventListener('input', function(e) {
                                var query = e.target.value.toLowerCase();
                
                                cy.nodes().forEach(function(node) {
                                    var label = node.data('label').toLowerCase();
                                    var match = label.includes(query) && query !== "";
                                    if (match) {
                                        node.addClass('highlighted-node');
                                    } else {
                                        node.removeClass('highlighted-node');
                                    }
                                });
                            });
                
                            // Reset Highlights Button
                            document.getElementById('resetHighlight').addEventListener('click', function() {
                                cy.elements().removeClass('highlighted-node highlighted-edge selected-node');
                                cy.fit(); // Adjust the view to fit all elements
                            });
                
                             // Initialize Tippy.js Tooltip for "Show All Tables" Link
                            var toggleTableList = document.getElementById('toggleTableList');
                            var tableListContent = document.getElementById('tableListContainer').innerHTML;
                
                            tippy(toggleTableList, {
                                content: tableListContent,
                                allowHTML: true,
                                interactive: true,
                                trigger: 'click',
                                placement: 'bottom',
                                theme: 'light-border',
                                arrow: true,
                                onShow(instance) {
                                    toggleTableList.textContent = 'Hide All Tables';
                                },
                                onHide(instance) {
                                    toggleTableList.textContent = 'Show All Tables';
                                }
                            });
                
                            // Enable zoom and pan
                            cy.userPanningEnabled(true);
                            cy.userZoomingEnabled(true);
                
                            // Handle window resize
                            window.addEventListener('resize', function() {
                                cy.resize();
                                cy.fit();
                            });
                        } catch (error) {
                            console.error('Error initializing Cytoscape:', error);
                        }
                    };
                </script>
                """.formatted(buildElementsJs(tables, nodeIds, sortedTableNames));

        writer.write(scripts);
    }

    private String buildElementsJs(Map<String, Table> tables, Map<String, String> nodeIds, List<String> sortedTableNames) {
        List<String> elements = new ArrayList<>();

        // Nodes with incremental numbering
        int tableNumber = 1;
        for (String tableName : sortedTableNames) {
            Table table = tables.get(tableName);
            String nodeId = nodeIds.get(tableName);
            String nodeLabel = escapeJavaScriptString(table.name());
            final ArrayList<String> columnNames = new ArrayList<>(table.columns().keySet());
            StringListSorter.sortStringsWithIdFirst(columnNames);
            String columnsJson = JsonUtil.toJsonArray(columnNames);
            String indexes = JsonUtil.toJsonArray(table.indexes().stream().map(v -> v.name()).collect(Collectors.toList()));

            String nodeElement = """
                            {
                                data: {
                                    id: '%s',
                                    label: '%s',
                                    columns: %s,
                                    indexes: %s,
                                }
                            }
                    """.formatted(nodeId, nodeLabel, columnsJson, indexes);
            elements.add(nodeElement);
            tableNumber++;
        }

        // Foreign Key Edges
        for (String tableName : sortedTableNames) {
            Table table = tables.get(tableName);
            String sourceId = nodeIds.get(table.name());
            for (ForeignKey fk : table.foreignKeys()) {
                String targetId = nodeIds.get(fk.referencedTable());
                if (targetId != null) {
                    String edgeId = "e_fk_%s_%s_%s".formatted(sourceId, targetId, fk.columnName());
                    String edgeLabel = escapeJavaScriptString(fk.columnName());

                    String edgeElement = """
                                    {
                                        data: {
                                            id: '%s',
                                            source: '%s',
                                            target: '%s',
                                            label: '%s',
                                            type: 'foreignKey'
                                        }
                                    }
                            """.formatted(edgeId, sourceId, targetId, edgeLabel);
                    elements.add(edgeElement);
                }
            }
        }

        // Reference Edges
        List<String> referenceEdges = inferReferenceEdges(tables, nodeIds, sortedTableNames);
        elements.addAll(referenceEdges);

        // Join elements with commas and proper indentation
        return String.join(",\n", elements);
    }

    /**
     * Establish reference relationships based on naming conventions.
     * Only considers columns ending with _id or _key, excluding primary and foreign keys.
     *
     * @param tables           The schema model.
     * @param nodeIds          Mapping from table names to unique node IDs.
     * @param sortedTableNames List of sorted table names.
     * @return List of reference edge elements as strings.
     */
    private List<String> inferReferenceEdges(Map<String, Table> tables, Map<String, String> nodeIds, List<String> sortedTableNames) {
        List<String> elementsList = new ArrayList<>();
        int edgeCount = 0;

        // Preprocess: Create a map for quick table name lookup (case-insensitive)
        Map<String, String> lowerCaseTableMap = tables.keySet().stream()
                .collect(Collectors.toMap(String::toLowerCase, name -> name));

        for (String tableName : sortedTableNames) {
            Table table = tables.get(tableName);
            for (Map.Entry<String, Column> columnEntry : table.columns().entrySet()) {
                final Column columnValue = columnEntry.getValue();
                String columnName = columnValue.name().toLowerCase();

                // Skip audit columns
                if (AUDIT_COLUMNS.contains(columnName)) {
                    continue;
                }

                // Skip primary and foreign key columns
                if (isForeignKeyColumn(columnValue, table)) {
                    continue;
                }

                // Check if column ends with _id or _key
                if (columnName.endsWith("_id") || columnName.endsWith("_key")) {
                    // Remove the suffix to get the base name
                    String baseName = columnName.endsWith("_id") ?
                            columnName.substring(0, columnName.length() - 3) :
                            columnName.substring(0, columnName.length() - 4);

                    String referencedTable = findReferencedTable(baseName, lowerCaseTableMap);

                    if (referencedTable != null) {
                        String sourceId = nodeIds.get(table.name());
                        String targetId = nodeIds.get(referencedTable);

                        if (sourceId != null && targetId != null) {
                            String edgeId = "ref_e" + edgeCount++;
                            String edgeLabel = escapeJavaScriptString(columnValue.name());

                            String edgeElement = """
                                            {
                                                data: {
                                                    id: '%s',
                                                    source: '%s',
                                                    target: '%s',
                                                    label: '%s',
                                                    type: 'reference'
                                                }
                                            }
                                    """.formatted(edgeId, sourceId, targetId, edgeLabel);
                            elementsList.add(edgeElement);
                        }
                    }
                }
            }
        }

        return elementsList;
    }

    /**
     * Determines if a column is part of a foreign key.
     *
     * @param column The column to check.
     * @param table  The table containing the column.
     * @return True if the column is a foreign key, false otherwise.
     */
    private boolean isForeignKeyColumn(Column column, Table table) {
        return table.foreignKeys().stream()
                .anyMatch(fk -> fk.columnName().equalsIgnoreCase(column.name()));
    }

    /**
     * Attempts to find the referenced table based on the base name.
     * First tries exact match, then appends various suffixes and retries.
     *
     * @param baseName          The base name derived from the column name.
     * @param lowerCaseTableMap The map of table names in lowercase to actual names.
     * @return The referenced table name if found, otherwise null.
     */
    private String findReferencedTable(String baseName, Map<String, String> lowerCaseTableMap) {
        // Direct match (case-insensitive)
        String matchedTable = lowerCaseTableMap.get(baseName.toLowerCase());
        if (matchedTable != null) {
            return matchedTable;
        }

        List<String> suffixes = Arrays.asList("_details", "_detail", "details", "detail", "cpp_", "cpp", "ha_");

        // Attempt to find matching table with suffixes
        for (String suffix : suffixes) {
            String nameWithSuffix = baseName + suffix;
            matchedTable = lowerCaseTableMap.get(nameWithSuffix.toLowerCase());
            if (matchedTable != null) {
                return matchedTable;
            }

            nameWithSuffix = suffix + baseName;
            matchedTable = lowerCaseTableMap.get(nameWithSuffix.toLowerCase());
            if (matchedTable != null) {
                return matchedTable;
            }
        }

        // If no match found, return null
        return null;
    }

    private void writeHTMLEnd(Writer writer) throws IOException {
        String htmlEnd = """
                    </body>
                    </html>
                """;
        writer.write(htmlEnd);
    }

    // Utility method to escape JavaScript strings
    private String escapeJavaScriptString(String input) {
        return input.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", " ") // Replace newline characters with spaces
                .replace("\t", " "); // Replace tabs with spaces
    }
}
