package uk.gov.moj.cpp.service.insights.liquibase.model;

import java.util.List;

public record Index(
        String name,
        List<String> columns,
        boolean unique
) {
}

