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

public class ASTUtils {

    /**
     * Builds a map of simple class names to fully qualified names from import declarations.
     *
     * @param cu CompilationUnit
     * @return Map of simple name to fully qualified name
     */
    public static Map<String, String> buildImportMap(CompilationUnit cu) {
        Map<String, String> importMap = new HashMap<>();
        cu.getImports().forEach(importDecl -> {
            String imported = importDecl.getNameAsString();
            if (!importDecl.isAsterisk()) {
                String simpleName = imported.substring(imported.lastIndexOf('.') + 1);
                importMap.put(simpleName, imported);
            }
        });
        return importMap;
    }

    /**
     * Checks if a node has any of the specified annotations.
     *
     * @param node               The node to check
     * @param annotationNamesSet Set of annotation names (simple or fully qualified)
     * @return true if any annotation matches, false otherwise
     */
    public static boolean hasAnyAnnotation(NodeWithAnnotations<?> node, Set<String> annotationNamesSet) {
        return node.getAnnotations().stream()
                .anyMatch(a -> {
                    // Get the simple name of the annotation
                    String simpleName = a.getNameAsString();

                    // Get the fully qualified name if present
                    String qualifiedName = simpleName;
                    if (a.getName().getQualifier().isPresent()) {
                        qualifiedName = a.getName().getQualifier().get().asString() + "." + simpleName;
                    }

                    // Check if either the simple or fully qualified name is in the set
                    return annotationNamesSet.contains(simpleName) || annotationNamesSet.contains(qualifiedName);
                });
    }

    /**
     * Checks if any constructor of a class has parameters (implying normal constructor injection)
     * or is annotated with specific injection annotations (e.g., @Inject).
     *
     * @param constructors       The list of constructors to check
     * @param annotationNamesSet Set of annotation names (simple or fully qualified, e.g., "Inject", "Autowired")
     * @return true if any constructor has parameters or is annotated with injection annotations, false otherwise
     */
    public static boolean hasConstructorInjection(List<ConstructorDeclaration> constructors, Set<String> annotationNamesSet) {
        return constructors.stream().anyMatch(constructor -> {
            // Check for parameters (normal constructor injection)
            boolean hasParameters = !constructor.getParameters().isEmpty();

            // Check if the constructor itself is annotated with any injection annotations
            boolean hasInjectionAnnotation = constructor.getAnnotations().stream()
                    .anyMatch(annotation -> {
                        String simpleName = annotation.getNameAsString();
                        String qualifiedName = simpleName;
                        if (annotation.getName().getQualifier().isPresent()) {
                            qualifiedName = annotation.getName().getQualifier().get().asString() + "." + simpleName;
                        }
                        // Check for both simple and fully qualified annotation names
                        return annotationNamesSet.contains(simpleName) || annotationNamesSet.contains(qualifiedName);
                    });

            // Return true if the constructor has parameters or has an injection annotation
            return hasParameters || hasInjectionAnnotation;
        });
    }


    /**
     * Resolves a type name to its fully qualified name using the import map and known classes.
     *
     * @param typeName     The type name to resolve
     * @param packageName  Current package name
     * @param importMap    Import map
     * @param classInfoMap Map of known classes
     * @return Fully qualified type name or null if unresolved
     */
    public static String resolveType(String typeName, String packageName, Map<String, String> importMap, Map<String, ?> classInfoMap) {
        if (typeName == null || typeName.isEmpty()) {
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
        final String finalClassName = typeName;
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
     * @param block The block statement
     * @return List of MethodCallExpr
     */
    public static List<MethodCallExpr> collectMethodCalls(BlockStmt block) {
        List<MethodCallExpr> methodCalls = new ArrayList<>();
        block.walk(MethodCallExpr.class, methodCalls::add);
        return methodCalls;
    }

    /**
     * Collects field assignments in a constructor body.
     *
     * @param body The constructor body
     * @return Map of field name to parameter name
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

        return fieldAssignments;
    }
}
