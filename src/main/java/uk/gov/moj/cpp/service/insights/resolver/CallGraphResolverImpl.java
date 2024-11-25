package uk.gov.moj.cpp.service.insights.resolver;

import uk.gov.moj.cpp.service.insights.indexer.IndexBuilderImpl;
import uk.gov.moj.cpp.service.insights.model.ClassInfo;
import uk.gov.moj.cpp.service.insights.model.DependencyInfo;
import uk.gov.moj.cpp.service.insights.model.MethodInfo;
import uk.gov.moj.cpp.service.insights.util.ASTUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.BlockStmt;

/**
 * Implementation of CallGraphResolver that builds a call graph for Java methods.
 * It maps each method to the list of methods it invokes, facilitating analysis
 * such as determining call hierarchies and dependencies.
 */
public class CallGraphResolverImpl implements CallGraphResolver {

    /**
     * Reference to the IndexBuilderImpl instance to access class and method information.
     */
    private final IndexBuilderImpl indexBuilder;

    /**
     * Concurrent map storing method signatures mapped to the list of method signatures they call.
     */
    private final ConcurrentMap<String, List<String>> methodCallMap = new ConcurrentHashMap<>();

    /**
     * Constructs a CallGraphResolverImpl with the provided IndexBuilderImpl.
     *
     * @param indexBuilder The IndexBuilderImpl instance used to access class and method information.
     */
    public CallGraphResolverImpl(IndexBuilderImpl indexBuilder) {
        this.indexBuilder = indexBuilder;
    }

    /**
     * Builds the call graph by iterating over all classes and their methods,
     * parsing each method body to identify method calls.
     */
    @Override
    public void resolveCallGraph() {
        // Iterate over all ClassInfo instances in the index
        for (ClassInfo classInfo : indexBuilder.getClassInfoMap().values()) {
            // Iterate over all declared methods in the class
            for (MethodInfo methodInfo : classInfo.getMethods().values()) {
                parseMethodBody(classInfo, methodInfo);
            }
        }
    }

    /**
     * Parses the body of a given method or constructor to identify and record method calls.
     *
     * @param classInfo  The ClassInfo instance representing the class containing the method.
     * @param methodInfo The MethodInfo instance representing the method to parse.
     */
    private void parseMethodBody(ClassInfo classInfo, MethodInfo methodInfo) {
        // Retrieve the body declaration (method or constructor)
        BodyDeclaration<?> bodyDecl = methodInfo.getMethodDeclaration();

        if (bodyDecl instanceof com.github.javaparser.ast.body.MethodDeclaration methodDecl) {
            // Handle method declarations
            if (!methodDecl.getBody().isPresent()) {
                return; // Skip abstract methods or interface methods without bodies
            }
            BlockStmt body = methodDecl.getBody().get();
            List<MethodCallExpr> methodCalls = ASTUtils.collectMethodCalls(body);

            // Process each method call expression found in the method body
            for (MethodCallExpr callExpr : methodCalls) {
                String calledMethodSignature = resolveMethodCall(classInfo, callExpr);
                if (calledMethodSignature != null) {
                    // Add the called method signature to the caller's entry in the call map
                    methodCallMap.computeIfAbsent(methodInfo.getSignature(),
                                    k -> Collections.synchronizedList(new ArrayList<>()))
                            .add(calledMethodSignature);
                }
            }

        } else if (bodyDecl instanceof com.github.javaparser.ast.body.ConstructorDeclaration constructorDecl) {
            // Handle constructor declarations
            BlockStmt body = constructorDecl.getBody();
            List<MethodCallExpr> methodCalls = ASTUtils.collectMethodCalls(body);

            // Process each method call expression found in the constructor body
            for (MethodCallExpr callExpr : methodCalls) {
                String calledMethodSignature = resolveMethodCall(classInfo, callExpr);
                if (calledMethodSignature != null) {
                    // Add the called method signature to the constructor's entry in the call map
                    methodCallMap.computeIfAbsent(methodInfo.getSignature(),
                                    k -> Collections.synchronizedList(new ArrayList<>()))
                            .add(calledMethodSignature);
                }
            }
        }
    }

