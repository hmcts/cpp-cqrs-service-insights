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
import java.util.stream.Collectors;

import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.BlockStmt;

public class CallGraphResolverImpl implements CallGraphResolver {

    private final IndexBuilderImpl indexBuilder;

    // Map of method signature to the list of methods it calls in order
    private final Map<String, List<String>> methodCallMap = new ConcurrentHashMap<>();

    public CallGraphResolverImpl(IndexBuilderImpl indexBuilder) {
        this.indexBuilder = indexBuilder;
    }

    @Override
    public void resolveCallGraph() {
        // Iterate over all classes and methods to build the call graph
        for (ClassInfo classInfo : indexBuilder.getClassInfoMap().values()) {
            for (MethodInfo methodInfo : classInfo.getMethods().values()) {
                parseMethodBody(classInfo, methodInfo);
            }
        }
    }

    private void parseMethodBody(ClassInfo classInfo, MethodInfo methodInfo) {
        BodyDeclaration<?> bodyDecl = methodInfo.getMethodDeclaration();

        if (bodyDecl instanceof com.github.javaparser.ast.body.MethodDeclaration methodDecl) {
            if (!methodDecl.getBody().isPresent()) {
                return; // Abstract or interface method
            }
            BlockStmt body = methodDecl.getBody().get();
            List<MethodCallExpr> methodCalls = ASTUtils.collectMethodCalls(body);

            for (MethodCallExpr callExpr : methodCalls) {
                String calledMethodSignature = resolveMethodCall(classInfo, callExpr);
                if (calledMethodSignature != null) {
                    methodCallMap.computeIfAbsent(methodInfo.getSignature(), k -> Collections.synchronizedList(new ArrayList<>()))
                            .add(calledMethodSignature);
                }
            }

        } else if (bodyDecl instanceof com.github.javaparser.ast.body.ConstructorDeclaration constructorDecl) {
            BlockStmt body = constructorDecl.getBody();
            List<MethodCallExpr> methodCalls = ASTUtils.collectMethodCalls(body);

            for (MethodCallExpr callExpr : methodCalls) {
                String calledMethodSignature = resolveMethodCall(classInfo, callExpr);
                if (calledMethodSignature != null) {
                    methodCallMap.computeIfAbsent(methodInfo.getSignature(), k -> Collections.synchronizedList(new ArrayList<>()))
                            .add(calledMethodSignature);
                }
            }
        }
    }

    private String resolveMethodCall(ClassInfo classInfo, MethodCallExpr callExpr) {
        // Simplistic resolution logic
        Optional<com.github.javaparser.ast.expr.Expression> scope = callExpr.getScope();
        String methodName = callExpr.getNameAsString();

        List<String> calledClassNames = new ArrayList<>();

        if (scope.isPresent()) {
            com.github.javaparser.ast.expr.Expression scopeExpr = scope.get();
            if (scopeExpr.isThisExpr()) {
                calledClassNames.add(classInfo.getClassName());
            } else if (scopeExpr.isNameExpr()) {
                String scopeName = scopeExpr.asNameExpr().getNameAsString();
                Optional<DependencyInfo> depOpt = classInfo.getDependency(scopeName);
                depOpt.ifPresent(dependency -> {
                    String dependencyType = dependency.getType();
                    if (dependencyType != null) {
                        calledClassNames.add(dependencyType);
                    }
                });
            }
            // Additional scope resolutions can be implemented here
        } else {
            // No scope specified; assume 'this'
            calledClassNames.add(classInfo.getClassName());
        }

        for (String calledClassName : calledClassNames) {
            String fqClassName = indexBuilder.resolveFullyQualifiedClassName(calledClassName, classInfo.getPackageName(), classInfo.getImportMap());
            if (fqClassName == null) {
                continue;
            }

            ClassInfo calledClassInfo = indexBuilder.getClassInfoMap().get(fqClassName);
            if (calledClassInfo == null) {
                continue;
            }

            // Find matching method signature based on name and parameter count
            List<MethodInfo> candidateMethods = calledClassInfo.getMethods().values().stream()
                    .filter(m -> {
                        String name;
                        BodyDeclaration<?> bodyDecl = m.getMethodDeclaration();

                        if (bodyDecl instanceof com.github.javaparser.ast.body.MethodDeclaration) {
                            name = ((com.github.javaparser.ast.body.MethodDeclaration) bodyDecl).getNameAsString();
                        } else if (bodyDecl instanceof com.github.javaparser.ast.body.ConstructorDeclaration) {
                            name = ((com.github.javaparser.ast.body.ConstructorDeclaration) bodyDecl).getNameAsString();
                        } else {
                            name = "";
                        }
                        return name.equals(methodName);
                    })
                    .collect(Collectors.toList());

            for (MethodInfo candidate : candidateMethods) {
                int candidateParamCount;
                if (candidate.getMethodDeclaration() instanceof com.github.javaparser.ast.body.MethodDeclaration mDecl) {
                    candidateParamCount = mDecl.getParameters().size();
                } else if (candidate.getMethodDeclaration() instanceof com.github.javaparser.ast.body.ConstructorDeclaration cDecl) {
                    candidateParamCount = cDecl.getParameters().size();
                } else {
                    continue;
                }

                int callArgCount = callExpr.getArguments().size();
                if (candidateParamCount == callArgCount) {
                    return candidate.getSignature();
                }
            }

            // Fallback to first method if no exact match
            if (!candidateMethods.isEmpty()) {
                return candidateMethods.get(0).getSignature();
            }
        }

        return null;
    }

    @Override
    public List<String> getCallStack(String methodSignature) {
        List<String> callStack = new ArrayList<>();
        Set<String> visited = ConcurrentHashMap.newKeySet();
        buildCallStack(methodSignature, callStack, visited);
        return callStack;
    }

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

    @Override
    public Optional<MethodInfo> findMethodInfo(String methodSignature) {
        // Extract class name from method signature
        int hashIndex = methodSignature.indexOf('#');
        if (hashIndex == -1) {
            return Optional.empty();
        }
        String className = methodSignature.substring(0, hashIndex);
        ClassInfo classInfo = indexBuilder.getClassInfoMap().get(className);
        if (classInfo == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(classInfo.getMethods().get(methodSignature));
    }
}
