package uk.gov.moj.cpp.service.insights.service;

import uk.gov.moj.cpp.service.insights.model.ClassInfo;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public interface MethodStackTracerService {
    void buildIndex(List<Path> sourcePaths) throws IOException;

    Optional<ClassInfo> getClassInfo(String className);

    String getMethodSignature(String className, String methodName);

    /**
     * Retrieves the entire method stack for the given method signature.
     *
     * @param methodSignature The fully qualified method signature.
     * @return A list representing the method stack.
     */
    List<String> getMethodStack(String methodSignature);

    /**
     * Retrieves the body of a method given its signature.
     *
     * @param methodSignature The fully qualified method signature.
     * @return Optional containing the method body as a String.
     */
    Optional<String> getMethodBody(String methodSignature);
}
