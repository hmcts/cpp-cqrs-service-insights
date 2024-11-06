package uk.gov.moj.cpp.service.insights.liquibase.parser.changes;

import uk.gov.moj.cpp.service.insights.liquibase.model.Column;
import uk.gov.moj.cpp.service.insights.liquibase.model.ForeignKey;
import uk.gov.moj.cpp.service.insights.liquibase.model.Table;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Element;

public final class RenameColumnChange implements Change {
    private final Element element;

    public RenameColumnChange(Element element) {
        this.element = element;
    }

    @Override
    public void apply(Map<String, Table> tables) {
        String tableName = element.getAttribute("tableName").toLowerCase();
        String oldColumnName = element.getAttribute("oldColumnName").toLowerCase();
        String newColumnName = element.getAttribute("newColumnName").toLowerCase();

        Table table = tables.get(tableName);
        if (table != null) {
            // Remove the old column
            Column column = table.columns().remove(oldColumnName);
            if (column != null) {
                // Create the renamed column
                Column renamedColumn = new Column(
                        newColumnName,
                        column.type(),
                        column.isPrimaryKey(),
                        column.isNullable(),
                        column.defaultValue(),
                        column.foreignKeyReference()
                );
                table.columns().put(newColumnName.toLowerCase(), renamedColumn);

                // Update primary keys if necessary
                if (renamedColumn.isPrimaryKey()) {
                    table.primaryKeys().remove(oldColumnName);
                    table.primaryKeys().add(newColumnName);
                }

                // Update foreign keys within the same table using Iterator
                Iterator<ForeignKey> fkIterator = table.foreignKeys().iterator();
                List<ForeignKey> updatedFks = new ArrayList<>();

                while (fkIterator.hasNext()) {
                    ForeignKey fk = fkIterator.next();
                    if (fk.columnName().equals(oldColumnName)) {
                        // Remove the old ForeignKey safely
                        fkIterator.remove();

                        // Create the updated ForeignKey
                        ForeignKey updatedFk = new ForeignKey(newColumnName, fk.referencedTable(), fk.referencedColumn());
                        updatedFks.add(updatedFk);
                    }
                }

                // Add all updated ForeignKeys after iteration
                table.foreignKeys().addAll(updatedFks);

                // Update foreign key references in other tables using removeIf
                for (Table tbl : tables.values()) {
                    List<ForeignKey> updatedForeignKeys = new ArrayList<>();

                    tbl.foreignKeys().removeIf(fk -> {
                        if (fk.referencedTable() != null
                                && fk.referencedTable().equals(tableName)
                                && fk.referencedColumn().equals(oldColumnName)) {
                            // Prepare the updated ForeignKey to add later
                            ForeignKey updatedFk = new ForeignKey(fk.columnName(), fk.referencedTable(), newColumnName);
                            updatedForeignKeys.add(updatedFk);
                            return true; // Remove this fk
                        }
                        return false; // Retain this fk
                    });

                    // Add all updated ForeignKeys after removal
                    tbl.foreignKeys().addAll(updatedForeignKeys);
                }
            }
        }
    }

}

