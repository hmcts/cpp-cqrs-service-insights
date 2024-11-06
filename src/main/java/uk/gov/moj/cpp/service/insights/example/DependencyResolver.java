package uk.gov.moj.cpp.service.insights.example;


import java.util.Optional;

import com.github.javaparser.ast.CompilationUnit;

public class DependencyResolver {

    private final ClassParser classParser;

    public DependencyResolver(ClassParser classParser) {
        this.classParser = classParser;
    }

    /**
     * Resolves an interface type to its implementation class name.
     * This implementation assumes that the implementation class is named as the interface with 'Impl' suffix.
     * Adjust this method based on your project's naming conventions or use a more sophisticated approach.
     */
    public Optional<String> resolveImplementation(String interfaceName) {
        String implClassName = interfaceName + "Impl";
        CompilationUnit cu = classParser.parseClass(implClassName).orElse(null);
        if (cu != null) {
            return Optional.of(implClassName);
        }
        return Optional.empty();
    }
}

