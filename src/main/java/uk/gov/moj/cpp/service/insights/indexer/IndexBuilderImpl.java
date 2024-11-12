package uk.gov.moj.cpp.service.insights.indexer;

import static java.lang.System.out;

import uk.gov.moj.cpp.service.insights.model.ClassInfo;
import uk.gov.moj.cpp.service.insights.model.DependencyInfo;
import uk.gov.moj.cpp.service.insights.model.MethodInfo;
import uk.gov.moj.cpp.service.insights.parser.JavaFileParser;
import uk.gov.moj.cpp.service.insights.util.ASTUtils;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

public class IndexBuilderImpl implements IndexBuilder {

    private static final Set<String> INJECTION_ANNOTATIONS = Set.of(
            "Inject", "Autowired", "Resource", "javax.inject.Inject"
    );

    private final JavaFileParser parser;

    // Map of fully qualified class name to ClassInfo
    private final Map<String, ClassInfo> classInfoMap = new ConcurrentHashMap<>();

    // Map of interface name to implementing class names
    private final Map<String, Set<String>> interfaceImplMap = new ConcurrentHashMap<>();

    // Assuming callGraphResolver is a field that needs to be defined and initialized
    private final CallGraphResolver callGraphResolver = new CallGraphResolver();

    public IndexBuilderImpl(JavaFileParser parser) {
        this.parser = parser;
    }

    @Override
    public void buildIndex(List<Path> sourcePaths) throws IOException {
        for (Path sourcePath : sourcePaths) {
            indexSourcePath(sourcePath);
        }
        resolveInterfaceImplementations();
        resolveInheritedMethods(); // New method to resolve inherited methods
    }

