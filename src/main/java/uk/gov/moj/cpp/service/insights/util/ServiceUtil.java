package uk.gov.moj.cpp.service.insights.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import org.apache.maven.plugin.logging.Log;

/**
 * Utility class for managing service-related operations.
 * Provides predefined service names and utility methods to interact with them.
 * This class is immutable and cannot be instantiated.
 */
public final class ServiceUtil {

    /**
     * Immutable set of predefined service names.
     */
    private static final Set<String> SERVICE_NAMES = Set.of(
            "applicationscourtorders.", "archiving.", "assignment.", "audit2dls.", "public",
            "authorisation.", "correspondence.", "defence.", "directionsmanagement.", "documentqueue.",
            "elasticsearch.", "hearing.", "listing.", "material.", "mireportdata.",
            "misystemdata.", "notification.", "notificationnotify.", "platform.", "progression.",
            "prosecutioncasefile.", "referencedata.", "referencedataoffences.", "resulting.", "results.",
            "result.", "scheduling.", "sjp.", "staging.", "stagingbulkscan.", "stagingdarts.", "stagingdvla.",
            "stagingenforcement.", "staginghmi.", "stagingpnldoffences.", "stagingprosecutors.",
            "stagingprosecutorscivil.", "stagingprosecutorsspi.", "stagingpubhub.", "subscriptions.",
            "support.", "systemdocgenerator.", "systemidmapper.", "systemscheduling.", "unifiedsearchquery.",
            "usersgroups.", "workmgmtproxycombo.", "workmanagementproxy.", "cpscasefile.", "cpscasemanagement."
    );