    /**
     * Resolves a MethodCallExpr to a fully qualified method signature.
     *
     * @param classInfo The ClassInfo instance representing the class containing the method call.
     * @param callExpr  The MethodCallExpr instance representing the method call expression.
     * @return The fully qualified signature of the called method, or null if it cannot be resolved.
     */
    private String resolveMethodCall(ClassInfo classInfo, MethodCallExpr callExpr) {
        // Extract the scope of the method call (e.g., this, another object)
        Optional<com.github.javaparser.ast.expr.Expression> scopeOpt = callExpr.getScope();
        String methodName = callExpr.getNameAsString();

        // List to hold potential class names where the called method might reside
        List<String> calledClassNames = new ArrayList<>();

        if (scopeOpt.isPresent()) {
            com.github.javaparser.ast.expr.Expression scopeExpr = scopeOpt.get();
            if (scopeExpr.isThisExpr()) {
                // If the scope is 'this', the method belongs to the current class
                calledClassNames.add(classInfo.getClassName());
            } else if (scopeExpr.isNameExpr()) {
                // If the scope is a named expression, it could be a dependency
                String scopeName = scopeExpr.asNameExpr().getNameAsString();
                Optional<DependencyInfo> depOpt = classInfo.getDependency(scopeName);
                depOpt.ifPresent(dependency -> {
                    String dependencyType = dependency.getType();
                    if (dependencyType != null) {
                        calledClassNames.add(dependencyType);
                    }
                });
                calledClassNames.add(classInfo.getClassName());
            }
            // Additional scope resolutions (e.g., static methods, nested calls) can be implemented here
        } else {
            // No scope specified; assume the method belongs to 'this' class
            calledClassNames.add(classInfo.getClassName());
        }

        // Attempt to resolve the called method within the identified classes
        for (String calledClassName : calledClassNames) {
            String fqClassName = indexBuilder.resolveFullyQualifiedClassName(
                    calledClassName, classInfo.getPackageName(), classInfo.getImportMap());
            if (fqClassName == null) {
                continue; // Skip if the class name cannot be resolved
            }

            ClassInfo calledClassInfo = indexBuilder.getClassInfoMap().get(fqClassName);
            if (calledClassInfo == null) {
                continue; // Skip if the class information is not available in the index
            }

            // Find candidate methods in the called class matching the method name
            List<MethodInfo> candidateMethods = calledClassInfo.getMethods().values().stream()
                    .filter(m -> getMethodName(m).equals(methodName))
                    .collect(Collectors.toList());

            // If no direct methods are found, search in inherited methods
            if (candidateMethods.isEmpty()) {
                candidateMethods = calledClassInfo.getInheritedMethods().stream()
                        .filter(m -> getMethodName(m).equals(methodName))
                        .collect(Collectors.toList());
            }

            // Attempt to match the method based on the number of arguments
            for (MethodInfo candidate : candidateMethods) {
                int candidateParamCount = getParameterCount(candidate);
                int callArgCount = callExpr.getArguments().size();

                if (candidateParamCount == callArgCount) {
                    // Found a method with a matching name and parameter count
                    return candidate.getSignature();
                }
            }
            // **Enhanced Logic: Attempt to Resolve Method in Imported Classes**
            // If the method call couldn't be resolved in the current class or its dependencies,
            // iterate over all imported classes to find a matching method.
            String resolvedSignature = resolveMethodInImports(classInfo, methodName, callExpr.getArguments().size());
            if (resolvedSignature != null) {
                return resolvedSignature;
            }

        }

        // Unable to resolve the method call to a known method signature
        return null;
    }

    /**
     * Attempts to resolve a method call by searching through all imported classes.
     *
     * @param classInfo  The ClassInfo instance representing the class containing the method call.
     * @param methodName The name of the method being called.
     * @param argCount   The number of arguments passed in the method call.
     * @return The fully qualified signature of the called method if found; otherwise, null.
     */
    private String resolveMethodInImports(ClassInfo classInfo, String methodName, int argCount) {
        Map<String, String> importMap = classInfo.getImportMap();

        for (String simpleClassName : importMap.keySet()) {
            String fqClassName = importMap.get(simpleClassName);
            ClassInfo importedClassInfo = indexBuilder.getClassInfoMap().get(fqClassName);
            if (importedClassInfo == null) {
                continue; // Skip if the imported class is not indexed
            }

            // Search for methods in the imported class
            List<MethodInfo> candidateMethods = importedClassInfo.getMethods().values().stream()
                    .filter(m -> getMethodName(m).equals(methodName))
                    .collect(Collectors.toList());

            // If no direct methods are found, search in inherited methods
            if (candidateMethods.isEmpty()) {
                candidateMethods = importedClassInfo.getInheritedMethods().stream()
                        .filter(m -> getMethodName(m).equals(methodName))
                        .collect(Collectors.toList());
            }

            // Attempt to match the method based on the number of arguments
            for (MethodInfo candidate : candidateMethods) {
                int candidateParamCount = getParameterCount(candidate);
                if (candidateParamCount == argCount) {
                    // Found a matching method in an imported class
                    return candidate.getSignature();
                }
            }

        }

        // Method not found in any imported classes
        return null;
    }

