package uk.gov.moj.cpp.service.insights.liquibase.model;

import java.io.File;
import java.util.List;
import java.util.Map;

public record Service(
        String name,
        String liquibaseDir,
        List<File> xmlFiles,
        Map<String, Table> tables
) {
}

