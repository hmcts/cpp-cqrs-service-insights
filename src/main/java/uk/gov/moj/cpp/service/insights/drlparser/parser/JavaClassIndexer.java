package uk.gov.moj.cpp.service.insights.drlparser.parser;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;

/**
 * Builds an index of Java classes, including their static methods and variables.
 */
public class JavaClassIndexer {

    private final Map<String, ClassInfo> classInfoMap = new ConcurrentHashMap<>();

    /**
     * Builds an index from the specified source directories.
     *
     * @param sourcePaths List of source directories to index.
     * @throws IOException If an I/O error occurs.
     */
    public void buildIndex(List<Path> sourcePaths) throws IOException {
        for (Path sourcePath : sourcePaths) {
            indexSourcePath(sourcePath);
        }
    }

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

        Set<String> staticImports = new HashSet<>();
        for (var importDecl : cu.getImports()) {
            if (importDecl.isStatic()) {
                staticImports.add(importDecl.getNameAsString());
            }
        }

        for (var typeDecl : cu.getTypes()) {
            processTypeDeclaration(typeDecl, packageName, staticImports);
        }
    }

    private void processTypeDeclaration(TypeDeclaration<?> typeDecl, String packageName, Set<String> staticImports) {
        String className = typeDecl.getNameAsString();
        String fullClassName = packageName.isEmpty() ? className : packageName + "." + className;

        Map<String, MethodDeclaration> methods = new HashMap<>();
        for (var methodDecl : typeDecl.getMethods()) {
            if (methodDecl.isStatic() && methodDecl.isPublic()) {
                methods.put(methodDecl.getNameAsString(), methodDecl);
            }
        }

        Map<String, FieldDeclaration> fields = new HashMap<>();
        for (var fieldDecl : typeDecl.getFields()) {
            if (fieldDecl.isStatic()) {
                for (var var : fieldDecl.getVariables()) {
                    fields.put(var.getNameAsString(), fieldDecl);
                }
            }
        }

        classInfoMap.put(fullClassName, new ClassInfo(fullClassName, methods, fields, new HashSet<>(staticImports)));

        // Process nested types
        for (var member : typeDecl.getMembers()) {
            if (member instanceof TypeDeclaration<?> nestedType) {
                processTypeDeclaration(nestedType, fullClassName, staticImports);
            }
        }
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
     * Represents information about a Java class.
     */
    public static class ClassInfo {
        private final String className;
        private final Map<String, MethodDeclaration> methods;
        private final Map<String, FieldDeclaration> fields;
        private final Set<String> staticImports;

        public ClassInfo(String className, Map<String, MethodDeclaration> methods, Map<String, FieldDeclaration> fields, Set<String> staticImports) {
            this.className = className;
            this.methods = methods;
            this.fields = fields;
            this.staticImports = staticImports;
        }

        public Optional<MethodDeclaration> getMethod(String methodName) {
            return Optional.ofNullable(methods.get(methodName));
        }

        public Optional<FieldDeclaration> getField(String fieldName) {
            return Optional.ofNullable(fields.get(fieldName));
        }

        public Set<String> getStaticImports() {
            return staticImports;
        }
    }
}

