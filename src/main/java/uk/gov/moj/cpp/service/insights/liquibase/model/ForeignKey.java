package uk.gov.moj.cpp.service.insights.liquibase.model;

public record ForeignKey(
        String columnName,
        String referencedTable,
        String referencedColumn


) {
}

