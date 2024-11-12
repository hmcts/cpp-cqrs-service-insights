package uk.gov.moj.cpp.service.insights.indexer;

import uk.gov.moj.cpp.service.insights.model.ClassInfo;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Interface for building an index of Java classes.
 */
public interface IndexBuilder {
    /**
     * Builds an index from the specified source directories.
     *
     * @param sourcePaths List of source directories to index.
     * @throws IOException If an I/O error occurs.
     */
    void buildIndex(List<Path> sourcePaths) throws IOException;

    /**
     * Retrieves ClassInfo for a given fully qualified class name.
     *
     * @param className Fully qualified class name.
     * @return Optional containing ClassInfo if found.
     */
    Optional<ClassInfo> getClassInfo(String className);

    /**
     * Generates a method signature based on class name, method name, and parameters.
     *
     * @param className  Fully qualified class name.
     * @param methodName Method name.

     * @return Method signature string.
     */
    String getMethodSignature(String className, String methodName);
}
