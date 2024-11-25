package uk.gov.moj.cpp.service.insights.liquibase.model;

import java.util.List;
import java.util.Map;

public record Table(
        String name,
        Map<String, Column> columns,
        List<String> primaryKeys,
        List<ForeignKey> foreignKeys,
        List<Index> indexes
) {
}


