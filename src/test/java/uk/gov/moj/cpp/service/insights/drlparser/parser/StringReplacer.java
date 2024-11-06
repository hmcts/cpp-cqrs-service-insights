package uk.gov.moj.cpp.service.insights.drlparser.parser;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * A Java program to automate the replacement of assert strings and group names
 * with auto-generated dummy strings across Java files within a specified directory.
 */
public class StringReplacer {

    // Mapping from original strings to dummy strings
    private final Map<String, String> stringMapping = new HashMap<>();

    // Pattern to identify test files (e.g., *Test.java)
    private static final Pattern TEST_FILE_PATTERN = Pattern.compile(".*Test\\.java$");

    // Random generator for dummy string generation
    private static final Random RANDOM = new Random();

    // Maximum recursion depth to prevent stack overflow
    private static final int MAX_RECURSION_DEPTH = 10;

    /**
     * Entry point of the program.
     *
     * @param args Command-line arguments.
     *             args[0] - Root directory path.
     *             args[1] - (Optional) Specific test file name.
     */
    public static void main(String[] args) {
        String rootDirPath = "/Users/satishkumar/moj/all/com.moj.cpp/cnp-module-application-insights/src/test";
        String specificTestFileName = "DrlParserTest.java";

        StringReplacer replacer = new StringReplacer();
        try {
            replacer.process(rootDirPath, specificTestFileName);
            System.out.println("\nString replacement completed successfully.");
        } catch (IOException e) {
            System.err.println("An error occurred during processing: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Main processing method.
     *
     * @param rootDirPath           The root directory to process.
     * @param specificTestFileName  (Optional) Specific test file to target.
     * @throws IOException If an I/O error occurs.
     */
    public void process(String rootDirPath, String specificTestFileName) throws IOException {
        Path rootPath = Paths.get(rootDirPath);
        if (!Files.isDirectory(rootPath)) {
            throw new IllegalArgumentException("The provided root path is not a directory.");
        }

        // Phase 1: Extraction - Build the string mapping from test files
        if (specificTestFileName != null) {
            // Target a specific test file
            Path testFilePath = findSpecificTestFile(rootPath, specificTestFileName);
            if (testFilePath != null) {
                extractStringsFromTestFile(testFilePath, 0);
            } else {
                System.err.println("Specific test file dummy_0E305B: " + specificTestFileName);
            }
        } else {
            // Target all test files matching the pattern
            extractStringsFromAllTestFiles(rootPath);
        }

        // Phase 2: Replacement - Replace strings across all files
        replaceStringsInAllFiles(rootPath);
    }

    /**
     * Finds a specific test file within the root directory.
     *
     * @param rootPath             The root directory.
     * @param specificTestFileName The specific test file name to find.
     * @return The Path to the test file, or null if dummy_0E305B.
     * @throws IOException If an I/O error occurs.
     */
    private Path findSpecificTestFile(Path rootPath, String specificTestFileName) throws IOException {
        try (Stream<Path> paths = Files.walk(rootPath)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals(specificTestFileName))
                    .findFirst()
                    .orElse(null);
        }
    }

    /**
     * Extracts strings from all test files in the directory.
     *
     * @param rootPath The root directory.
     * @throws IOException If an I/O error occurs.
     */
    private void extractStringsFromAllTestFiles(Path rootPath) throws IOException {
        try (Stream<Path> paths = Files.walk(rootPath)) {
            paths
                    .filter(Files::isRegularFile)
                    .filter(path -> TEST_FILE_PATTERN.matcher(path.getFileName().toString()).matches())
                    .forEach(this::extractStringsFromTestFileSafe);
        }
    }

    /**
     * Safely extracts strings from a test file, handling exceptions.
     *
     * @param testFilePath The path to the test file.
     */
    private void extractStringsFromTestFileSafe(Path testFilePath) {
        try {
            extractStringsFromTestFile(testFilePath, 0);
        } catch (IOException e) {
            System.err.println("Failed to extract strings from file: " + testFilePath);
            e.printStackTrace();
        }
    }

    /**
     * Extracts strings from a single test file and updates the mapping.
     *
     * @param testFilePath The path to the test file.
     * @param currentDepth The current recursion depth.
     * @throws IOException If an I/O error occurs.
     */
    private void extractStringsFromTestFile(Path testFilePath, int currentDepth) throws IOException {
        if (currentDepth > MAX_RECURSION_DEPTH) {
            System.err.println("Maximum recursion depth reached while processing file: " + testFilePath);
            return;
        }

        CompilationUnit cu;
        try {
            cu = StaticJavaParser.parse(testFilePath);
        } catch (Exception e) {
            System.err.println("Failed to parse file: " + testFilePath);
            e.printStackTrace();
            return;
        }

        // Visit method calls to assert methods and extract string literals
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(MethodCallExpr methodCall, Void arg) {
                try {
                    super.visit(methodCall, arg);

                    String methodName = methodCall.getNameAsString();
                    if (isAssertMethod(methodName)) {
                        // Extract string literals from arguments and nested method calls
                        for (var argument : methodCall.getArguments()) {
                            if (argument.isStringLiteralExpr()) {
                                String originalString = argument.asStringLiteralExpr().asString();
                                if (!stringMapping.containsKey(originalString)) {
                                    String dummyString = generateDummyString(originalString);
                                    stringMapping.put(originalString, dummyString);
                                    System.out.println("Mapping: \"" + originalString + "\" -> \"" + dummyString + "\"");
                                }
                            } else if (argument.isMethodCallExpr()) {
                                // Traverse nested method calls to extract string literals
                                extractStringsFromMethodCall(argument.asMethodCallExpr(), currentDepth + 1);
                            }
                            // Handle other expression types if necessary
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error processing method call in file: " + testFilePath);
                    e.printStackTrace();
                }
            }
        }, null);
    }

    /**
     * Recursively extracts strings from a MethodCallExpr.
     *
     * @param methodCall The MethodCallExpr to extract strings from.
     * @param currentDepth The current recursion depth.
     */
    private void extractStringsFromMethodCall(MethodCallExpr methodCall, int currentDepth) {
        if (currentDepth > MAX_RECURSION_DEPTH) {
            System.err.println("Maximum recursion depth reached while processing method call: " + methodCall);
            return;
        }

        try {
            methodCall.accept(new VoidVisitorAdapter<Void>() {
                @Override
                public void visit(StringLiteralExpr stringLiteral, Void arg) {
                    super.visit(stringLiteral, arg);
                    String originalString = stringLiteral.asString();
                    if (!stringMapping.containsKey(originalString)) {
                        String dummyString = generateDummyString(originalString);
                        stringMapping.put(originalString, dummyString);
                        System.out.println("Mapping: \"" + originalString + "\" -> \"" + dummyString + "\"");
                    }
                }

                @Override
                public void visit(MethodCallExpr innerMethodCall, Void arg) {
                    super.visit(innerMethodCall, arg);
                    // Recursively handle nested method calls
                    extractStringsFromMethodCall(innerMethodCall, currentDepth + 1);
                }
            }, null);
        } catch (Exception e) {
            System.err.println("Error extracting strings from method call: " + methodCall);
            e.printStackTrace();
        }
    }

    /**
     * Determines if a method name corresponds to an assert method.
     *
     * @param methodName The method name.
     * @return True if it's an assert method, false otherwise.
     */
    private boolean isAssertMethod(String methodName) {
        // Common JUnit assert methods
        return methodName.startsWith("assert");
    }

    /**
     * Generates a unique dummy string based on the original string.
     *
     * @param original The original string.
     * @return A unique dummy string.
     */
    private String generateDummyString(String original) {
        // Example: prefix with "dummy_" and append a unique identifier
        String prefix = "dummy_";
        String uniqueId = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        return prefix + uniqueId;
    }

    /**
     * Replaces all occurrences of the original strings with dummy strings across all files.
     *
     * @param rootPath The root directory.
     * @throws IOException If an I/O error occurs.
     */
    private void replaceStringsInAllFiles(Path rootPath) throws IOException {
        if (stringMapping.isEmpty()) {
            System.out.println("No strings found to replace.");
            return;
        }

        System.out.println("\nStarting replacement of strings across all files...");

        try (Stream<Path> paths = Files.walk(rootPath)) {
            paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(this::replaceStringsInFileSafe);
        }
    }

    /**
     * Safely replaces strings in a file, handling exceptions.
     *
     * @param filePath The path to the file.
     */
    private void replaceStringsInFileSafe(Path filePath) {
        try {
            replaceStringsInFile(filePath);
        } catch (IOException e) {
            System.err.println("Failed to replace strings in file: " + filePath);
            e.printStackTrace();
        }
    }

    /**
     * Replaces all occurrences of the original strings with dummy strings in a single file.
     *
     * @param filePath The path to the file.
     * @throws IOException If an I/O error occurs.
     */
    private void replaceStringsInFile(Path filePath) throws IOException {
        String content = Files.readString(filePath, StandardCharsets.UTF_8);
        String modifiedContent = content;

        for (Map.Entry<String, String> entry : stringMapping.entrySet()) {
            String original = entry.getKey();
            String dummy = entry.getValue();
            if(!original.startsWith("Missing") && !original.startsWith("Unexp") && !original.startsWith("Permission")) {
                // Escape original string for regex
                String escapedOriginal = Pattern.quote(original);

                // Replace all occurrences
                modifiedContent = modifiedContent.replaceAll(escapedOriginal, dummy);
            }
        }

        // Write back only if changes were made
        if (!modifiedContent.equals(content)) {
            Files.writeString(filePath, modifiedContent, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
            System.out.println("Replaced strings in file: " + filePath);
        }
    }
}
