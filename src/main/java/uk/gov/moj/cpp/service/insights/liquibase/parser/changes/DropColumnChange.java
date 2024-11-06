package uk.gov.moj.cpp.service.insights.liquibase.parser.changes;

import uk.gov.moj.cpp.service.insights.liquibase.model.Column;
import uk.gov.moj.cpp.service.insights.liquibase.model.ForeignKey;
import uk.gov.moj.cpp.service.insights.liquibase.model.Index;
import uk.gov.moj.cpp.service.insights.liquibase.model.Table;

import java.util.ArrayList;
import java.util.Map;

import org.w3c.dom.Element;

public final class DropColumnChange implements Change {
    private final Element element;

    public DropColumnChange(Element element) {
        this.element = element;
    }

    @Override
    public void apply(Map<String, Table> tables) {
        String tableName = element.getAttribute("tableName").toLowerCase();
        String columnName = element.getAttribute("columnName").toLowerCase();

        if (tableName.isEmpty() || columnName.isEmpty()) {
            System.out.println("Warning: Missing tableName or columnName in dropColumn change.");
            return;
        }

        Table table = tables.get(tableName);
        if (table == null) {
            System.out.println("Warning: Table '" + tableName + "' not found for dropping column '" + columnName + "'.");
            return;
        }

        // Remove the column from the table
        Column removedColumn = table.columns().remove(columnName);
        if (removedColumn == null) {
            System.out.println("Warning: Column '" + columnName + "' not found in table '" + tableName + "'.");
            return;
        }
        System.out.println("Dropped column '" + columnName + "' from table '" + tableName + "'.");

        // Remove primary key if the dropped column was part of it
        if (removedColumn.isPrimaryKey()) {
            table.primaryKeys().remove(columnName);
            System.out.println("Removed primary key '" + columnName + "' from table '" + tableName + "'.");
        }

        // Remove foreign keys that reference the dropped column
        ArrayList<ForeignKey> foreignKeysToRemove = new ArrayList<>();
        for (ForeignKey fk : table.foreignKeys()) {
            if (fk.columnName().equals(columnName)) {
                foreignKeysToRemove.add(fk);
            }
        }
        if (!foreignKeysToRemove.isEmpty()) {
            table.foreignKeys().removeAll(foreignKeysToRemove);
            for (ForeignKey fk : foreignKeysToRemove) {
                System.out.println("Removed foreign key '" + fk.columnName() + "' from table '" + tableName + "'.");
            }
        }

        // Remove indexes that include the dropped column
        ArrayList<Index> indexesToRemove = new ArrayList<>();
        for (Index index : table.indexes()) {
            if (index.columns().contains(columnName)) {
                indexesToRemove.add(index);
            }
        }
        if (!indexesToRemove.isEmpty()) {
            table.indexes().removeAll(indexesToRemove);
            for (Index index : indexesToRemove) {
                System.out.println("Removed index '" + index.name() + "' from table '" + tableName + "' due to dropped column '" + columnName + "'.");
            }
        }
    }
}
