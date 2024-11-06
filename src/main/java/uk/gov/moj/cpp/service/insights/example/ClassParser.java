package uk.gov.moj.cpp.service.insights.example;


import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Optional;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;

public class ClassParser {

    private final String projectDir;
    private final JavaParser javaParser = new JavaParser();

    public ClassParser(String projectDir) {
        this.projectDir = projectDir;
    }

    /**
     * Parses the Java source file for the given class name.
     *
     * @param className Fully qualified class name.
     * @return Optional containing the CompilationUnit if parsing is successful.
     */
    public Optional<CompilationUnit> parseClass(String className) {
        String classPath = className.replace('.', '/') + ".java";
        String fullPath = Paths.get(projectDir, classPath).toString();
        try (FileInputStream fis = new FileInputStream(fullPath)) {
            // Correct: Static method call
            ParseResult<CompilationUnit> result = javaParser.parse(fis);
            if (result.isSuccessful() && result.getResult().isPresent()) {
                return Optional.of(result.getResult().get());
            } else {
                System.err.println("Failed to parse class: " + className);
                return Optional.empty();
            }
        } catch (IOException e) {
            System.err.println("Error reading class file: " + className);
            return Optional.empty();
        }
    }

    public Optional<MethodDeclaration> getMethod(final CompilationUnit cu, final String methodName) {
        return cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals(methodName));
    }
}

