package uk.gov.moj.cpp.service.insights.liquibase.parser.changes;

import uk.gov.moj.cpp.service.insights.liquibase.model.Column;
import uk.gov.moj.cpp.service.insights.liquibase.model.ForeignKey;
import uk.gov.moj.cpp.service.insights.liquibase.model.Table;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.w3c.dom.Element;

/**
 * Handles the <addColumn> Liquibase change type.
 */
public final class AddColumnChange implements Change {
    private final Element element;

    public AddColumnChange(Element element) {
        this.element = element;
    }

    @Override
    public void apply(Map<String, Table> tables) {
        var tableName = element.getAttribute("tableName").toLowerCase();
        var table = tables.computeIfAbsent(tableName, name ->
                new Table(name, new LinkedHashMap<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>())
        );

        var columnElements = getChildElementsNS(element, "*", "column");
        for (var columnElement : columnElements) {
            var column = parseColumn(columnElement);
            table.columns().put(column.name().toLowerCase(), column);

            if (column.isPrimaryKey()) {
                table.primaryKeys().add(column.name());
            }

            // Handle Foreign Key if present
            Optional.ofNullable(column.foreignKeyReference())
                    .ifPresent(ref -> {
                        var foreignKey = parseForeignKey(ref, column.name());
                        if (foreignKey != null) {
                            table.foreignKeys().add(foreignKey);
                        }
                    });
        }
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
            return null;
        }

        var idx = foreignKeyRef.indexOf('(');
        var referencedTable = foreignKeyRef.substring(0, idx).toLowerCase();
        var referencedColumn = foreignKeyRef.substring(idx + 1, foreignKeyRef.length() - 1).toLowerCase();

        if (referencedTable.isEmpty() || referencedColumn.isEmpty()) {
            return null;
        }

        return new ForeignKey(columnName, referencedTable, referencedColumn);
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
