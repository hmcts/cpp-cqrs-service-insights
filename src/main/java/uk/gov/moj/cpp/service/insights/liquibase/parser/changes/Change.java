package uk.gov.moj.cpp.service.insights.liquibase.parser.changes;


import uk.gov.moj.cpp.service.insights.liquibase.model.Table;

import java.util.Map;

public sealed interface Change permits CreateTableChange, AddColumnChange, AddPrimaryKeyChange,
        AddForeignKeyConstraintChange, RenameTableChange, RenameColumnChange, DropTableChange, CreateIndexChange, DropIndexChange, DropColumnChange {

    void apply(Map<String, Table> tables);
}


