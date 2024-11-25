package uk.gov.moj.cpp.service.insights.drlparser.parser;

import uk.gov.moj.cpp.service.insights.drlparser.parser.model.ActionGroupMappings;
import uk.gov.moj.cpp.service.insights.util.JsonUtil;
import uk.gov.moj.cpp.service.insights.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import org.apache.maven.plugin.logging.Log;

/**
 * Parses DRL files to extract actions and their associated groups/permissions.
 */
public class DrlParser {

    // Static variables extracted from constants
    private static final Pattern IMPORT_PATTERN = Pattern.compile("import(?:\\s+static)?\\s+([^;]+);", Pattern.CASE_INSENSITIVE);
    private static final Pattern RULE_PATTERN = Pattern.compile("(?s)rule\\s+\"([^\"]+)\".*?when(.*?)then.*?end", Pattern.CASE_INSENSITIVE);
    private static final Pattern ACTION_PATTERN = Pattern.compile("Action\\s*\\(\\s*name\\s*==\\s*\"([^\"]+)\"\\s*\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern GROUP_PATTERN = Pattern.compile("\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONDITION_SPLIT_PATTERN = Pattern.compile("\\|\\||\\bor\\b|\\band\\b", Pattern.CASE_INSENSITIVE);

    private static final String COMMAND_API = "command-api";
    private static final String QUERY_API = "query-api";
    private static final String DRL_EXTENSION = ".drl";
    private static final String EVAL_PREFIX = "eval(";
    private static final String ADD_METHOD = "add";
    private static final String IS_MEMBER_OF_ANY_OF_THE_SUPPLIED_GROUPS = "userandgroupprovider.ismemberofanyofthesuppliedgroups";
    private static final String IS_SYSTEM_USER = "userAndGroupProvider.isSystemUser";
    private static final String COMMAND_ACTION_CATEGORY = "-command";
    private static final String QUERY_ACTION_CATEGORY = "-query";
    private static final String SYSTEM_USERS = "System Users";
    private static final String NOT_FOUND = "not found";

    private final Log log;
    private final ExecutorService executor;

    /**
     * Constructs a DrlParser with the provided Maven Log instance and ExecutorService.
     *
     * @param log      Maven's Log interface for logging messages.
     * @param executor ExecutorService for handling concurrent file parsing.
     */
    public DrlParser(Log log, ExecutorService executor) {
        this.log = log;
        this.executor = executor;
    }

    /**
     * Parses DRL files located within the specified list of paths.
     *
     * @param paths   List of directory paths to search for DRL files.
     * @param indexer JavaClassIndexer instance for resolving class information.
     * @return ActionGroupMappings containing the mappings of actions to groups/permissions.
     * @throws IOException          If an I/O error occurs.
     * @throws InterruptedException If the thread is interrupted.
     * @throws ExecutionException   If a task execution fails.
     */
    public ActionGroupMappings parse(List<Path> paths, JavaClassIndexer indexer) throws IOException, InterruptedException, ExecutionException {
        Objects.requireNonNull(paths, "Paths list cannot be null");
        Objects.requireNonNull(indexer, "JavaClassIndexer cannot be null");

        ActionGroupMappings mappings = new ActionGroupMappings();
        List<Future<?>> futures = new ArrayList<>();

        for (Path path : paths) {
            if (!Files.exists(path)) {
                log.warn("Path does not exist and will be skipped: " + path);
                continue;
            }
            if (!Files.isDirectory(path)) {
                log.warn("Path is not a directory and will be skipped: " + path);
                continue;
            }

            log.debug("Walking file tree for path: " + path);
            Files.walkFileTree(path, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (isRelevantFile(file)) {
                        log.debug("Submitting parsing task for file: " + file);
                        futures.add(executor.submit(() -> {
                            try {
                                parseFile(file, mappings, indexer);
                            } catch (IOException e) {
                                log.error("Error parsing file: " + file, e);
                            }
                        }));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }

        // Await task completion
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (ExecutionException ee) {
                log.error("ExecutionException during file parsing.", ee.getCause());
                throw ee;
            } catch (InterruptedException ie) {
                log.error("InterruptedException during file parsing.", ie);
                throw ie;
            }
        }

        log.info("Completed parsing DRL files. Total mappings: " + mappings.size());
        return mappings;
    }

    /**
     * Determines if a file is relevant for parsing based on directory and file extension.
     *
     * @param file Path to the file.
     * @return True if the file should be parsed, false otherwise.
     */
    private boolean isRelevantFile(Path file) {
        Path parentDir = file.getParent();
        return parentDir != null &&
                (parentDir.toString().toLowerCase().contains(COMMAND_API.toLowerCase()) ||
                        parentDir.toString().toLowerCase().contains(QUERY_API.toLowerCase())) &&
                file.toString().toLowerCase().endsWith(DRL_EXTENSION.toLowerCase());
    }

    /**
     * Parses a single DRL file and updates the provided ActionGroupMappings.
     *
     * @param file     Path to the DRL file to parse.
     * @param mappings Instance of ActionGroupMappings to update.
     * @param indexer  JavaClassIndexer instance for resolving class information.
     * @throws IOException If an I/O error occurs during file reading.
     */
    void parseFile(Path file, ActionGroupMappings mappings, JavaClassIndexer indexer) throws IOException {
        log.debug("Parsing DRL file: " + file);
        String content = Files.readString(file, StandardCharsets.UTF_8);

        Set<String> staticImports = extractStaticImports(content);
        log.debug("Extracted " + staticImports.size() + " static imports from file: " + file);

        Matcher ruleMatcher = RULE_PATTERN.matcher(content);

        while (ruleMatcher.find()) {
            String ruleName = ruleMatcher.group(1);
            String whenSection = ruleMatcher.group(2);
            log.debug("Processing rule: " + ruleName);

            Matcher actionMatcher = ACTION_PATTERN.matcher(whenSection);

            if (actionMatcher.find()) {
                String actionName = actionMatcher.group(1);
                Set<String> groupsAndPermissions = ConcurrentHashMap.newKeySet();

                // Extract all eval(...) blocks using the helper method
                List<String> evalBlocks = extractEvalBlocks(whenSection);

                int evalCount = 0;
                for (String evalContent : evalBlocks) {
                    evalCount++;
                    log.debug("Processing eval #" + evalCount + ": " + evalContent);

                    // Split the condition based on logical operators
                    String[] conditions = CONDITION_SPLIT_PATTERN.split(evalContent);
                    for (String condition : conditions) {
                        processCondition(condition.trim(), staticImports, file, indexer, groupsAndPermissions);
                    }
                }

                log.info("Total evals processed in rule \"" + ruleName + "\": " + evalCount);

                if (groupsAndPermissions.isEmpty()) {
                    groupsAndPermissions.add(NOT_FOUND);
                }

                categorizeAction(file, actionName, groupsAndPermissions, mappings);
                log.info("Mapped action: " + actionName + " to groups/permissions: " + groupsAndPermissions);
            } else {
                log.warn("No action found in rule: " + ruleName + " in file: " + file);
            }
        }
    }

    /**
     * Extracts all eval(...) blocks from the given when section.
     *
     * @param whenSection The 'when' section of a DRL rule.
     * @return List of complete eval condition strings.
     */
    private List<String> extractEvalBlocks(String whenSection) {
        List<String> evalBlocks = new ArrayList<>();
        int index = 0;
        while (index < whenSection.length()) {
            int evalStart = whenSection.toLowerCase().indexOf(EVAL_PREFIX.toLowerCase(), index);
            if (evalStart == -1) {
                break;
            }
            int start = evalStart + EVAL_PREFIX.length();
            int parenthesesCount = 1;
            int current = start;
            while (current < whenSection.length() && parenthesesCount > 0) {
                char c = whenSection.charAt(current);
                if (c == '(') {
                    parenthesesCount++;
                } else if (c == ')') {
                    parenthesesCount--;
                }
                current++;
            }
            if (parenthesesCount == 0) {
                String evalContent = whenSection.substring(start, current - 1).trim();
                evalBlocks.add(evalContent);
                index = current;
            } else {
                // Mismatched parentheses
                log.warn("Mismatched parentheses in eval block starting at index " + evalStart);
                break;
            }
        }
        return evalBlocks;
    }

    /**
     * Extracts static imports from the DRL file content.
     *
     * @param content Content of the DRL file.
     * @return Set of static import statements.
     */
    private Set<String> extractStaticImports(String content) {
        Set<String> staticImports = new HashSet<>();
        Matcher staticImportMatcher = IMPORT_PATTERN.matcher(content);
        while (staticImportMatcher.find()) {
            staticImports.add(staticImportMatcher.group(1));
        }
        return staticImports;
    }

    /**
     * Processes a single condition extracted from the EVAL section.
     *
     * @param condition            The condition string.
     * @param staticImports        Set of static imports from the file.
     * @param file                 Path to the DRL file.
     * @param indexer              JavaClassIndexer instance.
     * @param groupsAndPermissions Set to accumulate groups and permissions.
     */
    private void processCondition(String condition, Set<String> staticImports, Path file, JavaClassIndexer indexer, Set<String> groupsAndPermissions) {
        log.debug("Processing condition: " + condition + " in file: " + file);

        // Handle different types of conditions
        handleUnqualifiedMethodCalls(condition, staticImports, file, indexer, groupsAndPermissions);
        handleQualifiedMethodCalls(condition, staticImports, file, indexer, groupsAndPermissions);
        handleUnqualifiedVariableReferences(condition, staticImports, file, indexer, groupsAndPermissions);
        handleQualifiedVariableReferences(condition, staticImports, file, indexer, groupsAndPermissions);
        handleDirectGroupMembership(condition, groupsAndPermissions);
        handleSystemUserCondition(condition, groupsAndPermissions);
    }

    /**
     * Handles unqualified method calls in the condition.
     */
    private void handleUnqualifiedMethodCalls(String condition, Set<String> staticImports, Path file, JavaClassIndexer indexer, Set<String> groupsAndPermissions) {
        Pattern pattern = Pattern.compile("(?<!\\.)\\b(\\w+)\\s*\\(", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(condition);
        while (matcher.find()) {
            String methodName = matcher.group(1);
            log.debug("Found unqualified static method call: " + methodName + " in file: " + file);
            resolveMethodCall(methodName, staticImports, indexer, groupsAndPermissions);
        }
    }

    /**
     * Handles qualified method calls in the condition.
     */
    private void handleQualifiedMethodCalls(String condition, Set<String> staticImports, Path file, JavaClassIndexer indexer, Set<String> groupsAndPermissions) {
        Pattern pattern = Pattern.compile("\\b(\\w+)\\.(\\w+)\\s*\\(", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(condition);
        while (matcher.find()) {
            String className = matcher.group(1);
            String methodName = matcher.group(2);
            log.debug("Found qualified static method call: " + className + "." + methodName + " in file: " + file);
            resolveQualifiedMethodCall(className, methodName, staticImports, indexer, groupsAndPermissions);
        }
    }

    /**
     * Handles unqualified variable references in the condition.
     */
    private void handleUnqualifiedVariableReferences(String condition, Set<String> staticImports, Path file, JavaClassIndexer indexer, Set<String> groupsAndPermissions) {
        Pattern pattern = Pattern.compile("(?<!\\.)\\b(\\w+)\\s*(?!\\()", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(condition);
        while (matcher.find()) {
            String variableName = matcher.group(1);
            int end = matcher.end();
            if (end < condition.length()) {
                char nextChar = condition.charAt(end);
                if (nextChar == '.' || nextChar == '(') {
                    continue;
                }
            }
            log.debug("Found unqualified static variable reference: " + variableName + " in file: " + file);
            resolveVariableReference(variableName, staticImports, indexer, groupsAndPermissions);
        }
    }

    /**
     * Handles qualified variable references in the condition.
     */
    private void handleQualifiedVariableReferences(String condition, Set<String> staticImports, Path file, JavaClassIndexer indexer, Set<String> groupsAndPermissions) {
        Pattern pattern = Pattern.compile("\\b(\\w+)\\.(\\w+)\\b", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(condition);
        while (matcher.find()) {
            String className = matcher.group(1);
            String variableName = matcher.group(2);
            log.debug("Found qualified static variable reference: " + className + "." + variableName + " in file: " + file);
            resolveQualifiedVariableReference(className, variableName, staticImports, indexer, groupsAndPermissions);
        }
    }

    /**
     * Handles direct group membership conditions.
     */
    private void handleDirectGroupMembership(String condition, Set<String> groupsAndPermissions) {
        if (condition.toLowerCase().contains(IS_MEMBER_OF_ANY_OF_THE_SUPPLIED_GROUPS.toLowerCase())) {
            Matcher groupMatcher = GROUP_PATTERN.matcher(condition);
            while (groupMatcher.find()) {
                String groupName = groupMatcher.group(1);
                log.debug("Found group: " + groupName + " in condition: " + condition);
                groupsAndPermissions.add(StringUtils.humanReadable(groupName));
            }
        }
    }

    /**
     * Handles system user conditions.
     */
    private void handleSystemUserCondition(String condition, Set<String> groupsAndPermissions) {
        if (condition.toLowerCase().contains(IS_SYSTEM_USER.toLowerCase())) {
            log.debug("Found condition: isSystemUser");
            groupsAndPermissions.add(SYSTEM_USERS);
        }
    }

    /**
     * Resolves an unqualified method call using static imports.
     */
    private void resolveMethodCall(String methodName, Set<String> staticImports, JavaClassIndexer indexer, Set<String> groupsAndPermissions) {
        Optional<String> methodClassOpt = staticImports.stream()
                .filter(importStr -> importStr.toLowerCase().endsWith("." + methodName.toLowerCase()) || importStr.toLowerCase().endsWith(".*"))
                .findFirst();

        if (methodClassOpt.isPresent()) {
            String methodClass = methodClassOpt.get();
            if (methodClass.endsWith(".*")) {
                methodClass = methodClass.substring(0, methodClass.length() - 2);
            } else {
                methodClass = methodClass.substring(0, methodClass.lastIndexOf('.'));
            }

            resolveStaticMethod(methodClass, methodName, indexer, groupsAndPermissions);
        } else {
            log.warn("No matching static import found for method: " + methodName);
        }
    }

    /**
     * Resolves a qualified method call using static imports.
     */
    private void resolveQualifiedMethodCall(String className, String methodName, Set<String> staticImports, JavaClassIndexer indexer, Set<String> groupsAndPermissions) {
        Optional<String> methodClassOpt = staticImports.stream()
                .filter(importStr -> importStr.toLowerCase().endsWith("." + className.toLowerCase()) || importStr.toLowerCase().endsWith(".*"))
                .findFirst();

        if (methodClassOpt.isPresent()) {
            String methodClass = methodClassOpt.get();
            if (methodClass.endsWith(".*")) {
                methodClass = methodClass.substring(0, methodClass.length() - 2);
            }

            resolveStaticMethod(methodClass, methodName, indexer, groupsAndPermissions);
        } else {
            log.warn("No matching static import found for class: " + className);
        }
    }

    /**
     * Resolves a static method and updates the mappings.
     */
    private void resolveStaticMethod(String className, String methodName, JavaClassIndexer indexer, Set<String> groupsAndPermissions) {
        Optional<JavaClassIndexer.ClassInfo> classInfoOpt = indexer.getClassInfo(className);
        if (classInfoOpt.isPresent()) {
            JavaClassIndexer.ClassInfo classInfo = classInfoOpt.get();
            Optional<MethodDeclaration> methodOpt = classInfo.getMethod(methodName);
            if (methodOpt.isPresent()) {
                MethodDeclaration method = methodOpt.get();
                Set<String> groups = parseStaticMethod(method, classInfo, indexer);
                groupsAndPermissions.addAll(groups);
            } else {
                log.warn("Method not found: " + methodName + " in class " + className);
            }
        } else {
            log.warn("Class not found in index: " + className);
        }
    }

    /**
     * Parses a static method to extract groups and permissions.
     */
    Set<String> parseStaticMethod(MethodDeclaration method, JavaClassIndexer.ClassInfo classInfo, JavaClassIndexer indexer) {
        Set<String> groups = new HashSet<>();
        Map<String, String> keyValueMap = new HashMap<>();
        method.findFirst(com.github.javaparser.ast.stmt.ReturnStmt.class).ifPresent(returnStmt -> {
            // Extract key-value pairs from method calls like add(KEY, VALUE)
            returnStmt.findAll(MethodCallExpr.class).forEach(mce -> {
                String methodName = mce.getNameAsString();
                if (ADD_METHOD.equalsIgnoreCase(methodName)) {
                    // Ensure there are exactly two arguments
                    if (mce.getArguments().size() == 2) {
                        Expression keyExpr = mce.getArgument(0);
                        Expression valueExpr = mce.getArgument(1);

                        String key = StringUtils.extractEnumConstant(keyExpr, classInfo, indexer);
                        String value = StringUtils.extractEnumConstant(valueExpr, classInfo, indexer);

                        if (key != null && value != null) {
                            String readableKey = key;
                            String readableValue = value;

                            keyValueMap.put(readableKey, readableValue);
                        }
                    }
                }
            });
            if (keyValueMap.size() > 0) {
                String jsonResult = JsonUtil.mapToJson(keyValueMap);
                groups.add("Permission: " + jsonResult); // Prefix to identify JSON entry

            } else {
                returnStmt.findAll(com.github.javaparser.ast.expr.MethodCallExpr.class).forEach(mce -> {
                    String methodType = StringUtils.determineMethodType(mce, classInfo);
                    log.debug("Detected method call: " + methodType + " in method: " + method.getNameAsString());

                    mce.getArguments().forEach(arg -> {
                        String enumConstant = StringUtils.extractEnumConstant(arg, classInfo, indexer);
                        if (enumConstant != null) {
                            log.debug("Found enum constant: " + enumConstant + " in method: " + method.getNameAsString());
                            groups.add(StringUtils.humanReadable(enumConstant));
                        }
                    });
                });

                returnStmt.findAll(com.github.javaparser.ast.expr.ArrayCreationExpr.class).forEach(ace -> {
                    log.debug("Detected array creation (new String[]) in method: " + method.getNameAsString());
                    ace.getInitializer().ifPresent(initializer -> {
                        initializer.getValues().forEach(expr -> {
                            String value = StringUtils.extractStringValue(expr, classInfo, indexer);
                            if (value != null) {
                                log.debug("Found array element: " + value + " in method: " + method.getNameAsString());
                                groups.add(StringUtils.humanReadable(value));
                            }
                        });
                    });
                });
            }
        });

        return groups;
    }

    /**
     * Resolves an unqualified variable reference using static imports.
     */
    private void resolveVariableReference(String variableName, Set<String> staticImports, JavaClassIndexer indexer, Set<String> groupsAndPermissions) {
        Optional<String> variableClassOpt = staticImports.stream()
                .filter(importStr -> importStr.toLowerCase().endsWith("." + variableName.toLowerCase()) || importStr.toLowerCase().endsWith(".*"))
                .findFirst();

        if (variableClassOpt.isPresent()) {
            String variableClass = variableClassOpt.get();
            if (variableClass.endsWith(".*")) {
                variableClass = variableClass.substring(0, variableClass.length() - 2);
            } else {
                variableClass = variableClass.substring(0, variableClass.lastIndexOf('.'));
            }

            resolveStaticVariable(variableClass, variableName, indexer, groupsAndPermissions);
        } else {
            log.warn("No matching static import found for variable: " + variableName);
        }
    }

    /**
     * Resolves a qualified variable reference using static imports.
     */
    private void resolveQualifiedVariableReference(String className, String variableName, Set<String> staticImports, JavaClassIndexer indexer, Set<String> groupsAndPermissions) {
        String fullVariableImport = className + "." + variableName;
        boolean isFullyImported = staticImports.stream()
                .anyMatch(importStr -> importStr.equalsIgnoreCase(fullVariableImport));

        if (isFullyImported) {
            String variableClass = staticImports.stream()
                    .filter(importStr -> importStr.equalsIgnoreCase(fullVariableImport))
                    .findFirst()
                    .orElse(className);
            resolveStaticVariable(variableClass, variableName, indexer, groupsAndPermissions);
        } else {
            Optional<String> wildcardImportOpt = staticImports.stream()
                    .filter(importStr -> importStr.toLowerCase().endsWith("." + className.toLowerCase()) || importStr.toLowerCase().endsWith(".*"))
                    .findFirst();
            if (wildcardImportOpt.isPresent()) {
                String variableClass = wildcardImportOpt.get();
                if (variableClass.endsWith(".*")) {
                    variableClass = variableClass.substring(0, variableClass.length() - 2);
                }
                resolveStaticVariable(variableClass, variableName, indexer, groupsAndPermissions);
            } else {
                log.warn("No matching static import found for variable: " + className + "." + variableName);
            }
        }
    }

    /**
     * Resolves a static variable and updates the mappings.
     */
    private void resolveStaticVariable(String className, String variableName, JavaClassIndexer indexer, Set<String> groupsAndPermissions) {
        Optional<JavaClassIndexer.ClassInfo> classInfoOpt = indexer.getClassInfo(className);
        if (classInfoOpt.isPresent()) {
            JavaClassIndexer.ClassInfo classInfo = classInfoOpt.get();
            classInfo.getField(variableName).ifPresent(fieldDecl -> {
                boolean isStatic = fieldDecl.isStatic();
                if (isStatic) {
                    log.debug("Variable " + variableName + " is static.");
                    fieldDecl.getVariable(0).getInitializer().ifPresent(initializer -> {
                        if (initializer.isMethodCallExpr()) {
                            String methodType = StringUtils.determineMethodType(initializer.asMethodCallExpr(), classInfo);
                            log.debug("Detected method call in static variable: " + methodType + " in variable: " + variableName);

                            initializer.asMethodCallExpr().getArguments().forEach(arg -> {
                                String enumConstant = StringUtils.extractEnumConstant(arg, classInfo, indexer);
                                if (enumConstant != null) {
                                    log.debug("Found enum constant: " + enumConstant + " in static variable: " + variableName);
                                    groupsAndPermissions.add(StringUtils.humanReadable(enumConstant));
                                }
                            });
                        } else {
                            String value = initializer.toString().replace("\"", "");
                            log.debug("Static variable " + variableName + " has initializer value: " + value);
                            groupsAndPermissions.add(StringUtils.humanReadable(value));
                        }
                    });
                }
            });
        } else {
            log.warn("Class not found in index: " + className);
        }
    }

    /**
     * Categorizes an action based on the file path and updates the mappings.
     *
     * @param file                 Path to the DRL file.
     * @param actionName           Name of the action.
     * @param groupsAndPermissions Set of groups and permissions associated with the action.
     * @param mappings             ActionGroupMappings instance to update.
     */
    private void categorizeAction(Path file, String actionName, Set<String> groupsAndPermissions, ActionGroupMappings mappings) {
        String filePath = file.toString().toLowerCase();
        if (filePath.contains(COMMAND_ACTION_CATEGORY.toLowerCase())) {
            mappings.addCommandAction(actionName, groupsAndPermissions);
            log.debug("Categorized action: " + actionName + " as Command Action.");
        } else if (filePath.contains(QUERY_ACTION_CATEGORY.toLowerCase())) {
            mappings.addQueryAction(actionName, groupsAndPermissions);
            log.debug("Categorized action: " + actionName + " as Query Action.");
        } else {
            // Default categorization
            mappings.addQueryAction(actionName, groupsAndPermissions);
            log.debug("Categorized action: " + actionName + " as Query Action by default.");
        }
    }

    /**
     * Shuts down the ExecutorService gracefully.
     *
     * @throws InterruptedException If interrupted while waiting.
     */
    public void shutdownExecutor() throws InterruptedException {
        log.debug("Shutting down ExecutorService.");
        executor.shutdown();
        if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
            log.warn("Executor did not terminate within the specified time.");
            List<Runnable> droppedTasks = executor.shutdownNow();
            log.warn("Executor was abruptly shut down. " + droppedTasks.size() + " tasks will not be executed.");
        }
    }
}
