package uk.gov.moj.cpp.service.insights.model;

import com.github.javaparser.ast.body.BodyDeclaration;

public class MethodInfo {
    private final String signature;
    private final BodyDeclaration<?> methodDeclaration;

    public MethodInfo(String signature, BodyDeclaration<?> methodDeclaration) {
        this.signature = signature;
        this.methodDeclaration = methodDeclaration;
    }

    public String getSignature() {
        return signature;
    }

    public BodyDeclaration<?> getMethodDeclaration() {
        return methodDeclaration;
    }
}
