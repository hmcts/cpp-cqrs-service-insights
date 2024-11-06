package uk.gov.moj.cpp.service.insights.liquibase.parser.changes;

import uk.gov.moj.cpp.service.insights.liquibase.model.Table;

import java.util.Map;

import org.w3c.dom.Element;

public final class DropIndexChange implements Change {
    private final Element element;

    public DropIndexChange(Element element) {
        this.element = element;
    }

    @Override
    public void apply(Map<String, Table> tables) {
        String tableName = element.getAttribute("tableName").toLowerCase();
        String indexName = element.getAttribute("indexName");

        if (tableName.isEmpty() || indexName.isEmpty()) {
            System.out.println("Warning: Missing tableName or indexName in dropIndex change.");
            return;
        }

        Table table = tables.get(tableName);
        if (table == null) {
            System.out.println("Warning: Table '" + tableName + "' not found for dropping index '" + indexName + "'.");
            return;
        }

        boolean removed = table.indexes().removeIf(index -> index.name().equalsIgnoreCase(indexName));

        if (removed) {
            System.out.println("Dropped index '" + indexName + "' from table '" + tableName + "'.");
        } else {
            System.out.println("Warning: Index '" + indexName + "' not found in table '" + tableName + "'.");
        }
    }
}
