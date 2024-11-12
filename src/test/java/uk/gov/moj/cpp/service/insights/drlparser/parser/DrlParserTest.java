package uk.gov.moj.cpp.service.insights.drlparser.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import uk.gov.moj.cpp.service.insights.drlparser.parser.model.ActionGroupMappings;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.apache.maven.plugin.logging.Log;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the DrlParser class.
 */
class DrlParserTest {

    private Log mockLog;
    private JavaClassIndexer mockIndexer;
    private DrlParser drlParser;
    private FileSystem jimfs;

    @BeforeEach
    void setUp() {
        mockLog = mock(Log.class);
        mockIndexer = mock(JavaClassIndexer.class);
        drlParser = new DrlParser(mockLog, Executors.newFixedThreadPool(2));

        // Initialize in-memory file system
        jimfs = Jimfs.newFileSystem(Configuration.unix());
    }

    @AfterEach
    void tearDown() throws IOException, InterruptedException {
        drlParser.shutdownExecutor();
        jimfs.close();
    }

    /**
     * Helper method to create a directory structure with .drl files and necessary Java classes.
     */
    private void setupDrlFiles() throws IOException {
        // Create directories in Jimfs
        Path commandApiDir = jimfs.getPath("/dummy-command/dummy-command-api");
        Path queryApiDir = jimfs.getPath("/dummy-query/dummy-query-api");
        Files.createDirectories(commandApiDir);
        Files.createDirectories(queryApiDir);

        // Define resource paths
        String[] commandDrlResources = {
                "/drl/command-api/rule1-command.drl"
                // Add other command .drl resource paths here
        };

        String[] queryDrlResources = {
                "/drl/query-api/rule2-query.drl"
                // Add other query .drl resource paths here
        };

        String[] javaResourcesCommand = {
                "/java/uk/gov/moj/cpp/DummyRuleConstants.java",
                "/java/uk/gov/moj/cpp/DummyPermissionConstants.java"
                // Add other Java resource paths here if needed
        };
        String[] javaResourcesQuery = {
                "/java/uk/gov/moj/cpp/DummyGroupType.java"
                // Add other Java resource paths here if needed
        };
        // Load and write command .drl files
        for (String resourcePath : commandDrlResources) {
            Path drlFilePath = commandApiDir.resolve(getRelativePath(resourcePath));
            writeResourceToJimfs(resourcePath, drlFilePath);
        }

        // Load and write query .drl files
        for (String resourcePath : queryDrlResources) {
            Path drlFilePath = queryApiDir.resolve(getRelativePath(resourcePath));
            writeResourceToJimfs(resourcePath, drlFilePath);
        }

        // Load and write Java source files if necessary
        for (String resourcePath : javaResourcesCommand) {
            Path javaFilePath = commandApiDir.resolve(getRelativePath(resourcePath));
            writeResourceToJimfs(resourcePath, javaFilePath);
        }
        for (String resourcePath : javaResourcesQuery) {
            Path javaFilePath = queryApiDir.resolve(getRelativePath(resourcePath));
            writeResourceToJimfs(resourcePath, javaFilePath);
        }
    }

    /**
     * Helper method to convert resource path to relative path within Jimfs.
     * For example, "/drl/command-api/rule1-command.drl" becomes "src/main/resources/rules/rule1-command.drl"
     */
    private String getRelativePath(String resourcePath) {
        if (resourcePath.startsWith("/drl/command-api/")) {
            return "src/main/resources/rules/" + Paths.get(resourcePath).getFileName().toString();
        } else if (resourcePath.startsWith("/drl/query-api/")) {
            return "src/main/resources/rules/" + Paths.get(resourcePath).getFileName().toString();
        } else if (resourcePath.startsWith("/java/")) {
            return Paths.get(resourcePath).toString().replaceFirst("/java/", "src/main/java/");
        }
        // Add more mappings as needed
        return Paths.get(resourcePath).getFileName().toString();
    }

    /**
     * Helper method to read a resource file and write its content to Jimfs.
     */
    private void writeResourceToJimfs(String resourcePath, Path destinationPath) throws IOException {
        // Get the resource as an InputStream
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new FileNotFoundException("Resource not found: " + resourcePath);
            }

            // Ensure parent directories exist
            Files.createDirectories(destinationPath.getParent());