    /**
     * Extracts the method name from a MethodInfo instance.
     *
     * @param methodInfo The MethodInfo instance.
     * @return The name of the method.
     */
    private String getMethodName(MethodInfo methodInfo) {
        BodyDeclaration<?> bodyDecl = methodInfo.getMethodDeclaration();

        if (bodyDecl instanceof com.github.javaparser.ast.body.MethodDeclaration methodDecl) {
            return methodDecl.getNameAsString();
        } else if (bodyDecl instanceof com.github.javaparser.ast.body.ConstructorDeclaration constructorDecl) {
            return constructorDecl.getNameAsString();
        } else {
            return "";
        }
    }

    /**
     * Retrieves the number of parameters for a given MethodInfo instance.
     *
     * @param methodInfo The MethodInfo instance.
     * @return The number of parameters in the method.
     */
    private int getParameterCount(MethodInfo methodInfo) {
        BodyDeclaration<?> bodyDecl = methodInfo.getMethodDeclaration();

        if (bodyDecl instanceof com.github.javaparser.ast.body.MethodDeclaration methodDecl) {
            return methodDecl.getParameters().size();
        } else if (bodyDecl instanceof com.github.javaparser.ast.body.ConstructorDeclaration constructorDecl) {
            return constructorDecl.getParameters().size();
        } else {
            return 0;
        }
    }

    /**
     * Retrieves the call stack starting from the specified method signature.
     *
     * @param methodSignature The signature of the method to start the call stack from.
     * @return A list of method signatures representing the call stack.
     */
    @Override
    public List<String> getCallStack(String methodSignature) {
        List<String> callStack = new ArrayList<>();
        Set<String> visited = ConcurrentHashMap.newKeySet();
        buildCallStack(methodSignature, callStack, visited);
        return callStack;
    }

    /**
     * Recursively builds the call stack by traversing the call graph.
     *
     * @param methodSignature The current method signature being processed.
     * @param callStack       The list accumulating the call stack.
     * @param visited         A set tracking visited methods to avoid cycles.
     */
    private void buildCallStack(String methodSignature, List<String> callStack, Set<String> visited) {
        if (visited.contains(methodSignature)) {
            return; // Prevent infinite recursion due to cyclic calls
        }
        visited.add(methodSignature);
        callStack.add(methodSignature);
        List<String> callees = methodCallMap.getOrDefault(methodSignature, Collections.emptyList());
        for (String callee : callees) {
            buildCallStack(callee, callStack, visited);
        }
    }

    /**
     * Finds the MethodInfo instance corresponding to the given method signature.
     *
     * @param methodSignature The fully qualified signature of the method to find.
     * @return An Optional containing the MethodInfo if found, or empty otherwise.
     */
    @Override
    public Optional<MethodInfo> findMethodInfo(String methodSignature) {
        // Extract the class name from the method signature
        int hashIndex = methodSignature.indexOf('#');
        if (hashIndex == -1) {
            return Optional.empty(); // Invalid method signature format
        }
        String className = methodSignature.substring(0, hashIndex);
        ClassInfo classInfo = indexBuilder.getClassInfoMap().get(className);
        if (classInfo == null) {
            return Optional.empty(); // Class not found in the index
        }

        // Search for the MethodInfo in declared methods
        Optional<MethodInfo> methodOpt = classInfo.getMethods().values().stream()
                .filter(m -> m.getSignature().equals(methodSignature))
                .findFirst();

        if (methodOpt.isPresent()) {
            return methodOpt;
        }

        // If not found in declared methods, search in inherited methods
        return classInfo.getInheritedMethods().stream()
                .filter(m -> m.getSignature().equals(methodSignature))
                .findFirst();
    }

    /**
     * Retrieves the entire call graph mapping method signatures to the methods they invoke.
     *
     * @return An unmodifiable view of the method call map.
     */
    public Map<String, List<String>> getMethodCallMap() {
        return Collections.unmodifiableMap(methodCallMap);
    }
}
