package uk.gov.moj.cpp.service.insights.liquibase;

import uk.gov.moj.cpp.service.insights.liquibase.collector.FileCollector;
import uk.gov.moj.cpp.service.insights.liquibase.model.Table;
import uk.gov.moj.cpp.service.insights.liquibase.parser.ChangeLogParser;
import uk.gov.moj.cpp.service.insights.liquibase.parser.IChangeLogParser;
import uk.gov.moj.cpp.service.insights.liquibase.visualization.IVisualizationGenerator;
import uk.gov.moj.cpp.service.insights.liquibase.visualization.VisualizationGenerator;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Maven Plugin to visualize Liquibase schemas.
 */
@Mojo(name = "visualize-schema", defaultPhase = LifecyclePhase.GENERATE_RESOURCES, threadSafe = true)
public class SchemaVisualizerMojo extends AbstractMojo {

    /**
     * Directory containing Liquibase change log XML files.
     */
    @Parameter(property = "changeLogsDir", required = true)
    private File changeLogsDir;

    /**
     * Output directory for the schema visualization HTML file.
     */
    @Parameter(property = "outputDir", required = true, defaultValue = "target/html")
    private File outputDir;

    /**
     * Name of the schema visualization file.
     */
    @Parameter(property = "schemaFileName", required = false, defaultValue = "liquibase-schema-visualization.html")
    private String schemaFileName;

    public void execute() throws MojoExecutionException {
        getLog().info("Starting Liquibase Schema Visualization...");

        if (!changeLogsDir.exists() || !changeLogsDir.isDirectory()) {
            throw new MojoExecutionException("The provided changeLogsDir does not exist or is not a directory: " + changeLogsDir);
        }

        if (!outputDir.exists()) {
            if (!outputDir.mkdirs()) {
                throw new MojoExecutionException("Failed to create output directory: " + outputDir);
            }
        }

        IChangeLogParser parser = new ChangeLogParser();
        IVisualizationGenerator generator = new VisualizationGenerator();

        try {
            // Collect all change log XML files in the specified directory
            List<File> xmlFiles = FileCollector.collectChangeLogFiles(changeLogsDir.getAbsolutePath());

            // Build the schema model
            Map<String, Table> tables = new LinkedHashMap<>();
            for (File xmlFile : xmlFiles) {
                parser.parseChangeLog(xmlFile, tables);
            }

            // Generate HTML visualization
            if (!tables.isEmpty()) {
                String filePath = new File(outputDir, schemaFileName).getAbsolutePath();
                generator.generateHTMLVisualization(tables, filePath, "Database Schema");
                getLog().info("Schema visualization generated at: " + filePath);
            } else {
                getLog().warn("No tables found to generate visualization.");
            }

        } catch (Exception e) {
            throw new MojoExecutionException("Error during schema visualization generation", e);
        }

        getLog().info("Liquibase Schema Visualization completed successfully.");
    }
}