            // Write the content to Jimfs
            Files.copy(is, destinationPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Tests parsing with valid DRL files in the correct directories.
     */
    @Test
    void testParseWithValidDRLFiles() throws Exception {
        setupDrlFiles();

        List<Path> paths = List.of(jimfs.getPath("/dummy-command/dummy-command-api"), jimfs.getPath("/dummy-query/dummy-query-api"));
        /*List<Path> paths = Arrays.asList(
                Paths.get("/Users/satishkumar/moj/software/cpp.context.results/results-query/results-query-api"),
                Paths.get("/Users/satishkumar/moj/software/cpp.context.results/results-command/results-command-api")
                // Add other source directories as needed
        );*/
        JavaClassIndexer javaClassIndexer = new JavaClassIndexer();
        javaClassIndexer.buildIndex(paths);

        // Execute parsing
        ActionGroupMappings mappings = drlParser.parse(paths, javaClassIndexer);

        assertDrl(mappings);
    }

    private void assertDrl(final ActionGroupMappings mappings) {
        final Map<String, Set<String>> commandActions = mappings.getCommandActionToGroupsMap();
        // Verify mappings
        assertEquals(21, mappings.size());
        // Verify each command action and its associated groups
        // 1. defence.associate-defence-organisation
        assertTrue(commandActions.containsKey("defence.associate-defence-organisation"), "Missing key: defence.associate-defence-organisation");
        assertEquals(Set.of(
                "Dummy Chambers Admin",
                "Dummy Advocates"
        ), commandActions.get("defence.associate-defence-organisation"));

        // 2. material.add-material
        assertTrue(commandActions.containsKey("material.add-material"), "Missing key: material.add-material");
        assertEquals(Set.of(
                "Dummy CMS",
                "Dummy System Users"
        ), commandActions.get("material.add-material"));

        // 3. results.api.track-results
        assertTrue(commandActions.containsKey("results.api.track-results"), "Missing key: results.api.track-results");
        assertEquals(Set.of(
                "Dummy System Users",
                "Dummy Listing Officers",
                "Dummy Court Clerks",
                "Dummy Legal Advisers",
                "Dummy Court Associate",
                "Dummy Magistrates"
        ), commandActions.get("results.api.track-results"));

        // 4. listing.delete-next-hearings
        assertTrue(commandActions.containsKey("listing.delete-next-hearings"), "Missing key: listing.delete-next-hearings");
        assertEquals(Set.of(
                "Dummy System Users",
                "Dummy Listing Officers",
                "Dummy Crown Court Admin",
                "Dummy Court Administrators",
                "Dummy Legal Advisers",
                "Dummy Court Clerks",
                "Dummy Court Associate"
        ), commandActions.get("listing.delete-next-hearings"));

        // 5. results.command.generate-police-results-for-a-defendant
        assertTrue(commandActions.containsKey("results.command.generate-police-results-for-a-defendant"), "Missing key: results.command.generate-police-results-for-a-defendant");
        assertEquals(Set.of("not found"), commandActions.get("results.command.generate-police-results-for-a-defendant"));

        // 6. results.api.create-results
        assertTrue(commandActions.containsKey("results.api.create-results"), "Missing key: results.api.create-results");
        assertEquals(Set.of("Dummy System Users"), commandActions.get("results.api.create-results"));

        // 7. progression.archive-cotr
        assertTrue(commandActions.containsKey("progression.archive-cotr"), "Missing key: progression.archive-cotr");
        assertEquals(Set.of(
                "Advocates",
                "Permission: {\"dummy-action\":\"dummy-courts-access\",\"dummy-object\":\"DUMMY_COTR\"}"
        ), commandActions.get("progression.archive-cotr"));

        // 8. listing.command.publish-court-lists-for-crown-courts
        assertTrue(commandActions.containsKey("listing.command.publish-court-lists-for-crown-courts"), "Missing key: listing.command.publish-court-lists-for-crown-courts");
        assertEquals(Set.of("System Users"), commandActions.get("listing.command.publish-court-lists-for-crown-courts"));

        // 9. results.add-hearing-result
        assertTrue(commandActions.containsKey("results.add-hearing-result"), "Missing key: results.add-hearing-result");
        assertEquals(Set.of(
                "Dummy System Users",
                "Dummy Listing Officers",
                "Dummy Court Clerks",
                "Dummy Legal Advisers",
                "Dummy Court Associate",
                "Dummy Magistrates"
        ), commandActions.get("results.add-hearing-result"));

        // Verify query actions
        Map<String, Set<String>> queryActions = mappings.getQueryActionToGroupsMap();

        // Verify each query action and its associated groups
        // 1. results.query.informant-register-document-request
        assertTrue(queryActions.containsKey("results.query.informant-register-document-request"), "Missing key: results.query.informant-register-document-request");
        assertEquals(Set.of(
                "Dummy System Users",
                "Dummy Listing Officers"
        ), queryActions.get("results.query.informant-register-document-request"));

        // 2. results.get-defendants-tracking-status
        assertTrue(queryActions.containsKey("results.get-defendants-tracking-status"), "Missing key: results.get-defendants-tracking-status");
        assertEquals(Set.of(
                "Dummy Court Admins",
                "Dummy Judiciary",
                "Dummy Administrators",
                "Dummy Officers",
                "Dummy Advisers",
                "Dummy Associate",
                "Dummy Clerk"
        ), queryActions.get("results.get-defendants-tracking-status"));

// 3. results.get-hearing-details
        assertTrue(queryActions.containsKey("results.get-hearing-details"), "Missing key: results.get-hearing-details");
        assertEquals(Set.of(
                "Dummy Aid Admin",
                "Dummy Care Admin",
                "Dummy Service Admin",
                "Dummy Manager",
                "Dummy Support",
                "Dummy Operator",
                "Dummy Associate",
                "Dummy Clerk"
        ), queryActions.get("results.get-hearing-details"));

// 4. results.get-results-details
        assertTrue(queryActions.containsKey("results.get-results-details"), "Missing key: results.get-results-details");
        assertEquals(Set.of(
                "Dummy System Users",
                "Dummy Service Admin",
                "Dummy Advisers",
                "Dummy Operator",
                "Dummy Aid Admin",
                "Dummy Care Admin",
                "Dummy Admin",
                "Dummy Manager",
                "Dummy Support",
                "Dummy Associate",
                "Dummy Clerk",
                "Dummy Magistrates"
        ), queryActions.get("results.get-results-details"));


        // Verify that non-relevant .drl files are skipped
        assertFalse(commandActions.containsKey("OtherAction"), "Unexpected key found: OtherAction in commandActions");
        assertFalse(queryActions.containsKey("OtherAction"), "Unexpected key found: OtherAction in queryActions");

        // Assuming rule3.drl is skipped
    }

    /**
     * Tests parsing when some DRL files are malformed or missing actions.
     */
    @Test
    void testParseWithMalformedDRLFiles() throws Exception {
        // Create a malformed DRL file
        Path commandApiDir = jimfs.getPath("/drl/command-api");
        Files.createDirectories(commandApiDir);
        Path malformedDrl = commandApiDir.resolve("malformed-command.drl");
        Files.writeString(malformedDrl, "import com.example.Permissions;\n" +
                "rule \"MalformedRule\" when\n" +
                "Action(name == \"MalformedAction\")\n" +
                "eval(userAndGroupProvider.isMemberOfAnyOfTheSuppliedGroups(\"User\")\n" + // Missing closing parenthesis
                "then\n" +
                "end");

        List<Path> paths = List.of(jimfs.getPath("/drl"));

        // Execute parsing
        ActionGroupMappings mappings = drlParser.parse(paths, mockIndexer);

        assertEquals(1, mappings.size());
        assertTrue(!mappings.isEmpty());

    }

    /**
     * Tests parsing with no relevant .drl files.
     */
    @Test
    void testParseWithNoRelevantDRLFiles() throws Exception {
        // Create a directory without any .drl files
        Path emptyDir = jimfs.getPath("/drl/empty-api");
        Files.createDirectories(emptyDir);

        List<Path> paths = List.of(jimfs.getPath("/drl"));

        // Execute parsing
        ActionGroupMappings mappings = drlParser.parse(paths, mockIndexer);

        assertEquals(0, mappings.size());
        assertTrue(mappings.isEmpty());

        // Verify logging
        verify(mockLog).info("Completed parsing DRL files. Total mappings: 0");
    }

    /**
     * Tests parsing with nested relevant directories containing .drl files.
     */
    @Test
    void testParseWithNestedRelevantDirectories() throws Exception {
        // Create nested directories with relevant .drl files
        Path nestedCommandApiDir = jimfs.getPath("/drl/nested/command-api");
        Path nestedQueryApiDir = jimfs.getPath("/drl/nested/query-api");
        Files.createDirectories(nestedCommandApiDir);
        Files.createDirectories(nestedQueryApiDir);

        Path drlFile1 = nestedCommandApiDir.resolve("nestedRule1-command.drl");
        Files.writeString(drlFile1, "import com.example.Permissions;\n" +
                "rule \"NestedCreateUserAction\" when\n" +
                "Action(name == \"NestedCreateUser\")\n" +
                "eval(userAndGroupProvider.isMemberOfAnyOfTheSuppliedGroups(\"Super Admin\"))\n" +
                "then\n" +
                "end");


        List<Path> paths = List.of(jimfs.getPath("/drl"));


        // Execute parsing
        ActionGroupMappings mappings = drlParser.parse(paths, mockIndexer);

        // Verify mappings
        assertEquals(1, mappings.size());

        // Verify nested command actions
        Map<String, Set<String>> commandActions = mappings.getCommandActionToGroupsMap();
        assertTrue(commandActions.containsKey("NestedCreateUser"));
        assertEquals(Set.of("Super Admin"), commandActions.get("NestedCreateUser"));

    }
}
