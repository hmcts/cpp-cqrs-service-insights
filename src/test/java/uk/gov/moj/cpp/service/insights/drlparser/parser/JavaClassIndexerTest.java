package uk.gov.moj.cpp.service.insights.drlparser.parser;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JavaClassIndexerTest {

    private JavaClassIndexer indexer;

    @BeforeEach
    void setUp() throws Exception {
        indexer = new JavaClassIndexer();
        // Index sample source directory containing Java classes for testing
        // Ensure you have sample Java files in src/test/resources/java for testing
        indexer.buildIndex(List.of(Paths.get("src/test/resources/java")));
    }

    @Test
    void testGetClassInfo() {
        Optional<JavaClassIndexer.ClassInfo> classInfoOpt = indexer.getClassInfo("uk.gov.moj.cpp.SampleClass");
        assertTrue(classInfoOpt.isPresent(), "SampleClass should be indexed");

        JavaClassIndexer.ClassInfo classInfo = classInfoOpt.get();
        assertNotNull(classInfo);

        // Test methods
        Optional<com.github.javaparser.ast.body.MethodDeclaration> methodOpt = classInfo.getMethod("sampleMethod");
        assertTrue(methodOpt.isPresent(), "sampleMethod should exist in SampleClass");

        // Test fields
        Optional<com.github.javaparser.ast.body.FieldDeclaration> fieldOpt = classInfo.getField("SAMPLE_FIELD");
        assertTrue(fieldOpt.isPresent(), "SAMPLE_FIELD should exist in SampleClass");
    }

    @Test
    void testMissingClassInfo() {
        Optional<JavaClassIndexer.ClassInfo> classInfoOpt = indexer.getClassInfo("uk.gov.moj.cpp.NonExistentClass");
        assertFalse(classInfoOpt.isPresent(), "NonExistentClass should not be indexed");
    }
}
