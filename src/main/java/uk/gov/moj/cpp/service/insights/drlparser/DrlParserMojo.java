package uk.gov.moj.cpp.service.insights.drlparser;

import uk.gov.moj.cpp.service.insights.drlparser.parser.DrlParser;
import uk.gov.moj.cpp.service.insights.drlparser.parser.JavaClassIndexer;
import uk.gov.moj.cpp.service.insights.drlparser.parser.model.ActionGroupMappings;
import uk.gov.moj.cpp.service.insights.html.CytoscapeHTMLGenerator;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "parse-drl", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class DrlParserMojo extends AbstractMojo {
    // Constants for titles and subtitles
    private static final String EVENT_LIST_TITLE = "Event List";
    private static final String EVENT_LIST_SUBTITLE = "All Events";
    private static final String COMMAND_RULE_LIST_TITLE = "Command Rule List";
    private static final String COMMAND_RULE_LIST_SUBTITLE = "All Rules";
    private static final String QUERY_RULE_LIST_TITLE = "Query Rule List";
    private static final String QUERY_RULE_LIST_SUBTITLE = "All Rules";

    /**
     * List of directories containing DRL files.
     */
    @Parameter(property = "drlDirectories", required = true)
    private List<String> drlDirectories;
    /**
     * The output file for the generated HTML.
     */
    @Parameter(property = "commandRule", defaultValue = "commandRule.html", required = false)
    private String commandRule;
    /**
     * The output file for the generated HTML.
     */
    @Parameter(property = "queryRule", defaultValue = "queryRule.html", required = false)
    private String queryRule;

    public void execute() throws MojoExecutionException {
        try {
            // Convert directory strings to Path objects
            List<Path> paths = drlDirectories.stream()
                    .map(Paths::get)
                    .toList();

            // Initialize JavaClassIndexer (implementation not shown)
            JavaClassIndexer indexer = new JavaClassIndexer();
            indexer.buildIndex(paths);

            // Instantiate DrlParser with Maven's Log
            int numThreads = Runtime.getRuntime().availableProcessors();
            getLog().info("Initializing ExecutorService with " + numThreads + " threads.");
            ExecutorService executor = Executors.newFixedThreadPool(numThreads);
            DrlParser parser = new DrlParser(getLog(), executor);

            // Parse DRL files
            ActionGroupMappings mappings = parser.parse(paths, indexer);

            // Further processing with mappings
            getLog().info("Parsed ActionGroupMappings: " + mappings);

            // Create the target directory for the HTML file if it doesn't exist
            File targetDir = new File("target/html");
            if (!targetDir.exists()) {
                targetDir.mkdirs();
            }

            // Generate the HTML file using CytoscapeHTMLGenerator
            String commandRulePath = new File(targetDir, commandRule).getAbsolutePath();
            getLog().info("Generating Command HTML file at: " + commandRulePath);
            // Generate HTML for command rules
            CytoscapeHTMLGenerator.generateHTMLFile(
                    mappings.getCommandActionToGroupsMap(),
                    commandRulePath,
                    COMMAND_RULE_LIST_TITLE,
                    COMMAND_RULE_LIST_SUBTITLE
            );
            String queryRulePath = new File(targetDir, queryRule).getAbsolutePath();
            getLog().info("Generating Query HTML file at: " + queryRulePath);
            // Generate HTML for query rules
            CytoscapeHTMLGenerator.generateHTMLFile(
                    mappings.getQueryActionToGroupsMap(),
                    queryRulePath,
                    QUERY_RULE_LIST_TITLE,
                    QUERY_RULE_LIST_SUBTITLE
            );

        } catch (Exception e) {
            getLog().error("Failed to parse DRL files.", e);
            throw new MojoExecutionException("DRL parsing failed.", e);
        }
    }
}
