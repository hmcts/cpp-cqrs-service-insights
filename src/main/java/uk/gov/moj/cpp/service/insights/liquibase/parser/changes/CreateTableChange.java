package uk.gov.moj.cpp.service.insights.liquibase.parser.changes;

import uk.gov.moj.cpp.service.insights.liquibase.model.Column;
import uk.gov.moj.cpp.service.insights.liquibase.model.ForeignKey;
import uk.gov.moj.cpp.service.insights.liquibase.model.Index;
import uk.gov.moj.cpp.service.insights.liquibase.model.Table;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.w3c.dom.Element;

/**
 * Handles the <createTable> Liquibase change type.
 */
public final class CreateTableChange implements Change {
    private final Element element;

    public CreateTableChange(Element element) {
        this.element = element;
    }

    @Override
    public void apply(Map<String, Table> tables) {
        var tableName = element.getAttribute("tableName").toLowerCase();
        var columns = new LinkedHashMap<String, Column>();
        var primaryKeys = new ArrayList<String>();
        var foreignKeys = new ArrayList<ForeignKey>();
        var indexes = new ArrayList<Index>(); // Initialize indexes list

        // Parse columns
        var columnElements = getChildElementsNS(element, "*", "column");
        for (var columnElement : columnElements) {
            var column = parseColumn(columnElement);
            columns.put(column.name().toLowerCase(), column);

            if (column.isPrimaryKey()) {
                primaryKeys.add(column.name());
            }

            // Handle Foreign Key if present
            Optional.ofNullable(column.foreignKeyReference())
                    .ifPresent(ref -> {
                        var foreignKey = parseForeignKey(ref, column.name());
                        if (foreignKey != null) {
                            foreignKeys.add(foreignKey);
                        }
                    });
        }

        // Parse index elements within createTable (if any)
        var indexElements = getChildElementsNS(element, "*", "index"); // Adjust tag name as per XML
        for (var indexElement : indexElements) {
            var index = parseIndex(indexElement);
            if (index != null) {
                indexes.add(index);
            }
        }

        // Create the Table instance with indexes
        var table = new Table(tableName, columns, primaryKeys, foreignKeys, indexes);
        tables.put(tableName, table);
    }

    /**
     * Parses a <column> element into a Column object.
     *
     * @param columnElement The <column> XML element.
     * @return The parsed Column object.
     */
    private Column parseColumn(Element columnElement) {
        var name = columnElement.getAttribute("name").toLowerCase();
        var type = columnElement.getAttribute("type");
        boolean isPrimaryKey = false;
        boolean isNullable = true;
        String defaultValue = null;
        String foreignKeyReference = null;

        var constraintsElement = getChildElementNS(columnElement, "*", "constraints");
        if (constraintsElement != null) {
            var nullableAttr = constraintsElement.getAttribute("nullable");
            isNullable = !"false".equalsIgnoreCase(nullableAttr);

            var primaryKeyAttr = constraintsElement.getAttribute("primaryKey");
            isPrimaryKey = "true".equalsIgnoreCase(primaryKeyAttr);

            // Extract foreign key information
            var referencedTableName = constraintsElement.getAttribute("referencedTableName");
            var referencedColumnNames = constraintsElement.getAttribute("referencedColumnNames");
            var references = constraintsElement.getAttribute("references");

            if (!referencedTableName.isEmpty() && !referencedColumnNames.isEmpty()) {
                // Foreign key defined using referencedTableName and referencedColumnNames
                foreignKeyReference = referencedTableName + "(" + referencedColumnNames + ")";
            } else if (!references.isEmpty()) {
                // Foreign key defined using references attribute
                foreignKeyReference = references;
            }
        }

        return new Column(name, type, isPrimaryKey, isNullable, defaultValue, foreignKeyReference);
    }

    /**
     * Parses the foreign key reference string into a ForeignKey object.
     *
     * @param foreignKeyRef The foreign key reference string in format "table(column)".
     * @param columnName    The name of the current column.
     * @return A ForeignKey object if parsing is successful; otherwise, null.
     */
    private ForeignKey parseForeignKey(String foreignKeyRef, String columnName) {
        if (foreignKeyRef == null || !foreignKeyRef.contains("(") || !foreignKeyRef.endsWith(")")) {
            System.out.println("Warning: Invalid or missing foreign key reference format for column '" + columnName + "'. Expected 'table(column)', got: " + foreignKeyRef);
            return null;
        }

        var idx = foreignKeyRef.indexOf('(');
        var referencedTable = foreignKeyRef.substring(0, idx).toLowerCase();
        var referencedColumn = foreignKeyRef.substring(idx + 1, foreignKeyRef.length() - 1).toLowerCase();

        if (referencedTable.isEmpty() || referencedColumn.isEmpty()) {
            System.out.println("Warning: Referenced table or column is empty for foreign key on column '" + columnName + "'.");
            return null;
        }

        return new ForeignKey(columnName, referencedTable, referencedColumn);
    }

    /**
     * Parses an <index> element into an Index object.
     *
     * @param indexElement The <index> XML element.
     * @return The parsed Index object, or null if invalid.
     */
    private Index parseIndex(Element indexElement) {
        var indexName = indexElement.getAttribute("name");
        var unique = Boolean.parseBoolean(indexElement.getAttribute("unique"));

        var columnElements = getChildElementsNS(indexElement, "*", "column");
        var columns = new ArrayList<String>();
        for (var columnElement : columnElements) {
            var columnName = columnElement.getAttribute("name").toLowerCase();
            columns.add(columnName);
        }

        if (!indexName.isEmpty() && !columns.isEmpty()) {
            return new Index(indexName, columns, unique);
        } else {
            System.out.println("Warning: Invalid index definition in table: " + element.getAttribute("tableName"));
            return null;
        }
    }

    /**
     * Retrieves all child elements matching the specified namespace and local name.
     *
     * @param parent       The parent element.
     * @param namespaceURI The namespace URI.
     * @param localName    The local name of the child elements.
     * @return An array of matching child elements.
     */
    private Element[] getChildElementsNS(Element parent, String namespaceURI, String localName) {
        var nodeList = parent.getElementsByTagNameNS(namespaceURI, localName);
        var elements = new Element[nodeList.getLength()];
        for (int i = 0; i < nodeList.getLength(); i++) {
            elements[i] = (Element) nodeList.item(i);
        }
        return elements;
    }

    /**
     * Retrieves the first child element matching the specified namespace and local name.
     *
     * @param parent       The parent element.
     * @param namespaceURI The namespace URI.
     * @param localName    The local name of the child element.
     * @return The first matching child element, or null if none found.
     */
    private Element getChildElementNS(Element parent, String namespaceURI, String localName) {
        var nodeList = parent.getElementsByTagNameNS(namespaceURI, localName);
        return nodeList.getLength() > 0 ? (Element) nodeList.item(0) : null;
    }
}
