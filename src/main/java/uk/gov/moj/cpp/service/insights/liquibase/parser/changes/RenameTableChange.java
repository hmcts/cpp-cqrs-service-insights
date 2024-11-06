package uk.gov.moj.cpp.service.insights.liquibase.parser.changes;

import uk.gov.moj.cpp.service.insights.liquibase.model.ForeignKey;
import uk.gov.moj.cpp.service.insights.liquibase.model.Table;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

import org.w3c.dom.Element;

public final class RenameTableChange implements Change {
    private final Element element;

    public RenameTableChange(Element element) {
        this.element = element;
    }

    @Override
    public void apply(Map<String, Table> tables) {
        String oldTableName = element.getAttribute("oldTableName").toLowerCase();
        String newTableName = element.getAttribute("newTableName").toLowerCase();

        // Rename the table in the tables map
        Table table = tables.remove(oldTableName);
        if (table != null) {
            Table renamedTable = new Table(newTableName, table.columns(), table.primaryKeys(), table.foreignKeys(), table.indexes());
            tables.put(newTableName, renamedTable);

            // Update foreign key references in other tables
            for (Table tbl : tables.values()) {
                // Use removeIf with Objects.equals to safely handle nulls
                tbl.foreignKeys().removeIf(fk -> Objects.equals(fk.referencedTable(), oldTableName));

                // Optionally, if you need to update the referenced table name in foreign keys:
                // Iterate over a copy to avoid ConcurrentModificationException
                for (ForeignKey fk : new ArrayList<>(tbl.foreignKeys())) {
                    if (Objects.equals(fk.referencedTable(), oldTableName)) {
                        ForeignKey updatedFk = new ForeignKey(fk.columnName(), newTableName, fk.referencedColumn());
                        tbl.foreignKeys().remove(fk);
                        tbl.foreignKeys().add(updatedFk);
                    }
                }
            }
        } else {
            System.out.println("Warning: Table to rename not found: " + oldTableName);
        }
    }
}

