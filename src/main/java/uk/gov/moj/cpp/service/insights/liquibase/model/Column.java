package uk.gov.moj.cpp.service.insights.liquibase.model;


public record Column(
        String name,
        String type,
        boolean isPrimaryKey,
        boolean isNullable,
        String defaultValue,
        String foreignKeyReference // Format: "table(column)"
) {
}