    /**
     * Private constructor to prevent instantiation.
     */
    private ServiceUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated.");
    }

    /**
     * Retrieves an unmodifiable set of all predefined service names.
     *
     * @return Unmodifiable set of service names.
     */
    public static Set<String> getServiceNames() {
        return SERVICE_NAMES;
    }

    /**
     * Checks if the provided service name is among the predefined service names.
     *
     * @param serviceName The service name to validate.
     * @return {@code true} if the service name is valid; {@code false} otherwise.
     * @throws IllegalArgumentException if {@code serviceName} is {@code null} or empty.
     */
    public static boolean isValidService(String serviceName) {
        if (serviceName == null || serviceName.isBlank()) {
            throw new IllegalArgumentException("Service name cannot be null or empty.");
        }
        return SERVICE_NAMES.contains(serviceName);
    }

    /**
     * Retrieves a subset of service names that start with the given prefix.
     *
     * @param prefix The prefix to filter service names.
     * @return Unmodifiable set of service names matching the prefix.
     * @throws IllegalArgumentException if {@code prefix} is {@code null}.
     */
    public static Set<String> getServicesByPrefix(String prefix) {
        if (prefix == null) {
            throw new IllegalArgumentException("Prefix cannot be null.");
        }
        return SERVICE_NAMES.stream()
                .filter(service -> service.startsWith(prefix))
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Retrieves all service names as a list.
     *
     * @return Unmodifiable list of service names.
     */
    public static List<String> getServiceNamesAsList() {
        return List.copyOf(SERVICE_NAMES);
    }

    /**
     * Finds static String variables, classes annotated with @Event, methods annotated with @Handles,
     * and classes implementing the Aggregate interface in Java source files within Maven modules.
     *
     * @param directoryPath The root directory path containing multiple Maven modules.
     * @param log           The Maven plugin logger for logging information and errors.
     * @return A map where the key is the module name (relative path), and the value is a ModuleScanResult object containing VariableInfo, EventInfo, HandlesInfo, and AggregateInfo.
     * @throws IOException if an I/O error occurs while reading the files.
     */
    public static Map<String, ModuleScanResult> scanModules(String directoryPath, Log log) throws IOException {
        Objects.requireNonNull(directoryPath, "Directory path cannot be null.");
        Objects.requireNonNull(log, "Log cannot be null.");

        Path rootPath = Paths.get(directoryPath);
        if (!Files.exists(rootPath) || !Files.isDirectory(rootPath)) {
            throw new IllegalArgumentException("The provided path does not exist or is not a directory.");
        }

        Map<String, ModuleScanResult> result = new ConcurrentHashMap<>();

        try (Stream<Path> paths = Files.walk(rootPath)) {
            paths.filter(Files::isDirectory)
                    .filter(ServiceUtil::isMavenModule)
                    .parallel() // Enable parallel processing for performance
                    .forEach(modulePath -> {
                        String moduleName = rootPath.relativize(modulePath).toString();
                        if (moduleName.isBlank()) {
                            moduleName = modulePath.getFileName() != null ? modulePath.getFileName().toString() : "root";
                            log.warn("Module name is blank after relativization. Assigned name: " + moduleName);
                        }

                        // Define paths to src/main/java and target/generated-sources/annotations
                        Path srcMainJava = modulePath.resolve("src").resolve("main").resolve("java");
                        Path srcTargetGeneratedSource = modulePath.resolve("target").resolve("generated-sources");

                        // Process src/main/java
                        if (Files.exists(srcMainJava) && Files.isDirectory(srcMainJava)) {
                            processJavaFiles(log, moduleName, srcMainJava, result);
                        } else {
                            log.warn("Module " + moduleName + " does not contain src/main/java directory. Skipping.");
                        }

                        // Process target/generated-sources/annotations if exists
                        if (Files.exists(srcTargetGeneratedSource) && Files.isDirectory(srcTargetGeneratedSource) && !moduleName.endsWith("command-handler") && !moduleName.endsWith("event-processor")) {
                            processJavaFiles(log, moduleName, srcTargetGeneratedSource, result);
                        }
                    });
        } catch (IOException e) {
            log.error("Error walking through the root directory: " + e.getMessage(), e);
            throw e; // Re-throw after logging
        }

        return result;
    }

    /**
     * Processes Java files within a specified directory, extracting VariableInfo, EventInfo, HandlesInfo, and AggregateInfo.
     *
     * @param log           The Maven plugin logger.
     * @param moduleName    The name of the current module.
     * @param javaDirectory The directory containing Java source files.
     * @param result        The map to accumulate scan results.
     */
    private static void processJavaFiles(
            final Log log,
            final String moduleName,
            final Path javaDirectory,
            final Map<String, ModuleScanResult> result
    ) {
        log.info("Processing module: " + moduleName);
        List<VariableInfo> variables = Collections.synchronizedList(new ArrayList<>());
        List<EventInfo> events = Collections.synchronizedList(new ArrayList<>());
        List<HandlesInfo> handles = Collections.synchronizedList(new ArrayList<>());
        List<AggregateInfo> aggregates = Collections.synchronizedList(new ArrayList<>()); // New list for AggregateInfo

        try (Stream<Path> javaFiles = Files.walk(javaDirectory)) {
            javaFiles.filter(p -> p.toString().endsWith(".java"))
                    .parallel()
                    .forEach(javaFile -> {
                        try {
                            CompilationUnit compilationUnit = StaticJavaParser.parse(javaFile);

                            // Extract static String variables
                            compilationUnit.findAll(FieldDeclaration.class).stream()
                                    .filter(field -> field.isStatic() && isStringType(field))
                                    .forEach(field -> field.getVariables().forEach(variable -> {
                                        variable.getInitializer()
                                                .filter(Expression::isStringLiteralExpr)
                                                .map(Expression::asStringLiteralExpr)
                                                .map(StringLiteralExpr::getValue)
                                                .filter(value -> SERVICE_NAMES.stream().anyMatch(value::startsWith))
                                                .ifPresent(value -> {
                                                    String className = getFullyQualifiedClassName(javaDirectory, javaFile, compilationUnit);
                                                    variables.add(new VariableInfo(className, variable.getNameAsString(), value));
                                                    log.debug("Found Variable: " + className + "." + variable.getNameAsString() + " = " + value);
                                                });
                                    }));

                            // Extract @Event-annotated classes
                            compilationUnit.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
                                classDecl.getAnnotationByName("Event").ifPresent(annotation -> {
                                    String eventValue = extractEventValue(annotation);
                                    if (eventValue != null && !eventValue.isBlank()) {
                                        String className = getFullyQualifiedClassName(javaDirectory, javaFile, compilationUnit, classDecl);
                                        events.add(new EventInfo(className, eventValue));
                                        log.debug("Found Event: " + className + " with value " + eventValue);
                                    }
                                });
                            });

                            // Extract @Handles-annotated methods
                            compilationUnit.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
                                classDecl.getMethods().forEach(methodDecl -> {
                                    methodDecl.getAnnotationByName("Handles").ifPresent(annotation -> {
                                        String handlesValue = extractHandlesValue(annotation);
                                        if (handlesValue != null && !handlesValue.isBlank()) {
                                            String params = methodDecl.getParameters().stream()
                                                    .map(p -> p.getType().asString())
                                                    .collect(Collectors.joining(","));
                                            String methodName = methodDecl.getNameAsString() + "(" + params + ")";

                                            String className = getFullyQualifiedClassName(javaDirectory, javaFile, compilationUnit, classDecl);
                                            handles.add(new HandlesInfo(className, methodName, handlesValue));
                                            log.debug("Found Handles: Method " + methodName + " handles " + handlesValue);
                                        }
                                    });
                                });
                            });

                            // Extract classes implementing Aggregate interface
                            compilationUnit.findAll(ClassOrInterfaceDeclaration.class).stream()
                                    .filter(classDecl -> classDecl.isClassOrInterfaceDeclaration() && !classDecl.isInterface())
                                    .filter(classDecl -> classDecl.getImplementedTypes().stream()
                                            .anyMatch(type -> type.getNameAsString().equals("Aggregate")))
                                    .forEach(classDecl -> {
                                        String className = getFullyQualifiedClassName(javaDirectory, javaFile, compilationUnit, classDecl);
                                        aggregates.add(new AggregateInfo(className));
                                        log.debug("Found Aggregate: " + className);
                                    });

                        } catch (IOException e) {
                            log.error("Error parsing file: " + javaFile + " - " + e.getMessage(), e);
                        }
                    });
        } catch (IOException e) {
            log.error("Error walking through java directory in module " + moduleName + " - " + e.getMessage(), e);
        }

        if (!variables.isEmpty() || !events.isEmpty() || !handles.isEmpty() || !aggregates.isEmpty()) {
            // Merge existing results if module already exists in the map
            result.merge(moduleName,
                    new ModuleScanResult(
                            List.copyOf(variables),
                            List.copyOf(events),
                            List.copyOf(handles),
                            List.copyOf(aggregates) // Add aggregates to the result
                    ),
                    (existing, newEntry) -> new ModuleScanResult(
                            concatenateLists(existing.variables(), newEntry.variables()),
                            concatenateLists(existing.events(), newEntry.events()),
                            concatenateLists(existing.handles(), newEntry.handles()),
                            concatenateLists(existing.aggregates(), newEntry.aggregates())
                    )
            );
        }
    }

    public static Map<String, ModuleScanResult> resolveHandlesValues(Map<String, ModuleScanResult> moduleScanResults, Log log) {
        Objects.requireNonNull(moduleScanResults, "moduleScanResults cannot be null");
        Objects.requireNonNull(log, "log cannot be null");

        return moduleScanResults.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> {
                            var scanResult = entry.getValue();

                            // Create a map of variable names to their values, handling duplicates
                            var variableMap = scanResult.variables().stream()
                                    .collect(Collectors.toMap(
                                            VariableInfo::variableName,
                                            VariableInfo::variableValue,
                                            (existing, duplicate) -> {
                                                log.warn("Duplicate variable name '" + existing + "'. Using the first occurrence.");
                                                return existing;
                                            }
                                    ));

                            // Resolve handles values
                            var resolvedHandles = scanResult.handles().stream()
                                    .map(handlesInfo -> {
                                        var resolvedValue = variableMap.getOrDefault(handlesInfo.handlesValue(), handlesInfo.handlesValue());
                                        return new HandlesInfo(handlesInfo.className(), handlesInfo.methodName(), resolvedValue);
                                    })
                                    .collect(Collectors.toUnmodifiableList());

                            return new ModuleScanResult(
                                    scanResult.variables(),
                                    scanResult.events(),
                                    resolvedHandles,
                                    scanResult.aggregates()
                            );
                        },
                        (existing, replacement) -> existing // This should not occur as keys are unique
                ));
    }


    /**
     * Concatenates two lists into one.
     *
     * @param <T>   The type of elements in the lists.
     * @param list1 The first list.
     * @param list2 The second list.
     * @return A new list containing all elements from both lists.
     */
    private static <T> List<T> concatenateLists(List<T> list1, List<T> list2) {
        List<T> combined = new ArrayList<>(list1);
        combined.addAll(list2);
        return combined;
    }

    /**
     * Determines if a directory is a Maven module by checking for the presence of a pom.xml file.
     *
     * @param dir The directory to check.
     * @return {@code true} if the directory is a Maven module; {@code false} otherwise.
     */
    private static boolean isMavenModule(Path dir) {
        Path pomFile = dir.resolve("pom.xml");
        return Files.exists(pomFile) && Files.isRegularFile(pomFile);
    }


    /**
     * Checks if the field is of type String.
     *
     * @param field The field declaration to check.
     * @return {@code true} if the field is of type String; {@code false} otherwise.
     */
    private static boolean isStringType(FieldDeclaration field) {
        return field.getElementType().isClassOrInterfaceType() &&
                field.getElementType().asClassOrInterfaceType().getNameAsString().equals("String");
    }

    /**
     * Extracts the event value from the @Event annotation.
     *
     * @param annotation The annotation expression.
     * @return The event value if present; {@code null} otherwise.
     */
    private static String extractEventValue(AnnotationExpr annotation) {
        if (annotation.isSingleMemberAnnotationExpr()) {
            Expression memberValue = annotation.asSingleMemberAnnotationExpr().getMemberValue();
            if (memberValue instanceof StringLiteralExpr stringLiteral) {
                return stringLiteral.getValue();
            }
        } else if (annotation.isNormalAnnotationExpr()) {
            // Handle normal annotation with named parameters if needed
            // Example: @Event(name = "hearing.defendant-added")
            return annotation.asNormalAnnotationExpr().getPairs().stream()
                    .filter(pair -> pair.getNameAsString().equals("value") || pair.getNameAsString().equals("name"))
                    .map(pair -> {
                        Expression valueExpr = pair.getValue();
                        return valueExpr instanceof StringLiteralExpr ? ((StringLiteralExpr) valueExpr).getValue() : null;
                    })
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    /**
     * Extracts the handles value from the @Handles annotation.
     *
     * @param annotation The annotation expression.
     * @return The handles value if present; {@code null} otherwise.
     */
    private static String extractHandlesValue(AnnotationExpr annotation) {
        if (annotation.isSingleMemberAnnotationExpr()) {
            Expression memberValue = annotation.asSingleMemberAnnotationExpr().getMemberValue();
            if (memberValue instanceof StringLiteralExpr stringLiteral) {
                return stringLiteral.getValue();
            } else {
                return memberValue.toString();
            }
        } else if (annotation.isNormalAnnotationExpr()) {
            // Handle normal annotation with named parameters if needed
            // Example: @Handles(name = "hearing.add-case-defendants-for-hearing")
            return annotation.asNormalAnnotationExpr().getPairs().stream()
                    .filter(pair -> pair.getNameAsString().equals("value") || pair.getNameAsString().equals("name"))
                    .map(pair -> {
                        Expression valueExpr = pair.getValue();
                        return valueExpr instanceof StringLiteralExpr ? ((StringLiteralExpr) valueExpr).getValue() : null;
                    })
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    /**
     * Helper method to derive the fully qualified class name from the file path and CompilationUnit.
     *
     * @param javaDirectory   The path to the src/main/java directory.
     * @param javaFile        The path to the Java file.
     * @param compilationUnit The CompilationUnit representing the parsed Java file.
     * @return The fully qualified class name.
     */
    private static String getFullyQualifiedClassName(Path javaDirectory, Path javaFile, CompilationUnit compilationUnit) {
        // Get package name
        String packageName = compilationUnit.getPackageDeclaration()
                .map(pd -> pd.getName().toString())
                .orElse("");

        // Get class name with null check
        String className;
        Path fileNamePath = javaFile.getFileName();
        if (fileNamePath != null) {
            className = fileNamePath.toString().replace(".java", "");
        } else {
            // Log a warning and assign a default class name or skip processing
            className = "UnknownClass";
            // Alternatively, you can choose to skip adding this class
        }

        // Combine package name and class name
        return packageName.isEmpty() ? className : packageName + "." + className;
    }

    /**
     * Overloaded helper method to derive the fully qualified class name, considering nested classes.
     *
     * @param javaDirectory   The path to the src/main/java directory.
     * @param javaFile        The path to the Java file.
     * @param compilationUnit The CompilationUnit representing the parsed Java file.
     * @param classDecl       The ClassOrInterfaceDeclaration representing the class.
     * @return The fully qualified class name.
     */
    private static String getFullyQualifiedClassName(Path javaDirectory, Path javaFile, CompilationUnit compilationUnit, ClassOrInterfaceDeclaration classDecl) {
        // Get package name
        String packageName = compilationUnit.getPackageDeclaration()
                .map(pd -> pd.getName().toString())
                .orElse("");

        // Build class name with nested classes if any
        Deque<String> classNames = new ArrayDeque<>();
        ClassOrInterfaceDeclaration current = classDecl;
        while (current != null) {
            classNames.push(current.getNameAsString());
            current = current.findAncestor(ClassOrInterfaceDeclaration.class).orElse(null);
        }

        String fullClassName = String.join(".", classNames);
        return packageName.isEmpty() ? fullClassName : packageName + "." + fullClassName;
    }

    /**
     * Extracts the service name using regex.
     *
     * @param path The file path from which to extract the service name.
     * @return The extracted service name, or null if not found.
     */
    public static String getServiceNameRegex(String path) {
        Pattern pattern = Pattern.compile("cpp\\.context\\.([^/]+)");
        Matcher matcher = pattern.matcher(path);
        if (matcher.find()) {
            return matcher.group(1).replace(".", "");
        }
        return null;
    }

    public static Set<String> scanForServiceInQuotes(String methodSourceCode) {
        Set<String> namesCalled = new HashSet<>();
        String[] lines = methodSourceCode.split("\n");
        for (String line : lines) {
            int startIndex = 0;
            while ((startIndex = line.indexOf("\"", startIndex)) != -1) {
                int endIndex = line.indexOf("\"", startIndex + 1);
                if (endIndex != -1) {
                    String quotedText = line.substring(startIndex + 1, endIndex);
                    if (!quotedText.contains(" ")) {  // Ensure no spaces in the quoted text
                        SERVICE_NAMES.forEach(s -> {
                            if (quotedText.contains(s)) {
                                namesCalled.add(quotedText);
                            }
                        });
                    }
                    startIndex = endIndex + 1;
                } else {
                    break;
                }
            }
        }
        return namesCalled;
    }

    /**
     * Record to represent information about a static String variable.
     */
    public record VariableInfo(String className, String variableName, String variableValue) {
        /**
         * Constructs a VariableInfo record.
         *
         * @param className     The fully qualified class name where the variable is defined.
         * @param variableName  The name of the variable.
         * @param variableValue The value of the variable.
         */
        public VariableInfo {
            Objects.requireNonNull(className, "className cannot be null");
            Objects.requireNonNull(variableName, "variableName cannot be null");
            Objects.requireNonNull(variableValue, "variableValue cannot be null");
        }
    }

    /**
     * Record to represent information about an Event-annotated class.
     */
    public record EventInfo(String className, String eventValue) {
        /**
         * Constructs an EventInfo record.
         *
         * @param className  The fully qualified class name.
         * @param eventValue The event string from the @Event annotation.
         */
        public EventInfo {
            Objects.requireNonNull(className, "className cannot be null");
            Objects.requireNonNull(eventValue, "eventValue cannot be null");
        }
    }

    /**
     * Record to represent information about a method annotated with @Handles.
     */
    public record HandlesInfo(String className, String methodName, String handlesValue) {
        /**
         * Constructs a HandlesInfo record.
         *
         * @param className    The fully qualified class name.
         * @param methodName   The name of the method.
         * @param handlesValue The value of the @Handles annotation.
         */
        public HandlesInfo {
            Objects.requireNonNull(className, "className cannot be null");
            Objects.requireNonNull(methodName, "methodName cannot be null");
            Objects.requireNonNull(handlesValue, "handlesValue cannot be null");
        }
    }

    /**
     * Record to represent information about a class implementing Aggregate.
     */
    public record AggregateInfo(String className) {
        /**
         * Constructs an AggregateInfo record.
         *
         * @param className The fully qualified class name implementing Aggregate.
         */
        public AggregateInfo {
            Objects.requireNonNull(className, "className cannot be null");
        }
    }

    /**
     * Model to hold VariableInfo, EventInfo, HandlesInfo, and AggregateInfo for a module.
     */
    public record ModuleScanResult(
            List<VariableInfo> variables,
            List<EventInfo> events,
            List<HandlesInfo> handles,
            List<AggregateInfo> aggregates
    ) {
        /**
         * Constructs a ModuleScanResult record.
         *
         * @param variables  The list of VariableInfo objects.
         * @param events     The list of EventInfo objects.
         * @param handles    The list of HandlesInfo objects.
         * @param aggregates The list of AggregateInfo objects.
         */
        public ModuleScanResult {
            Objects.requireNonNull(variables, "variables cannot be null");
            Objects.requireNonNull(events, "events cannot be null");
            Objects.requireNonNull(handles, "handles cannot be null");
            Objects.requireNonNull(aggregates, "aggregates cannot be null");
        }
    }
}
