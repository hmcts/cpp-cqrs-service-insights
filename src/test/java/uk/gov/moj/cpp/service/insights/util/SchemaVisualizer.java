package uk.gov.moj.cpp.service.insights.util;


import uk.gov.moj.cpp.service.insights.liquibase.collector.FileCollector;
import uk.gov.moj.cpp.service.insights.liquibase.collector.IServiceCollector;
import uk.gov.moj.cpp.service.insights.liquibase.collector.ServiceCollector;
import uk.gov.moj.cpp.service.insights.liquibase.model.Service;
import uk.gov.moj.cpp.service.insights.liquibase.model.Table;
import uk.gov.moj.cpp.service.insights.liquibase.parser.ChangeLogParser;
import uk.gov.moj.cpp.service.insights.liquibase.parser.IChangeLogParser;
import uk.gov.moj.cpp.service.insights.liquibase.visualization.IVisualizationGenerator;
import uk.gov.moj.cpp.service.insights.liquibase.visualization.VisualizationGenerator;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SchemaVisualizer {

    private final IServiceCollector serviceCollector;
    private final IChangeLogParser parser;
    private final IVisualizationGenerator generator;

    public SchemaVisualizer(IServiceCollector serviceCollector,
                            IChangeLogParser parser,
                            IVisualizationGenerator generator) {
        this.serviceCollector = serviceCollector;
        this.parser = parser;
        this.generator = generator;
    }

    public static void main(String[] args) {

        String changeLogsDir = "/Users/satishkumar/moj/all/";
        String outputFilePath = "/Users/satishkumar/moj/all/er/";

        IServiceCollector serviceCollector = new ServiceCollector();
        IChangeLogParser parser = new ChangeLogParser();
        IVisualizationGenerator generator = new VisualizationGenerator();

        SchemaVisualizer visualizer = new SchemaVisualizer(serviceCollector, parser, generator);
        visualizer.run(changeLogsDir, outputFilePath);
    }

    public void run(String changeLogsDir, String outputFilePath) {
        try {
            List<Service> services = serviceCollector.collectServices(changeLogsDir);

            for (Service service : services) {
                // Collect all change log XML files
                List<File> xmlFiles = FileCollector.collectChangeLogFiles(service.liquibaseDir());
                // Update the service record with xmlFiles
                Service updatedService = new Service(
                        service.name(),
                        service.liquibaseDir(),
                        xmlFiles,
                        service.tables()
                );

                // Build the schema model
                Map<String, Table> tables = new LinkedHashMap<>();
                for (File xmlFile : xmlFiles) {
                    parser.parseChangeLog(xmlFile, tables);
                }
                // Update the service record with tables
                updatedService = new Service(
                        updatedService.name(),
                        updatedService.liquibaseDir(),
                        updatedService.xmlFiles(),
                        tables
                );

                // Generate HTML visualization
                String filePath = outputFilePath + updatedService.name() + ".html";
                if (!tables.isEmpty()) {
                    generator.generateHTMLVisualization(tables, filePath, updatedService.name());
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
