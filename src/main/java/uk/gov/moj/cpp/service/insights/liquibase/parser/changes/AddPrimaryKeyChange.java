package uk.gov.moj.cpp.service.insights.liquibase.parser.changes;

import uk.gov.moj.cpp.service.insights.liquibase.model.Column;
import uk.gov.moj.cpp.service.insights.liquibase.model.Table;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import org.w3c.dom.Element;

public final class AddPrimaryKeyChange implements Change {
    private final Element element;

    public AddPrimaryKeyChange(Element element) {
        this.element = element;
    }

    @Override
    public void apply(Map<String, Table> tables) {
        String tableName = element.getAttribute("tableName").toLowerCase();
        Table table = tables.get(tableName);
        if (table == null) {
            // Handle table not existing
            table = new Table(tableName, new LinkedHashMap<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
            tables.put(tableName, table);
        }

        String constraintName = element.getAttribute("constraintName");
        String columnNames = element.getAttribute("columnNames");
        String[] pkColumns = columnNames.split(",");

        for (String pkColumn : pkColumns) {
            pkColumn = pkColumn.trim().toLowerCase();
            Column column = table.columns().get(pkColumn);
            if (column != null) {
                // Update existing column
                column = new Column(
                        column.name(),
                        column.type(),
                        true,
                        column.isNullable(),
                        column.defaultValue(),
                        column.foreignKeyReference()
                );
                table.columns().put(pkColumn, column);
            } else {
                // Column might not exist yet; create it
                column = new Column(pkColumn, "UNKNOWN", true, true, null, null);
                table.columns().put(pkColumn, column);
            }
            if (!table.primaryKeys().contains(pkColumn)) {
                table.primaryKeys().add(pkColumn);
            }
        }
    }
}

