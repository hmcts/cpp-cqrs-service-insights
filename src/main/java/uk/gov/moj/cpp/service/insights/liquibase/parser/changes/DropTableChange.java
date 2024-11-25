package uk.gov.moj.cpp.service.insights.liquibase.parser.changes;


import uk.gov.moj.cpp.service.insights.liquibase.model.Table;

import java.util.Map;
import java.util.Objects;

import org.w3c.dom.Element;

public final class DropTableChange implements Change {
    private final Element element;

    public DropTableChange(Element element) {
        this.element = element;
    }

    @Override
    public void apply(Map<String, Table> tables) {
        String tableName = element.getAttribute("tableName").toLowerCase();
        tables.remove(tableName);

        // Remove foreign key references in other tables
        for (Table tbl : tables.values()) {
            tbl.foreignKeys().removeIf(fk -> Objects.equals(fk.referencedTable(), tableName));
        }
    }
}

