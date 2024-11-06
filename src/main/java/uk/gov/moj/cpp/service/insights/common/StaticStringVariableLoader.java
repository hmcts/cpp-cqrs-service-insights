package uk.gov.moj.cpp.service.insights.common;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

/**
 * Utility to load all String type static variables from Java source files into a map.
 */
public class StaticStringVariableLoader {

    // Map to store fully qualified variable name to its value
    private final Map<String, String> staticStringVariables = new ConcurrentHashMap<>();

    /**
     * Example usage of the StaticStringVariableLoader.
     */
    public static void main(String[] args) {
        StaticStringVariableLoader loader = new StaticStringVariableLoader();
        List<Path> sourcePaths = Arrays.asList(
                Path.of("/Users/satishkumar/moj/software/cpp.context.results/results-event/results-event-processor")

        );

        try {
            loader.loadStaticStringVariables(sourcePaths);
            Map<String, String> variables = loader.getStaticStringVariables();
            variables.forEach((key, value) -> {
                if (value != null) {
                    System.out.println(key + " = \"" + value + "\"");
                } else {
                    System.out.println(key + " = <Uninitialized or Non-Literal>");
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Starts the loading process given the list of source directories.
     *
     * @param sourcePaths List of paths to Java source directories.
     * @throws IOException If an I/O error occurs.
     */
    public void loadStaticStringVariables(List<Path> sourcePaths) throws IOException {
        for (Path sourcePath : sourcePaths) {
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
    }

    /**
     * Parses a single Java file and extracts static String variables.
     *
     * @param file Path to the Java file.
     * @throws IOException If an I/O error occurs.
     */
    private void parseJavaFile(Path file) throws IOException {
        CompilationUnit cu;
        try {
            cu = StaticJavaParser.parse(file);
        } catch (Exception e) {
            System.err.println("Failed to parse file: " + file);
            return;
        }

        String packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getNameAsString())
                .orElse("");

        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(ClassOrInterfaceDeclaration cid, Void arg) {
                String className = cid.getNameAsString();
                String fullyQualifiedClassName = packageName.isEmpty() ? className : packageName + "." + className;

                for (FieldDeclaration field : cid.getFields()) {
                    if (field.isStatic() && field.getElementType().isClassOrInterfaceType()) {
                        String type = field.getElementType().asClassOrInterfaceType().getNameAsString();
                        if ("String".equals(type)) {
                            for (VariableDeclarator var : field.getVariables()) {
                                String varName = var.getNameAsString();
                                Optional<Expression> initializer = var.getInitializer();
                                if (initializer.isPresent() && initializer.get() instanceof StringLiteralExpr) {
                                    String value = ((StringLiteralExpr) initializer.get()).getValue();
                                    String fullyQualifiedVarName = fullyQualifiedClassName + "." + varName;
                                    if (value.contains(".")) {
                                        staticStringVariables.put(fullyQualifiedVarName, value);
                                    }
                                }
                            }
                        }
                    }
                }

                super.visit(cid, arg);
            }

            @Override
            public void visit(EnumDeclaration ed, Void arg) {
                String className = ed.getNameAsString();
                String fullyQualifiedClassName = packageName.isEmpty() ? className : packageName + "." + className;

                for (FieldDeclaration field : ed.getFields()) {
                    if (field.isStatic() && field.getElementType().isClassOrInterfaceType()) {
                        String type = field.getElementType().asClassOrInterfaceType().getNameAsString();
                        if ("String".equals(type)) {
                            for (VariableDeclarator var : field.getVariables()) {
                                String varName = var.getNameAsString();
                                Optional<Expression> initializer = var.getInitializer();
                                if (initializer.isPresent() && initializer.get() instanceof StringLiteralExpr) {
                                    String value = ((StringLiteralExpr) initializer.get()).getValue();
                                    String fullyQualifiedVarName = fullyQualifiedClassName + "." + varName;
                                    if (value.contains(".")) {
                                        staticStringVariables.put(fullyQualifiedVarName, value);
                                    }
                                }
                            }
                        }
                    }
                }

                super.visit(ed, arg);
            }
        }, null);
    }

    /**
     * Retrieves the map of static String variables.
     *
     * @return Map of fully qualified variable names to their values.
     */
    public Map<String, String> getStaticStringVariables() {
        return staticStringVariables;
    }
}