    private void indexSourcePath(Path sourcePath) throws IOException {
        Files.walkFileTree(sourcePath, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {

                if (file.toString().endsWith(".java") && !file.getFileName().toString().endsWith("Test.java")) {
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
        CompilationUnit cu = parser.parse(file);

        String packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getNameAsString())
                .orElse("");

        // Build import map: simple name -> fully qualified name
        Map<String, String> importMap = ASTUtils.buildImportMap(cu);

        for (TypeDeclaration<?> typeDecl : cu.getTypes()) {
            processTypeDeclaration(typeDecl, packageName, null, importMap);
        }
    }

    private void processTypeDeclaration(TypeDeclaration<?> typeDecl, String packageName, String parentClass, Map<String, String> importMap) {
        String className = typeDecl.getNameAsString();
        String fullClassName = parentClass == null
                ? (packageName.isEmpty() ? className : packageName + "." + className)
                : parentClass + "$" + className; // For nested classes

        // Initialize ClassInfo
        ClassInfo classInfo = classInfoMap.computeIfAbsent(fullClassName, k -> new ClassInfo(fullClassName, packageName, importMap));

        if (typeDecl instanceof ClassOrInterfaceDeclaration coiDecl) {
            // Handle superclass relationships
            if (coiDecl.getExtendedTypes().size() > 0) {
                for (ClassOrInterfaceType superClass : coiDecl.getExtendedTypes()) {
                    String superClassName = superClass.getNameWithScope();
                    String superClassFullName = resolveFullyQualifiedClassName(superClassName, packageName, importMap);
                    if (superClassFullName != null) {
                        classInfo.setSuperClass(superClassFullName);
                    }
                }
            }
            if (coiDecl.isInterface()) {
                // If it's an interface, ensure it's in the interfaceImplMap with an empty set
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

            // Process fields
            processFields(coiDecl, classInfo, packageName, importMap);

            // Process methods
            processMethods(coiDecl, classInfo, fullClassName);

            // Process constructors
            processConstructors(coiDecl, classInfo, packageName, importMap, fullClassName);
        }

        // Process nested types
        for (BodyDeclaration<?> member : typeDecl.getMembers()) {
            if (member instanceof TypeDeclaration<?> nestedType) {
                processTypeDeclaration(nestedType, packageName, fullClassName, importMap);
            }
        }
    }

    private void processFields(ClassOrInterfaceDeclaration coiDecl, ClassInfo classInfo, String packageName, Map<String, String> importMap) {
        for (FieldDeclaration fieldDecl : coiDecl.getFields()) {
            boolean isInjected = ASTUtils.hasAnyAnnotation(fieldDecl, INJECTION_ANNOTATIONS);
            for (VariableDeclarator var : fieldDecl.getVariables()) {
                String fieldName = var.getNameAsString();
                String fieldType = ASTUtils.resolveType(var.getType().asString(), packageName, importMap, classInfoMap);
                classInfo.addDependency(new DependencyInfo(fieldName, fieldType, isInjected, true));
            }
        }
    }

    private void processMethods(ClassOrInterfaceDeclaration coiDecl, ClassInfo classInfo, String fullClassName) {

        for (MethodDeclaration methodDecl : coiDecl.getMethods()) {
            String methodSignature = generateMethodSignature(fullClassName, methodDecl);
            MethodInfo methodInfo = new MethodInfo(methodSignature, methodDecl);
            classInfo.addMethod(methodInfo);
        }
    }

    private void processConstructors(ClassOrInterfaceDeclaration coiDecl, ClassInfo classInfo, String packageName,
                                     Map<String, String> importMap, String fullClassName) {
        for (ConstructorDeclaration constructorDecl : coiDecl.getConstructors()) {
            String constructorSignature = generateConstructorSignature(fullClassName, constructorDecl);
            MethodInfo methodInfo = new MethodInfo(constructorSignature, constructorDecl);
            classInfo.addMethod(methodInfo); // Treating constructors as methods

            boolean isConstructorInjected = ASTUtils.hasAnyAnnotation(constructorDecl, INJECTION_ANNOTATIONS);
            if (!isConstructorInjected) {
                isConstructorInjected = ASTUtils.hasConstructorInjection(coiDecl.getConstructors(), INJECTION_ANNOTATIONS);
            }
            // Add all constructor parameters as dependencies
            for (Parameter param : constructorDecl.getParameters()) {
                String paramName = param.getNameAsString();
                String paramType = ASTUtils.resolveType(param.getType().asString(), packageName, importMap, classInfoMap);
                classInfo.addDependency(new DependencyInfo(paramName, paramType, isConstructorInjected, false));
            }

            // Process assignments in constructor body
            if (constructorDecl.getBody() != null) {
                Map<String, String> fieldAssignments = ASTUtils.collectFieldAssignments(constructorDecl.getBody());
                for (Map.Entry<String, String> entry : fieldAssignments.entrySet()) {
                    String fieldName = entry.getKey();
                    String paramName = entry.getValue();
                    Optional<DependencyInfo> paramDepOpt = classInfo.getDependency(paramName);
                    if (paramDepOpt.isPresent()) {
                        String paramType = paramDepOpt.get().getType();
                        classInfo.addDependency(new DependencyInfo(fieldName, paramType, isConstructorInjected, true));
                    }
                }
            }
        }
    }

    private String generateMethodSignature(String className, MethodDeclaration methodDecl) {
        String params = methodDecl.getParameters().stream()
                .map(p -> p.getType().asString())
                .collect(Collectors.joining(","));
        return className + "#" + methodDecl.getNameAsString() + "(" + params + ")";
    }

    private String generateConstructorSignature(String className, ConstructorDeclaration constructorDecl) {
        String params = constructorDecl.getParameters().stream()
                .map(p -> p.getType().asString())
                .collect(Collectors.joining(","));
        return className + "#<init>(" + params + ")";
    }

    public String resolveFullyQualifiedClassName(String className, String packageName, Map<String, String> importMap) {
        return ASTUtils.resolveType(className, packageName, importMap, classInfoMap);
    }

    /**
     * Resolves dependencies that are interfaces by mapping them to their implementing classes.
     * If an interface has exactly one implementation, updates the dependency type to the implementing class.
     * If multiple implementations exist, logs a warning.
     * If no implementations are found, logs a warning.
     */
    private void resolveInterfaceImplementations() {
        for (ClassInfo classInfo : classInfoMap.values()) {
            for (DependencyInfo dependency : classInfo.getDependencies()) {
                String depType = dependency.getType();
                // Check if depType is an interface by checking if it exists in interfaceImplMap
                if (depType != null && interfaceImplMap.containsKey(depType)) {
                    Set<String> implClasses = interfaceImplMap.get(depType);
                    if (implClasses.size() == 1) {
                        String implClass = implClasses.iterator().next();
                        dependency.setType(implClass);
                        dependency.addImplementingClass(implClass);
                        out.println("Resolved interface dependency: " + depType + " to " + implClass);
                    } else if (implClasses.size() > 1) {
                        // Handle multiple implementations, possibly by selecting one or marking as ambiguous
                        System.err.println("Multiple implementations found for interface: " + depType + ". Dependency resolution ambiguous for dependency: " + dependency.getName());
                        dependency.addImplementingClass("AMBIGUOUS");
                    } else {
                        // No implementations found
                        System.err.println("No implementations found for interface: " + depType + ". Dependency resolution failed for dependency: " + dependency.getName());
                        dependency.addImplementingClass("UNRESOLVED");
                    }
                }
            }
        }
    }

    /**
     * Resolves and aggregates inherited methods from superclasses into each ClassInfo.
     */
    private void resolveInheritedMethods() {
        for (ClassInfo classInfo : classInfoMap.values()) {
            String superClass = classInfo.getSuperclassName();
            Set<String> visitedClasses = new HashSet<>();
            while (superClass != null && !visitedClasses.contains(superClass)) {
                visitedClasses.add(superClass);
                ClassInfo superClassInfo = classInfoMap.get(superClass);
                if (superClassInfo != null) {
                    // Add methods from superclass if not already present
                    for (MethodInfo method : superClassInfo.getMethods().values()) {
                        if (!classInfo.hasMethod(method.getSignature())) {
                            classInfo.addInheritedMethod(method);
                        }
                    }
                    // Move up the hierarchy
                    superClass = superClassInfo.getSuperclassName();
                } else {
                    // Superclass not found in index, possibly java.lang.Object or external library
                    // Optionally, handle external superclasses here
                    superClass = null;
                }
            }
        }
    }

    @Override
    public Optional<ClassInfo> getClassInfo(String className) {
        return Optional.ofNullable(classInfoMap.get(className));
    }

    @Override
    public String getMethodSignature(String className, String methodName) {
        return className + "#" + methodName ;
    }

    // Additional getter for classInfoMap if needed
    public Map<String, ClassInfo> getClassInfoMap() {
        return classInfoMap;
    }

    /**
     * Retrieves the body of a method given its signature.
     * If the method is part of an interface, retrieves the body from the implementing class.
     *
     * @param methodSignature The signature of the method.
     * @return Optional containing the method body as a String.
     */
    public Optional<String> getMethodBody(String methodSignature) {
        Optional<MethodInfo> methodInfoOpt = callGraphResolver.findMethodInfo(methodSignature);
        if (methodInfoOpt.isEmpty()) {
            return Optional.empty();
        }

        BodyDeclaration<?> bodyDecl = methodInfoOpt.get().getMethodDeclaration();

        if (bodyDecl instanceof MethodDeclaration methodDecl) {
            Optional<String> body = methodDecl.getBody().map(v -> v.toString());
            if (body.isPresent()) {
                return body;
            } else {
                // Likely an abstract method or interface method
                // Attempt to find the implementing class's method body
                return findImplementingMethodBody(methodSignature);
            }
        } else if (bodyDecl instanceof ConstructorDeclaration constructorDecl) {
            return Optional.of(constructorDecl.getBody().toString());
        }

        return Optional.empty();
    }

    /**
     * Finds the method body from the implementing class for a given interface method signature.
     *
     * @param interfaceMethodSignature The method signature from the interface.
     * @return Optional containing the implementing class's method body if found.
     */
    private Optional<String> findImplementingMethodBody(String interfaceMethodSignature) {
        // Parse the interface method signature
        // Expected format: "com.example.Interface#methodName(paramType1,paramType2,...)"
        int hashIndex = interfaceMethodSignature.indexOf('#');
        if (hashIndex == -1) {
            return Optional.empty();
        }

        String interfaceName = interfaceMethodSignature.substring(0, hashIndex);
        String methodPart = interfaceMethodSignature.substring(hashIndex + 1); // "methodName(paramType1,paramType2,...)"

        // Find implementing classes
        Set<String> implementingClasses = interfaceImplMap.getOrDefault(interfaceName, Set.of());

        if (implementingClasses.isEmpty()) {
            System.err.println("No implementations found for interface: " + interfaceName);
            return Optional.empty();
        }

        // For simplicity, handle the case with exactly one implementation
        if (implementingClasses.size() == 1) {
            String implClass = implementingClasses.iterator().next();
            String implMethodSignature = implClass + "#" + methodPart;
            Optional<MethodInfo> implMethodInfoOpt = callGraphResolver.findMethodInfo(implMethodSignature);
            if (implMethodInfoOpt.isPresent()) {
                BodyDeclaration<?> implBodyDecl = implMethodInfoOpt.get().getMethodDeclaration();
                if (implBodyDecl instanceof MethodDeclaration implMethodDecl) {
                    return implMethodDecl.getBody().map(v -> v.toString());
                } else if (implBodyDecl instanceof ConstructorDeclaration implConstructorDecl) {
                    return Optional.of(implConstructorDecl.getBody().toString());
                }
            } else {
                System.err.println("Implementing method not found: " + implMethodSignature);
            }
        } else {
            // Handle multiple implementations
            // This could involve selecting a default implementation, logging a warning, etc.
            // For now, we'll log a warning and return empty
            System.err.println("Multiple implementations found for interface: " + interfaceName + ". Cannot determine which implementation to use for method: " + methodPart);
        }

        return Optional.empty();
    }

    public Map<String, Set<String>> getInterfaceImplMap() {
        return interfaceImplMap;
    }

    // Mock implementation of CallGraphResolver for completeness
    // Replace this with your actual implementation
    private static class CallGraphResolver {
        public Optional<MethodInfo> findMethodInfo(String methodSignature) {
            // Implementation to find MethodInfo based on methodSignature
            // This could involve looking up in classInfoMap or another data structure
            // For demonstration, return empty
            return Optional.empty(); // Placeholder
        }
    }
}
