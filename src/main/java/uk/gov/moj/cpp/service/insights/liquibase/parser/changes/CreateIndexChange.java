package uk.gov.moj.cpp.service.insights.liquibase.parser.changes;

import uk.gov.moj.cpp.service.insights.liquibase.model.Index;
import uk.gov.moj.cpp.service.insights.liquibase.model.Table;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public final class CreateIndexChange implements Change {
    private final Element element;

    public CreateIndexChange(Element element) {
        this.element = element;
    }

    @Override
    public void apply(Map<String, Table> tables) {
        String tableName = element.getAttribute("tableName").toLowerCase();
        String indexName = element.getAttribute("indexName");
        boolean unique = Boolean.parseBoolean(element.getAttribute("unique"));

        Table table = tables.get(tableName);
        if (table == null) {
            // Handle the case where the table does not exist
            table = new Table(
                    tableName,
                    new LinkedHashMap<>(),
                    new ArrayList<>(),
                    new ArrayList<>(),
                    new ArrayList<>()
            );
            tables.put(tableName, table);
            System.out.println("Warning: Table '" + tableName + "' not found. Created a new entry.");
        }

        NodeList columnNodes = element.getElementsByTagNameNS("*", "column");
        List<String> columns = new ArrayList<>();
        for (int i = 0; i < columnNodes.getLength(); i++) {
            Element columnElement = (Element) columnNodes.item(i);
            String columnName = columnElement.getAttribute("name").toLowerCase();
            columns.add(columnName);
        }

        Index index = new Index(indexName, columns, unique);
        table.indexes().add(index);
        System.out.println("Added index '" + indexName + "' to table '" + tableName + "'.");
    }
}

