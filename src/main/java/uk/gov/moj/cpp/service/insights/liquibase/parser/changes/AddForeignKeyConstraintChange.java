package uk.gov.moj.cpp.service.insights.liquibase.parser.changes;


import uk.gov.moj.cpp.service.insights.liquibase.model.Column;
import uk.gov.moj.cpp.service.insights.liquibase.model.ForeignKey;
import uk.gov.moj.cpp.service.insights.liquibase.model.Table;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import org.w3c.dom.Element;

public final class AddForeignKeyConstraintChange implements Change {
    private final Element element;

    public AddForeignKeyConstraintChange(Element element) {
        this.element = element;
    }

    @Override
    public void apply(Map<String, Table> tables) {
        String baseTableName = element.getAttribute("baseTableName").toLowerCase();
        String baseColumnNames = element.getAttribute("baseColumnNames").toLowerCase();
        String referencedTableName = element.getAttribute("referencedTableName").toLowerCase();
        String referencedColumnNames = element.getAttribute("referencedColumnNames").toLowerCase();

        Table baseTable = tables.get(baseTableName);
        if (baseTable == null) {
            // Handle table not existing
            baseTable = new Table(baseTableName, new LinkedHashMap<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
            tables.put(baseTableName, baseTable);
        }

        String[] baseColumns = baseColumnNames.split(",");
        String[] referencedColumns = referencedColumnNames.split(",");

        for (int i = 0; i < baseColumns.length; i++) {
            String baseColumn = baseColumns[i].trim();
            String referencedColumn = referencedColumns[i].trim();

            ForeignKey fk = new ForeignKey(baseColumn, referencedTableName, referencedColumn);
            baseTable.foreignKeys().add(fk);

            Column column = baseTable.columns().get(baseColumn.toLowerCase());
            if (column != null) {
                column = new Column(
                        column.name(),
                        column.type(),
                        column.isPrimaryKey(),
                        column.isNullable(),
                        column.defaultValue(),
                        referencedTableName + "(" + referencedColumn + ")"
                );
                baseTable.columns().put(baseColumn.toLowerCase(), column);
            } else {
                // Column might not exist yet; create it
                column = new Column(baseColumn, "UNKNOWN", false, true, null, referencedTableName + "(" + referencedColumn + ")");
                baseTable.columns().put(baseColumn.toLowerCase(), column);
            }
        }
    }
}
