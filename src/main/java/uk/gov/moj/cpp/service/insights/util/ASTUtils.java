package uk.gov.moj.cpp.service.insights.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.stmt.BlockStmt;

/**
 * Utility class for Abstract Syntax Tree (AST) operations.
 * Provides helper methods to parse and analyze Java code structures.
 * This class is immutable and cannot be instantiated.
 */
public final class ASTUtils {

    /**
     * Private constructor to prevent instantiation.
     */
    private ASTUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated.");
    }

    /**
     * Builds a map of simple class names to fully qualified names from import declarations.
     *
     * @param compilationUnit The CompilationUnit to process.
     * @return An unmodifiable map where keys are simple class names and values are fully qualified names.
     */
    public static Map<String, String> buildImportMap(CompilationUnit compilationUnit) {
        Map<String, String> importMap = new HashMap<>();
        compilationUnit.getImports().forEach(importDeclaration -> {
            String imported = importDeclaration.getNameAsString();
            if (!importDeclaration.isAsterisk()) {
                String simpleName = getSimpleName(imported);
                importMap.put(simpleName, imported);
            }
        });
        return Map.copyOf(importMap);
    }

    /**
     * Checks if a node has any of the specified annotations.
     *
     * @param node               The node to check for annotations.
     * @param annotationNamesSet A set of annotation names (simple or fully qualified) to look for.
     * @return {@code true} if any annotation matches; {@code false} otherwise.
     */
    public static boolean hasAnyAnnotation(NodeWithAnnotations<?> node, Set<String> annotationNamesSet) {
        return node.getAnnotations().stream()
                .anyMatch(annotation -> {
                    String simpleName = annotation.getNameAsString();
                    String qualifiedName = getQualifiedName(annotation);
                    return annotationNamesSet.contains(simpleName) || annotationNamesSet.contains(qualifiedName);
                });
    }

    /**
     * Checks if any constructor of a class has parameters (implying normal constructor injection)
     * or is annotated with specific injection annotations (e.g., @Inject).
     *
     * @param constructors       The list of constructors to inspect.
     * @param annotationNamesSet A set of annotation names (simple or fully qualified) indicating injection.
     * @return {@code true} if any constructor meets the injection criteria; {@code false} otherwise.
     */
    public static boolean hasConstructorInjection(List<ConstructorDeclaration> constructors, Set<String> annotationNamesSet) {
        return constructors.stream().anyMatch(constructor -> {
            boolean hasParameters = !constructor.getParameters().isEmpty();
            boolean hasInjectionAnnotation = constructor.getAnnotations().stream()
                    .anyMatch(annotation -> {
                        String simpleName = annotation.getNameAsString();
                        String qualifiedName = getQualifiedName(annotation);
                        return annotationNamesSet.contains(simpleName) || annotationNamesSet.contains(qualifiedName);
                    });
            return hasParameters || hasInjectionAnnotation;
        });
    }

    /**
     * Resolves a type name to its fully qualified name using the import map and known classes.
     *
     * @param typeName     The type name to resolve.
     * @param packageName  The current package name.
     * @param importMap    A map of simple class names to fully qualified names.
     * @param classInfoMap A map of known classes (keys are fully qualified names).
     * @return The fully qualified type name, or {@code null} if unresolved.
     */
    public static String resolveType(String typeName, String packageName, Map<String, String> importMap, Map<String, ?> classInfoMap) {
        if (typeName == null || typeName.isBlank()) {
            return null;
        }

        // Handle generic types by stripping type parameters
        if (typeName.contains("<")) {
            typeName = typeName.substring(0, typeName.indexOf('<'));
        }

        // If typeName is already fully qualified, verify its existence
        if (classInfoMap.containsKey(typeName)) {
            return typeName;
        }

        // If typeName is simple and present in imports, return the imported fully qualified name
        if (importMap.containsKey(typeName)) {
            return importMap.get(typeName);
        }

        // If type is in the same package, construct its fully qualified name
        String fqName = packageName.isEmpty() ? typeName : packageName + "." + typeName;
        if (classInfoMap.containsKey(fqName)) {
            return fqName;
        }

        // Handle nested class names (e.g., Outer.Inner)
        if (typeName.contains(".")) {
            if (classInfoMap.containsKey(typeName)) {
                return typeName;
            }
        }

        // As a last resort, perform a wildcard search
        String finalClassName = typeName;
        List<String> matches = classInfoMap.keySet().stream()
                .filter(k -> k.endsWith("." + finalClassName))
                .collect(Collectors.toList());

        if (matches.size() == 1) {
            return matches.get(0);
        }

        return fqName;
    }

    /**
     * Collects all method calls within a block statement.
     *
     * @param block The block statement to analyze.
     * @return An unmodifiable list of MethodCallExpr found within the block.
     */
    public static List<MethodCallExpr> collectMethodCalls(BlockStmt block) {
        List<MethodCallExpr> methodCalls = new ArrayList<>();
        block.walk(MethodCallExpr.class, methodCalls::add);
        return List.copyOf(methodCalls);
    }

    /**
     * Collects field assignments in a constructor body.
     *
     * @param body The constructor body to analyze.
     * @return An unmodifiable map where keys are field names and values are parameter names.
     */
    public static Map<String, String> collectFieldAssignments(BlockStmt body) {
        Map<String, String> fieldAssignments = new HashMap<>();

        body.walk(AssignExpr.class, assignExpr -> {
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
        });

        return Map.copyOf(fieldAssignments);
    }

    /**
     * Retrieves the simple class name from a fully qualified class name.
     *
     * @param qualifiedName The fully qualified class name.
     * @return The simple class name.
     */
    private static String getSimpleName(String qualifiedName) {
        return qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
    }

    /**
     * Retrieves the fully qualified name of an annotation.
     *
     * @param annotation The annotation expression.
     * @return The fully qualified name if present; otherwise, the simple name.
     */
    private static String getQualifiedName(com.github.javaparser.ast.expr.AnnotationExpr annotation) {
        String simpleName = annotation.getNameAsString();
        if (annotation.getName().getQualifier().isPresent()) {
            return annotation.getName().getQualifier().get().asString() + "." + simpleName;
        }
        return simpleName;
    }
}
