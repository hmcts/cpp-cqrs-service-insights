package uk.gov.moj.cpp.service.insights.common;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

/**
 * Builds an index of Java classes, including their methods, fields, and method call relationships.
 * Allows querying the call stack of a specific method and printing their bodies.
 */
public class MethodStackTracer {

    // Set of annotations that denote injection
    private static final Set<String> INJECTION_ANNOTATIONS = Set.of(
            "Inject", "Autowired", "Resource", "javax.inject.Inject"
    );

    // Map of fully qualified class name to ClassInfo
    private final Map<String, ClassInfo> classInfoMap = new ConcurrentHashMap<>();

    // Map of method signature to the list of methods it calls in order
    private final Map<String, List<String>> methodCallMap = new ConcurrentHashMap<>();

    // Map of interface name to implementing class names
    private final Map<String, Set<String>> interfaceImplMap = new ConcurrentHashMap<>();

    /**
     * Example usage of the MethodStackTracer.
     */
    public static void main(String[] args) {
        MethodStackTracer indexer = new MethodStackTracer();
        List<Path> sourcePaths = Arrays.asList(Path.of("/Users/satishkumar/moj/software/cpp.context.hearing"));

        try {
            indexer.buildIndex(sourcePaths);

            // Example: Get method body for a specific method
            String targetClassName = "uk.gov.moj.cpp.hearing.event.PublishResultsV3EventProcessor";
            String targetMethodName = "resultsShared";
            List<String> targetParameters = Arrays.asList("JsonEnvelope");

            Optional<ClassInfo> classInfoOpt = indexer.getClassInfo(targetClassName);
            if (classInfoOpt.isPresent()) {
                ClassInfo classInfo = classInfoOpt.get();
                String methodSignature = indexer.getMethodSignature(targetClassName, targetMethodName, targetParameters);
                Optional<MethodInfo> methodInfoOpt = classInfo.getMethods().values().stream()
                        .filter(m -> m.getSignature().equals(methodSignature))
                        .findFirst();
                if (methodInfoOpt.isPresent()) {
                    BodyDeclaration<?> bodyDecl = methodInfoOpt.get().getMethodDeclaration();
                    if (bodyDecl instanceof MethodDeclaration methodDecl && methodDecl.getBody().isPresent()) {
                        BlockStmt body = methodDecl.getBody().get();
                        System.out.println("Method Body for " + methodSignature + ":");
                        System.out.println(body);
                    } else if (bodyDecl instanceof ConstructorDeclaration constructorDecl) {
                        BlockStmt body = constructorDecl.getBody();
                        System.out.println("Constructor Body for " + methodSignature + ":");
                        System.out.println(body.toString());
                    } else {
                        System.out.println("No body available for the method: " + methodSignature);
                    }

                    // Now, retrieve and print the bodies of all nested method calls in the call stack
                    List<String> callStack = indexer.getCallStack(
                            targetClassName,
                            targetMethodName,
                            targetParameters
                    );

                    System.out.println("\nCall Stack Method Bodies:");
                    for (String calleeSignature : callStack) {
                        // Skip the initial method as it's already printed
                        if (calleeSignature.equals(methodSignature)) {
                            continue;
                        }

                        Optional<MethodInfo> calleeMethodOpt = indexer.findMethodInfo(calleeSignature);
                        if (calleeMethodOpt.isPresent()) {
                            BodyDeclaration<?> calleeBodyDecl = calleeMethodOpt.get().getMethodDeclaration();
                            if (calleeBodyDecl instanceof MethodDeclaration calleeMethodDecl && calleeMethodDecl.getBody().isPresent()) {
                                BlockStmt calleeBody = calleeMethodDecl.getBody().get();
                                System.out.println("Method Body for " + calleeSignature + ":");
                                System.out.println(calleeBody);
                            } else if (calleeBodyDecl instanceof ConstructorDeclaration calleeConstructorDecl) {
                                BlockStmt calleeBody = calleeConstructorDecl.getBody();
                                System.out.println("Constructor Body for " + calleeSignature + ":");
                                System.out.println(calleeBody.toString());
                            } else {
                                System.out.println("No body available for the method: " + calleeSignature);
                                // Attempt to find implementations if it's an interface method
                                List<MethodInfo> implementations = indexer.findImplementationsMethodInfo(calleeSignature);
                                if (!implementations.isEmpty()) {
                                    for (MethodInfo implMethod : implementations) {
                                        BodyDeclaration<?> implBodyDecl = implMethod.getMethodDeclaration();
                                        if (implBodyDecl instanceof MethodDeclaration implMethodDecl && implMethodDecl.getBody().isPresent()) {
                                            BlockStmt implBody = implMethodDecl.getBody().get();
                                            System.out.println("Method Body for Implementation " + implMethod.getSignature() + ":");
                                            System.out.println(implBody);
                                        } else if (implBodyDecl instanceof ConstructorDeclaration implConstructorDecl) {
                                            BlockStmt implBody = implConstructorDecl.getBody();
                                            System.out.println("Constructor Body for Implementation " + implMethod.getSignature() + ":");
                                            System.out.println(implBody.toString());
                                        } else {
                                            System.out.println("No body available for the implementation method: " + implMethod.getSignature());
                                        }
                                        System.out.println("--------------------------------------------------");
                                    }
                                } else {
                                    System.out.println("No implementations found for the interface method: " + calleeSignature);
                                }
                            }
                        } else {
                            System.out.println("Method not found: " + calleeSignature);
                        }

                        System.out.println("--------------------------------------------------");
                    }

                } else {
                    System.out.println("Method not found: " + targetClassName + "#" + targetMethodName + "(" + String.join(",", targetParameters) + ")");
                }
            } else {
                System.out.println("Class not found: " + targetClassName);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Builds an index from the specified source directories.
     *
     * @param sourcePaths List of source directories to index.
     * @throws IOException If an I/O error occurs.
     */
    public void buildIndex(List<Path> sourcePaths) throws IOException {
        // First pass: parse all classes and collect class information
        for (Path sourcePath : sourcePaths) {
            indexSourcePath(sourcePath);
        }

        // Second pass: resolve interface implementations
        resolveInterfaceImplementations();

        // Third pass: parse method bodies to collect method calls
        for (ClassInfo classInfo : classInfoMap.values()) {
            for (MethodInfo methodInfo : classInfo.getMethods().values()) {
                parseMethodBody(classInfo, methodInfo);
            }
        }
    }

    /**
     * Recursively visits all Java files in the source path and parses them.
     *
     * @param sourcePath Root source directory.
     * @throws IOException If an I/O error occurs.
     */
    private void indexSourcePath(Path sourcePath) throws IOException {
        Files.walkFileTree(sourcePath, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.toString().endsWith(".java")) {
                    try {
                        parseJavaFile(file);
                    } catch (IOException e) {
                        System.err.println("Error parsing file: " + file);
                        e.printStackTrace();
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Parses a single Java file and extracts class, method, and field information.
     *
     * @param file Path to the Java file.
     * @throws IOException If an I/O error occurs.
     */
    private void parseJavaFile(Path file) throws IOException {
        CompilationUnit cu;
        try {
            cu = StaticJavaParser.parse(file);
        } catch (Exception e) {
            System.err.println("Error parsing file: " + file);
            e.printStackTrace();
            return;
        }

        String packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getNameAsString())
                .orElse("");

        // Build import map: simple name -> fully qualified name
        Map<String, String> importMap = new HashMap<>();
        cu.getImports().forEach(importDecl -> {
            String imported = importDecl.getNameAsString();
            if (!importDecl.isAsterisk()) {
                String simpleName = imported.substring(imported.lastIndexOf('.') + 1);
                importMap.put(simpleName, imported);
            }
        });

        for (TypeDeclaration<?> typeDecl : cu.getTypes()) {
            processTypeDeclaration(typeDecl, packageName, null, importMap);
        }
    }

    /**
     * Processes a type declaration (class, interface, enum) and extracts relevant information.
     *
     * @param typeDecl    The type declaration.
     * @param packageName The package name.
     * @param parentClass The parent class name if it's a nested type; null otherwise.
     * @param importMap   Map of simple class names to fully qualified names from imports.
     */
    private void processTypeDeclaration(TypeDeclaration<?> typeDecl, String packageName, String parentClass, Map<String, String> importMap) {
        String className = typeDecl.getNameAsString();
        String fullClassName = parentClass == null
                ? (packageName.isEmpty() ? className : packageName + "." + className)
                : parentClass + "$" + className; // For nested classes

        // Initialize ClassInfo
        ClassInfo classInfo = classInfoMap.computeIfAbsent(fullClassName, k -> new ClassInfo(fullClassName, packageName, importMap));

        // Determine if it's an interface and track implementations
        if (typeDecl instanceof ClassOrInterfaceDeclaration coiDecl) {
            if (coiDecl.isInterface()) {
                interfaceImplMap.putIfAbsent(fullClassName, ConcurrentHashMap.newKeySet());
            } else {
                // Track implemented interfaces
                for (ClassOrInterfaceType implementedInterface : coiDecl.getImplementedTypes()) {
                    String ifaceName = implementedInterface.getNameWithScope();
                    String ifaceFullName = resolveFullyQualifiedClassName(ifaceName, packageName, importMap);
                    if (ifaceFullName != null) {
                        classInfo.addImplementedInterface(ifaceFullName);
                        interfaceImplMap.computeIfAbsent(ifaceFullName, k -> ConcurrentHashMap.newKeySet()).add(fullClassName);
                    }
                }
            }

            // Track extended classes or interfaces if needed
            // This can be extended based on requirements
        } else if (typeDecl instanceof EnumDeclaration) {
            // Handle enum-specific logic if necessary
        }

        // Process fields
        for (FieldDeclaration fieldDecl : typeDecl.getFields()) {
            boolean isInjected = fieldDecl.getAnnotations().stream()
                    .anyMatch(a -> INJECTION_ANNOTATIONS.contains(a.getNameAsString()) ||
                            INJECTION_ANNOTATIONS.contains(
                                    a.getName().getQualifier().map(Name::asString).orElse(a.getNameAsString())
                            ));
            for (VariableDeclarator var : fieldDecl.getVariables()) {
                String fieldName = var.getNameAsString();
                String fieldType = resolveFullyQualifiedClassName(var.getType().asString(), packageName, importMap);
                classInfo.addDependency(new DependencyInfo(fieldName, fieldType, isInjected, true));
            }
        }

        // Process methods
        for (MethodDeclaration methodDecl : typeDecl.getMethods()) {
            String methodSignature = getMethodSignature(fullClassName, methodDecl);
            MethodInfo methodInfo = new MethodInfo(methodSignature, methodDecl);
            classInfo.addMethod(methodInfo);
        }

        // Process constructors
        for (ConstructorDeclaration constructorDecl : typeDecl.getConstructors()) {
            String constructorSignature = getConstructorSignature(fullClassName, constructorDecl);
            MethodInfo methodInfo = new MethodInfo(constructorSignature, constructorDecl);
            classInfo.addMethod(methodInfo);

            // Check if constructor is annotated with @Inject
            boolean isConstructorInjected = constructorDecl.getAnnotations().stream()
                    .anyMatch(a -> INJECTION_ANNOTATIONS.contains(a.getNameAsString()) ||
                            INJECTION_ANNOTATIONS.contains(
                                    a.getName().getQualifier().map(Name::asString).orElse(a.getNameAsString())
                            ));

            // Add all constructor parameters as dependencies
            for (Parameter param : constructorDecl.getParameters()) {
                String paramName = param.getNameAsString();
                String paramType = resolveFullyQualifiedClassName(param.getType().asString(), packageName, importMap);
                classInfo.addDependency(new DependencyInfo(paramName, paramType, isConstructorInjected, false));
            }

            // Process assignments in constructor body
            if (constructorDecl.getBody() != null) {
                AssignmentCollector assignmentCollector = new AssignmentCollector();
                assignmentCollector.visit(constructorDecl.getBody(), null);
                Map<String, String> fieldAssignments = assignmentCollector.getFieldAssignments();
                for (Map.Entry<String, String> entry : fieldAssignments.entrySet()) {
                    String fieldName = entry.getKey();
                    String paramName = entry.getValue();
                    // Get the type of the parameter
                    Optional<DependencyInfo> paramDepOpt = classInfo.getDependency(paramName);
                    if (paramDepOpt.isPresent()) {
                        String paramType = paramDepOpt.get().getType();
                        classInfo.addDependency(new DependencyInfo(fieldName, paramType, isConstructorInjected, true));
                    }
                }
            }
        }

        // Process nested types
        for (BodyDeclaration<?> member : typeDecl.getMembers()) {
            if (member instanceof TypeDeclaration<?> nestedType) {
                processTypeDeclaration(nestedType, packageName, fullClassName, importMap);
            }
        }
    }

    /**
     * Resolves the fully qualified class name based on the simple or partially qualified name,
     * using the import map to handle ambiguities.
     *
     * @param className   The class name to resolve (can be simple or qualified).
     * @param packageName The current package name.
     * @param importMap   Map of simple class names to fully qualified names from imports.
     * @return Fully qualified class name or null if not found.
     */
    private String resolveFullyQualifiedClassName(String className, String packageName, Map<String, String> importMap) {
        if (className == null || className.isEmpty()) {
            return null;
        }

        // Handle generic types by stripping type parameters
        if (className.contains("<")) {
            className = className.substring(0, className.indexOf('<'));
        }

        // If className is already fully qualified, verify its existence
        if (classInfoMap.containsKey(className)) {
            return className;
        }

        // If className is simple and present in imports, return the imported fully qualified name
        if (importMap.containsKey(className)) {
            return importMap.get(className);
        }

        // If class is in the same package, construct its fully qualified name
        String fqName = packageName.isEmpty() ? className : packageName + "." + className;
        if (classInfoMap.containsKey(fqName)) {
            return fqName;
        }

        // Handle nested class names (e.g., Outer.Inner)
        if (className.contains(".")) {
            if (classInfoMap.containsKey(className)) {
                return className;
            }
        }

        // As a last resort, perform a wildcard search
        final String finalClassName = className;
        List<String> matches = classInfoMap.keySet().stream()
                .filter(k -> k.endsWith("." + finalClassName))
                .collect(Collectors.toList());

        if (matches.size() == 1) {
            return matches.get(0);
        }

        // Ambiguous or not found
        return null;
    }

    /**
     * Generates a unique method signature including class name and parameter types.
     *
     * @param className  Fully qualified class name.
     * @param methodDecl The method declaration.
     * @return Unique method signature.
     */
    private String getMethodSignature(String className, MethodDeclaration methodDecl) {
        String params = methodDecl.getParameters().stream()
                .map(p -> p.getType().asString())
                .collect(Collectors.joining(","));
        return className + "#" + methodDecl.getNameAsString() + "(" + params + ")";
    }

    /**
     * Generates a unique constructor signature including class name and parameter types.
     *
     * @param className       Fully qualified class name.
     * @param constructorDecl The constructor declaration.
     * @return Unique constructor signature.
     */
    private String getConstructorSignature(String className, ConstructorDeclaration constructorDecl) {
        String params = constructorDecl.getParameters().stream()
                .map(p -> p.getType().asString())
                .collect(Collectors.joining(","));
        return className + "#<init>(" + params + ")";
    }

    /**
     * Parses the body of a method to identify method calls and populate the call graph.
     *
     * @param classInfo  The class containing the method.
     * @param methodInfo The method to parse.
     */
    private void parseMethodBody(ClassInfo classInfo, MethodInfo methodInfo) {
        BodyDeclaration<?> bodyDecl = methodInfo.getMethodDeclaration();

        if (bodyDecl instanceof MethodDeclaration methodDecl) {
            if (!methodDecl.getBody().isPresent()) {
                return; // Abstract or interface method
            }
            BlockStmt body = methodDecl.getBody().get();
            MethodCallCollector collector = new MethodCallCollector();
            collector.visit(body, null);
            List<MethodCallExpr> methodCalls = collector.getMethodCalls();

            for (MethodCallExpr callExpr : methodCalls) {
                String calledMethodSignature = resolveMethodCall(classInfo, callExpr);
                if (calledMethodSignature != null) {
                    methodCallMap.computeIfAbsent(methodInfo.getSignature(), k -> new ArrayList<>())
                            .add(calledMethodSignature);
                }
            }

        } else if (bodyDecl instanceof ConstructorDeclaration constructorDecl) {
            BlockStmt body = constructorDecl.getBody();
            MethodCallCollector collector = new MethodCallCollector();
            collector.visit(body, null);
            List<MethodCallExpr> methodCalls = collector.getMethodCalls();

            for (MethodCallExpr callExpr : methodCalls) {
                String calledMethodSignature = resolveMethodCall(classInfo, callExpr);
                if (calledMethodSignature != null) {
                    methodCallMap.computeIfAbsent(methodInfo.getSignature(), k -> new ArrayList<>())
                            .add(calledMethodSignature);
                }
            }
        }
    }

    /**
     * Resolves a method call expression to its fully qualified method signature.
     *
     * @param classInfo The class containing the method making the call.
     * @param callExpr  The method call expression.
     * @return Fully qualified method signature or null if resolution fails.
     */
    private String resolveMethodCall(ClassInfo classInfo, MethodCallExpr callExpr) {
        // Determine the scope of the method call
        Optional<Expression> scope = callExpr.getScope();
        String methodName = callExpr.getNameAsString();

        List<String> calledClassNames = new ArrayList<>();

        if (scope.isPresent()) {
            Expression scopeExpr = scope.get();
            if (scopeExpr.isThisExpr()) {
                calledClassNames.add(classInfo.getClassName());
            } else if (scopeExpr.isNameExpr()) {
                String scopeName = scopeExpr.asNameExpr().getNameAsString();
                // Check if scopeName is a dependency (field or constructor parameter)
                Optional<DependencyInfo> depOpt = classInfo.getDependency(scopeName);
                if (depOpt.isPresent()) {
                    DependencyInfo dependency = depOpt.get();
                    String dependencyType = dependency.getType();

                    if (dependencyType != null) {
                        if (interfaceImplMap.containsKey(dependencyType)) {
                            Set<String> implementations = interfaceImplMap.get(dependencyType);
                            calledClassNames.addAll(implementations);
                        } else {
                            calledClassNames.add(dependencyType);
                        }
                    }
                } else {
                    // It's a local variable; without symbol solving, we cannot determine its type
                    // Hence, skip resolving this method call
                    return null;
                }
            } else if (scopeExpr.isFieldAccessExpr()) {
                FieldAccessExpr fieldAccess = scopeExpr.asFieldAccessExpr();
                Expression fieldScope = fieldAccess.getScope();

                if (fieldScope.isThisExpr()) {
                    String fieldName = fieldAccess.getNameAsString();
                    Optional<DependencyInfo> depOpt = classInfo.getDependency(fieldName);
                    if (depOpt.isPresent()) {
                        String fieldType = depOpt.get().getType();
                        calledClassNames.add(fieldType);
                    }
                } else {
                    // Handle other scopes if necessary
                    return null;
                }
            } else {
                // Additional scope resolution can be implemented as needed
                return null;
            }
        } else {
            // No scope specified; method call on 'this' or static import
            calledClassNames.add(classInfo.getClassName());

            // Also check implemented interfaces
            for (String ifaceName : classInfo.getImplementedInterfaces()) {
                calledClassNames.add(ifaceName);
            }
        }

        // Attempt to resolve method calls for each possible called class
        for (String calledClassName : calledClassNames) {
            String fqClassName = resolveFullyQualifiedClassName(calledClassName, classInfo.getPackageName(), classInfo.getImportMap());

            if (fqClassName == null) {
                // Unable to resolve class name
                continue;
            }

            ClassInfo calledClassInfo = classInfoMap.get(fqClassName);
            if (calledClassInfo == null) {
                continue;
            }

            // Find matching method signature
            List<MethodInfo> candidateMethods = calledClassInfo.getMethods().values().stream()
                    .filter(m -> {
                        String name;
                        BodyDeclaration<?> bodyDecl = m.getMethodDeclaration();

                        if (bodyDecl instanceof MethodDeclaration) {
                            name = ((MethodDeclaration) bodyDecl).getNameAsString();
                        } else if (bodyDecl instanceof ConstructorDeclaration) {
                            name = ((ConstructorDeclaration) bodyDecl).getNameAsString();
                        } else {
                            // Handle other possible BodyDeclaration types or throw an exception
                            name = "";
                        }
                        return name.equals(methodName);
                    })
                    .collect(Collectors.toList());

            // Simplistic matching based on parameter count
            for (MethodInfo candidate : candidateMethods) {
                int candidateParamCount;
                if (candidate.getMethodDeclaration() instanceof MethodDeclaration mDecl) {
                    candidateParamCount = mDecl.getParameters().size();
                } else if (candidate.getMethodDeclaration() instanceof ConstructorDeclaration cDecl) {
                    candidateParamCount = cDecl.getParameters().size();
                } else {
                    continue;
                }

                int callArgCount = callExpr.getArguments().size();
                if (candidateParamCount == callArgCount) {
                    return candidate.getSignature();
                }
            }

            // If no exact match found, attempt to find by name only
            if (!candidateMethods.isEmpty()) {
                return candidateMethods.get(0).getSignature(); // Return the first match
            }
        }

        return null;
    }

    /**
     * Resolves which classes implement a given interface and updates the call graph accordingly.
     */
    private void resolveInterfaceImplementations() {
        // Iterate over method calls and if the called class is an interface,
        // map the call to all implementing classes' methods
        Map<String, List<String>> updatedMethodCallMap = new ConcurrentHashMap<>();

        for (Map.Entry<String, List<String>> entry : methodCallMap.entrySet()) {
            String caller = entry.getKey();
            List<String> callees = entry.getValue();

            for (String callee : callees) {
                if (interfaceImplMap.containsKey(callee)) {
                    Set<String> implementations = interfaceImplMap.get(callee);
                    for (String implClass : implementations) {
                        ClassInfo implClassInfo = classInfoMap.get(implClass);
                        if (implClassInfo != null) {
                            // Find the corresponding method in the implementation
                            String methodName = getMethodName(callee);
                            Optional<MethodInfo> methodOpt = implClassInfo.getMethods().values().stream()
                                    .filter(m -> m.getMethodDeclaration().asMethodDeclaration().getNameAsString().equals(methodName))
                                    .findFirst();
                            methodOpt.ifPresent(m -> {
                                updatedMethodCallMap
                                        .computeIfAbsent(caller, k -> Collections.synchronizedList(new ArrayList<>()))
                                        .add(m.getSignature());
                            });
                        }
                    }
                } else {
                    // Callee is a concrete class; no action needed
                    updatedMethodCallMap
                            .computeIfAbsent(caller, k -> Collections.synchronizedList(new ArrayList<>()))
                            .add(callee);
                }
            }
        }

        // Replace the original methodCallMap with the updated one
        methodCallMap.putAll((updatedMethodCallMap));
    }

    /**
     * Extracts the method name from a method signature.
     *
     * @param methodSignature The method signature.
     * @return The method name.
     */
    private String getMethodName(String methodSignature) {
        int hashIndex = methodSignature.indexOf('#');
        int parenIndex = methodSignature.indexOf('(', hashIndex);
        if (hashIndex != -1 && parenIndex != -1) {
            return methodSignature.substring(hashIndex + 1, parenIndex);
        }
        return "";
    }

    /**
     * Retrieves class information by its fully qualified name.
     *
     * @param className Fully qualified class name.
     * @return Optional containing ClassInfo if found.
     */
    public Optional<ClassInfo> getClassInfo(String className) {
        return Optional.ofNullable(classInfoMap.get(className));
    }

    /**
     * Retrieves the method signature based on class name, method name, and parameters.
     *
     * @param className  Fully qualified class name.
     * @param methodName Method name.
     * @param parameters List of parameter types.
     * @return Method signature string.
     */
    public String getMethodSignature(String className, String methodName, List<String> parameters) {
        String params = String.join(",", parameters);
        return className + "#" + methodName + "(" + params + ")";
    }

    /**
     * Retrieves the call stack for a given method.
     *
     * @param className  Fully qualified class name.
     * @param methodName Method name.
     * @param parameters List of parameter types.
     * @return List of method signatures representing the call stack.
     */
    public List<String> getCallStack(String className, String methodName, List<String> parameters) {
        String methodSignature = getMethodSignature(className, methodName, parameters);
        List<String> callStack = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        buildCallStack(methodSignature, callStack, visited);
        return callStack;
    }

    /**
     * Recursively builds the call stack starting from the specified method.
     *
     * @param methodSignature The starting method signature.
     * @param callStack       The accumulated call stack.
     * @param visited         Set of visited methods to avoid cycles.
     */
    private void buildCallStack(String methodSignature, List<String> callStack, Set<String> visited) {
        if (visited.contains(methodSignature)) {
            return; // Avoid cycles
        }
        visited.add(methodSignature);
        callStack.add(methodSignature);
        List<String> callees = methodCallMap.getOrDefault(methodSignature, Collections.emptyList());
        for (String callee : callees) {
            buildCallStack(callee, callStack, visited);
        }
    }

    /**
     * Finds MethodInfo based on method signature.
     *
     * @param methodSignature The method signature.
     * @return Optional containing MethodInfo if found.
     */
    private Optional<MethodInfo> findMethodInfo(String methodSignature) {
        // Extract class name from method signature
        int hashIndex = methodSignature.indexOf('#');
        if (hashIndex == -1) {
            return Optional.empty();
        }
        String className = methodSignature.substring(0, hashIndex);
        Optional<ClassInfo> classInfoOpt = getClassInfo(className);
        if (classInfoOpt.isEmpty()) {
            return Optional.empty();
        }
        ClassInfo classInfo = classInfoOpt.get();
        return Optional.ofNullable(classInfo.getMethods().get(methodSignature));
    }

    /**
     * Finds MethodInfo implementations for a given interface method signature.
     *
     * @param interfaceMethodSignature The interface method signature.
     * @return List of MethodInfo objects representing implementations.
     */
    private List<MethodInfo> findImplementationsMethodInfo(String interfaceMethodSignature) {
        List<MethodInfo> implementations = new ArrayList<>();
        // Extract class name and method name from interface method signature
        int hashIndex = interfaceMethodSignature.indexOf('#');
        int parenIndex = interfaceMethodSignature.indexOf('(', hashIndex);
        if (hashIndex == -1 || parenIndex == -1) {
            return implementations;
        }
        String interfaceClassName = interfaceMethodSignature.substring(0, hashIndex);
        String methodName = interfaceMethodSignature.substring(hashIndex + 1, parenIndex);

        // Find all implementing classes
        Set<String> implementationsSet = interfaceImplMap.getOrDefault(interfaceClassName, Collections.emptySet());
        for (String implClassName : implementationsSet) {
            ClassInfo implClassInfo = classInfoMap.get(implClassName);
            if (implClassInfo != null) {
                // Find methods in implementation class that match the interface method
                List<MethodInfo> matchingMethods = implClassInfo.getMethods().values().stream()
                        .filter(m -> {
                            String implMethodName;
                            BodyDeclaration<?> bodyDecl = m.getMethodDeclaration();

                            if (bodyDecl instanceof MethodDeclaration) {
                                implMethodName = ((MethodDeclaration) bodyDecl).getNameAsString();
                            } else if (bodyDecl instanceof ConstructorDeclaration) {
                                implMethodName = ((ConstructorDeclaration) bodyDecl).getNameAsString();
                            } else {
                                implMethodName = "";
                            }
                            return implMethodName.equals(methodName);
                        })
                        .collect(Collectors.toList());
                implementations.addAll(matchingMethods);
            }
        }
        return implementations;
    }

    /**
     * Represents information about a Java class.
     */
    public static class ClassInfo {
        private final String className;
        private final String packageName;
        private final Map<String, String> importMap;
        private final Map<String, MethodInfo> methods = new ConcurrentHashMap<>();
        private final Map<String, DependencyInfo> dependencies = new ConcurrentHashMap<>();
        private final Set<String> implementedInterfaces = new HashSet<>();

        public ClassInfo(String className, String packageName, Map<String, String> importMap) {
            this.className = className;
            this.packageName = packageName;
            this.importMap = new HashMap<>(importMap);
        }

        public String getClassName() {
            return className;
        }

        public String getPackageName() {
            return packageName;
        }

        public Map<String, String> getImportMap() {
            return importMap;
        }

        public void addMethod(MethodInfo methodInfo) {
            methods.put(methodInfo.getSignature(), methodInfo);
        }

        public void addDependency(DependencyInfo dependencyInfo) {
            dependencies.put(dependencyInfo.getName(), dependencyInfo);
        }

        public Optional<DependencyInfo> getDependency(String name) {
            return Optional.ofNullable(dependencies.get(name));
        }

        public Map<String, MethodInfo> getMethods() {
            return methods;
        }

        public Collection<DependencyInfo> getDependencies() {
            return dependencies.values();
        }

        public void addImplementedInterface(String interfaceName) {
            implementedInterfaces.add(interfaceName);
        }

        public Set<String> getImplementedInterfaces() {
            return implementedInterfaces;
        }
    }

    /**
     * Represents information about a Java method or constructor.
     */
    public static class MethodInfo {
        private final String signature;
        private final BodyDeclaration<?> methodDeclaration;

        public MethodInfo(String signature, BodyDeclaration<?> methodDeclaration) {
            this.signature = signature;
            this.methodDeclaration = methodDeclaration;
        }

        public String getSignature() {
            return signature;
        }

        public BodyDeclaration<?> getMethodDeclaration() {
            return methodDeclaration;
        }
    }

    /**
     * Represents information about a Java dependency (field or constructor parameter).
     */
    public static class DependencyInfo {
        private final String name;
        private final String type;
        private final boolean isInjected;
        private final boolean isField;

        public DependencyInfo(String name, String type, boolean isInjected, boolean isField) {
            this.name = name;
            this.type = type;
            this.isInjected = isInjected;
            this.isField = isField;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }

        public boolean isInjected() {
            return isInjected;
        }

        public boolean isField() {
            return isField;
        }
    }

    /**
     * Represents a method call expression collector using JavaParser's visitor pattern.
     */
    private static class MethodCallCollector extends VoidVisitorAdapter<Void> {
        private final List<MethodCallExpr> methodCalls = new ArrayList<>();

        @Override
        public void visit(MethodCallExpr call, Void arg) {
            super.visit(call, arg);
            methodCalls.add(call);
        }

        public List<MethodCallExpr> getMethodCalls() {
            return methodCalls;
        }
    }

    /**
     * Collects assignments in constructor bodies to track field assignments.
     */
    private static class AssignmentCollector extends VoidVisitorAdapter<Void> {
        private final Map<String, String> fieldAssignments = new HashMap<>();


        @Override
        public void visit(AssignExpr assignExpr, Void arg) {
            super.visit(assignExpr, arg);
            if (assignExpr.getOperator() != AssignExpr.Operator.ASSIGN) {
                return;
            }
            Expression target = assignExpr.getTarget();
            Expression value = assignExpr.getValue();

            if (target.isFieldAccessExpr() && value.isNameExpr()) {
                FieldAccessExpr fieldAccess = target.asFieldAccessExpr();
                if (fieldAccess.getScope().isThisExpr()) {
                    String fieldName = fieldAccess.getNameAsString();
                    String paramName = value.asNameExpr().getNameAsString();
                    fieldAssignments.put(fieldName, paramName);
                }
            }
        }

        public Map<String, String> getFieldAssignments() {
            return fieldAssignments;
        }
    }
}
