package uk.gov.moj.cpp.service.insights.service;

import uk.gov.moj.cpp.service.insights.indexer.IndexBuilderImpl;
import uk.gov.moj.cpp.service.insights.model.ClassInfo;
import uk.gov.moj.cpp.service.insights.model.MethodInfo;
import uk.gov.moj.cpp.service.insights.resolver.CallGraphResolver;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;

public class MethodStackTracerServiceImpl implements MethodStackTracerService {

    private final IndexBuilderImpl indexBuilder;
    private final CallGraphResolver callGraphResolver;

    public MethodStackTracerServiceImpl(IndexBuilderImpl indexBuilder, CallGraphResolver callGraphResolver) {
        this.indexBuilder = indexBuilder;
        this.callGraphResolver = callGraphResolver;
    }

    @Override
    public void buildIndex(List<Path> sourcePaths) throws IOException {
        indexBuilder.buildIndex(sourcePaths);
        callGraphResolver.resolveCallGraph();
    }

    @Override
    public Optional<ClassInfo> getClassInfo(String className) {
        return indexBuilder.getClassInfo(className);
    }

    @Override
    public String getMethodSignature(String className, String methodName) {
        return indexBuilder.getMethodSignature(className, methodName);
    }

    @Override
    public List<String> getMethodStack(String methodSignature) {
        return callGraphResolver.getCallStack(methodSignature);
    }

    @Override
    public Optional<String> getMethodBody(String methodSignature) {
        Optional<MethodInfo> methodInfoOpt = callGraphResolver.findMethodInfo(methodSignature);
        if (methodInfoOpt.isEmpty()) {
            return Optional.empty();
        }

        BodyDeclaration<?> bodyDecl = methodInfoOpt.get().getMethodDeclaration();

        if (bodyDecl instanceof com.github.javaparser.ast.body.MethodDeclaration methodDecl) {
            Optional<String> body = methodDecl.getBody().map(BlockStmt::toString);
            if (body.isPresent()) {
                return body;
            } else {
                // Likely an abstract method or interface method
                // Attempt to find the implementing class's method body
                return findImplementingMethodBody(methodSignature);
            }
        } else if (bodyDecl instanceof com.github.javaparser.ast.body.ConstructorDeclaration constructorDecl) {
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
        Set<String> implementingClasses = indexBuilder.getInterfaceImplMap().getOrDefault(interfaceName, Set.of());

        if (implementingClasses.isEmpty()) {
            return Optional.empty();
        }

        // For simplicity, handle the case with exactly one implementation
        if (implementingClasses.size() == 1) {
            String implClass = implementingClasses.iterator().next();
            String implMethodSignature = implClass + "#" + methodPart;
            Optional<MethodInfo> implMethodInfoOpt = callGraphResolver.findMethodInfo(implMethodSignature);
            if (implMethodInfoOpt.isPresent()) {
                BodyDeclaration<?> implBodyDecl = implMethodInfoOpt.get().getMethodDeclaration();
                if (implBodyDecl instanceof com.github.javaparser.ast.body.MethodDeclaration implMethodDecl) {
                    return implMethodDecl.getBody().map(BlockStmt::toString);
                } else if (implBodyDecl instanceof com.github.javaparser.ast.body.ConstructorDeclaration implConstructorDecl) {
                    return Optional.of(implConstructorDecl.getBody().toString());
                }
            }
        }

        return Optional.empty();
    }

}
