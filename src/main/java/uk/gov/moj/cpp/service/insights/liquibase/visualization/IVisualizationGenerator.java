package uk.gov.moj.cpp.service.insights.liquibase.visualization;

import uk.gov.moj.cpp.service.insights.liquibase.model.Table;

import java.io.IOException;
import java.util.Map;

public interface IVisualizationGenerator {
    void generateHTMLVisualization(Map<String, Table> tables, String filePath, String serviceName) throws IOException;
}

