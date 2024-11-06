package uk.gov.moj.cpp.service.insights.example;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;

public class MethodAnalyzer {

    private final ClassParser classParser;
    private final DependencyResolver dependencyResolver;
    private final StaticVariableReplacer staticVariableReplacer;

    public MethodAnalyzer(ClassParser classParser,
                          DependencyResolver dependencyResolver,
                          StaticVariableReplacer staticVariableReplacer) {
        this.classParser = classParser;
        this.dependencyResolver = dependencyResolver;
        this.staticVariableReplacer = staticVariableReplacer;
    }

    public Set<String> analyze(String className, String methodName, int maxDepth) {
        Set<String> methodStack = new HashSet<>();
        analyzeRecursive(className, methodName, maxDepth, methodStack);
        return methodStack;
    }

    private void analyzeRecursive(String className, String methodName, int depth, Set<String> methodStack) {
        if (depth <= 0) {
            return;
        }

        Optional<CompilationUnit> cuOpt = classParser.parseClass(className);
        if (!cuOpt.isPresent()) {
            return;
        }

        CompilationUnit cu = cuOpt.get();
        Optional<MethodDeclaration> methodOpt = classParser.getMethod(cu, methodName);
        if (!methodOpt.isPresent()) {
            return;
        }

        MethodDeclaration method = methodOpt.get();
        String methodBody = method.getBody().map(Object::toString).orElse("");
        methodBody = staticVariableReplacer.replace(methodBody);

        // Parse method calls
        method.findAll(MethodCallExpr.class).forEach(call -> {
            String calledMethodName = call.getNameAsString();
            methodStack.add(calledMethodName);

            // Resolve the scope to determine the class
            Optional<com.github.javaparser.ast.expr.Expression> scopeOpt = call.getScope();
            final AtomicReference<String> calledClassName = new AtomicReference<>(className); // Default to current class

            if (scopeOpt.isPresent()) {
                String scope = scopeOpt.get().toString();
                // Attempt to resolve if scope is a class name or an interface
                // For simplicity, assume scope is a class name
                calledClassName.set(scope);
                // If it's an interface, resolve to implementation
                Optional<String> implClassOpt = dependencyResolver.resolveImplementation(scope);
                implClassOpt.ifPresent(implClassName -> calledClassName.set(implClassName));
            }

            analyzeRecursive(calledClassName.get(), calledMethodName, depth - 1, methodStack);
        });
    }
}

