package uk.gov.moj.cpp.service.insights.indexer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import uk.gov.moj.cpp.service.insights.model.ClassInfo;
import uk.gov.moj.cpp.service.insights.model.DependencyInfo;
import uk.gov.moj.cpp.service.insights.parser.JavaFileParser;
import uk.gov.moj.cpp.service.insights.parser.JavaFileParserImpl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IndexBuilderImplTest {

    private IndexBuilderImpl indexBuilder;
    private JavaFileParser javaFileParser;

    @BeforeEach
    void setUp() {
        javaFileParser = new JavaFileParserImpl();
        indexBuilder = new IndexBuilderImpl(javaFileParser);
    }

    @Test
    void buildIndex_ShouldPopulateClassInfoMap_Have_Inheritance_Relation(@TempDir Path tempDir) throws IOException {
        // Arrange

        // Create TestClass.java
        Path testClassFile = tempDir.resolve("TestClass.java");
        String testClassContent = """
                package com.example;
                
                import javax.inject.Inject;
                
                public class TestClass {
                
                    @Inject
                    private DependencyClass dependency;
                
                    public void performAction() {
                        dependency.execute();
                        helperMethod();
                    }
                
                    private void helperMethod() {
                        // Helper method logic
                    }
                }
                """;
        Files.writeString(testClassFile, testClassContent);

        // Create DependencyClass.java
        Path dependencyClassFile = tempDir.resolve("DependencyClass.java");
        String dependencyClassContent = """
                package com.example;
                
                public class DependencyClass {
                    public void execute() {
                        // Execution logic
                        System.out.println("Executing dependency.");
                    }
                }
                """;
        Files.writeString(dependencyClassFile, dependencyClassContent);

        // Create BaseClass.java
        Path baseClassFile = tempDir.resolve("BaseClass.java");
        String baseClassContent = """
                package com.example;
                
                public class BaseClass {
                    public void baseMethod() {
                        // Base method logic
                    }
                }
                """;
        Files.writeString(baseClassFile, baseClassContent);

        // Create SubClassA.java that extends BaseClass
        Path subClassAFile = tempDir.resolve("SubClassA.java");
        String subClassAContent = """
                package com.example;
                
                public class SubClassA extends BaseClass {
                    public void methodA() {
                        // Method A logic
                    }
                }
                """;
        Files.writeString(subClassAFile, subClassAContent);

        // Create SubClassB.java that extends BaseClass
        Path subClassBFile = tempDir.resolve("SubClassB.java");
        String subClassBContent = """
                package com.example;
                
                public class SubClassB extends BaseClass {
                    public void methodB() {
                        // Method B logic
                    }
                }
                """;
        Files.writeString(subClassBFile, subClassBContent);

        List<Path> sourcePaths = Collections.singletonList(tempDir);

        // Act
        indexBuilder.buildIndex(sourcePaths);

        // Assert

        // Verify TestClass
        Optional<ClassInfo> testClassOpt = indexBuilder.getClassInfo("com.example.TestClass");
        assertTrue(testClassOpt.isPresent(), "ClassInfo should be present for TestClass");
        ClassInfo testClassInfo = testClassOpt.get();
        assertEquals("com.example.TestClass", testClassInfo.getClassName(), "Class name should match");
        assertEquals("com.example", testClassInfo.getPackageName(), "Package name should match");
        assertEquals(2, testClassInfo.getMethods().size(), "TestClass should have three methods (including constructor)");
        assertTrue(testClassInfo.getMethods().containsKey("com.example.TestClass#performAction()"), "Method performAction should be present");
        assertTrue(testClassInfo.getMethods().containsKey("com.example.TestClass#helperMethod()"), "Method helperMethod should be present");

        // Verify DependencyClass
        Optional<ClassInfo> depClassInfoOpt = indexBuilder.getClassInfo("com.example.DependencyClass");
        assertTrue(depClassInfoOpt.isPresent(), "ClassInfo should be present for DependencyClass");
        ClassInfo depClassInfo = depClassInfoOpt.get();
        assertEquals("com.example.DependencyClass", depClassInfo.getClassName(), "DependencyClass name should match");
        assertEquals(1, depClassInfo.getMethods().size(), "DependencyClass should have one method");
        assertTrue(depClassInfo.getMethods().containsKey("com.example.DependencyClass#execute()"), "Method execute should be present");

        // Check Dependency type in TestClass
        Optional<DependencyInfo> depOpt = testClassInfo.getDependency("dependency");
        assertTrue(depOpt.isPresent(), "Dependency 'dependency' should be present");
        DependencyInfo dependencyInfo = depOpt.get();
        assertEquals("com.example.DependencyClass", dependencyInfo.getType(), "Dependency type should match");
        assertTrue(dependencyInfo.isInjected(), "Dependency should be marked as injected");
        assertTrue(dependencyInfo.isField(), "Dependency should be a field");

        // Verify BaseClass
        Optional<ClassInfo> baseClassOpt = indexBuilder.getClassInfo("com.example.BaseClass");
        assertTrue(baseClassOpt.isPresent(), "ClassInfo should be present for BaseClass");
        ClassInfo baseClassInfo = baseClassOpt.get();
        assertEquals("com.example.BaseClass", baseClassInfo.getClassName(), "BaseClass name should match");
        assertEquals(1, baseClassInfo.getMethods().size(), "BaseClass should have one method");
        assertTrue(baseClassInfo.getMethods().containsKey("com.example.BaseClass#baseMethod()"), "Method baseMethod should be present");

        // Verify SubClassA
        Optional<ClassInfo> subClassAOpt = indexBuilder.getClassInfo("com.example.SubClassA");
        assertTrue(subClassAOpt.isPresent(), "ClassInfo should be present for SubClassA");
        ClassInfo subClassAInfo = subClassAOpt.get();
        assertEquals("com.example.SubClassA", subClassAInfo.getClassName(), "SubClassA name should match");
        assertEquals(1, subClassAInfo.getMethods().size(), "SubClassA should have one method");
        assertTrue(subClassAInfo.getMethods().containsKey("com.example.SubClassA#methodA()"), "Method methodA should be present");
        assertEquals("com.example.BaseClass", subClassAInfo.getSuperclassName(), "Superclass of SubClassA should be BaseClass");

        // Verify SubClassB
        Optional<ClassInfo> subClassBOpt = indexBuilder.getClassInfo("com.example.SubClassB");
        assertTrue(subClassBOpt.isPresent(), "ClassInfo should be present for SubClassB");
        ClassInfo subClassBInfo = subClassBOpt.get();
        assertEquals("com.example.SubClassB", subClassBInfo.getClassName(), "SubClassB name should match");
        assertEquals(1, subClassBInfo.getMethods().size(), "SubClassB should have one method");
        assertTrue(subClassBInfo.getMethods().containsKey("com.example.SubClassB#methodB()"), "Method methodB should be present");
        assertEquals("com.example.BaseClass", subClassBInfo.getSuperclassName(), "Superclass of SubClassB should be BaseClass");
    }

    @Test
    void buildIndex_ShouldPopulateClassInfoMap(@TempDir Path tempDir) throws IOException {
        // Arrange

        // Create TestClass.java
        Path testClassFile = tempDir.resolve("TestClass.java");
        String testClassContent = """
                package com.example;
                
                import javax.inject.Inject;
                
                public class TestClass {
                
                    @Inject
                    private DependencyClass dependency;
                
                    public void performAction() {
                        dependency.execute();
                        helperMethod();
                    }
                
                    private void helperMethod() {
                        // Helper method logic
                    }
                }
                """;
        Files.writeString(testClassFile, testClassContent);

        // Create DependencyClass.java
        Path dependencyClassFile = tempDir.resolve("DependencyClass.java");
        String dependencyClassContent = """
                package com.example;
                
                public class DependencyClass {
                    public void execute() {
                        // Execution logic
                        System.out.println("Executing dependency.");
                    }
                }
                """;
        Files.writeString(dependencyClassFile, dependencyClassContent);

        List<Path> sourcePaths = Collections.singletonList(tempDir);

        // Act
        indexBuilder.buildIndex(sourcePaths);

        // Assert

        // Verify TestClass
        Optional<ClassInfo> testClassOpt = indexBuilder.getClassInfo("com.example.TestClass");
        assertTrue(testClassOpt.isPresent(), "ClassInfo should be present for TestClass");
        ClassInfo testClassInfo = testClassOpt.get();
        assertEquals("com.example.TestClass", testClassInfo.getClassName(), "Class name should match");
        assertEquals("com.example", testClassInfo.getPackageName(), "Package name should match");
        assertEquals(2, testClassInfo.getMethods().size(), "TestClass should have three methods (including constructor)");
        assertTrue(testClassInfo.getMethods().containsKey("com.example.TestClass#performAction()"), "Method performAction should be present");
        assertTrue(testClassInfo.getMethods().containsKey("com.example.TestClass#helperMethod()"), "Method helperMethod should be present");

        // Verify DependencyClass
        Optional<ClassInfo> depClassInfoOpt = indexBuilder.getClassInfo("com.example.DependencyClass");
        assertTrue(depClassInfoOpt.isPresent(), "ClassInfo should be present for DependencyClass");
        ClassInfo depClassInfo = depClassInfoOpt.get();
        assertEquals("com.example.DependencyClass", depClassInfo.getClassName(), "DependencyClass name should match");
        assertEquals(1, depClassInfo.getMethods().size(), "DependencyClass should have one method");
        assertTrue(depClassInfo.getMethods().containsKey("com.example.DependencyClass#execute()"), "Method execute should be present");

        // Check Dependency type in TestClass
        Optional<DependencyInfo> depOpt = testClassInfo.getDependency("dependency");
        assertTrue(depOpt.isPresent(), "Dependency 'dependency' should be present");
        DependencyInfo dependencyInfo = depOpt.get();
        assertEquals("com.example.DependencyClass", dependencyInfo.getType(), "Dependency type should match");
        assertTrue(dependencyInfo.isInjected(), "Dependency should be marked as injected");
        assertTrue(dependencyInfo.isField(), "Dependency should be a field");
    }

    @Test
    void buildIndex_ShouldPopulateClassInfoMapWithConstructorInjection(@TempDir Path tempDir) throws IOException {
        // Arrange

        // Create TestClass.java
        Path testClassFile = tempDir.resolve("TestClass.java");
        String testClassContent = """
                package com.example;
                
                import javax.inject.Inject;
                
                public class TestClass {
                
                    private DependencyClass dependency;
                
                    public TestClass(DependencyClass dependency) {
                        this.dependency = dependency;
                    }
                
                    public void performAction() {
                        dependency.execute();
                        helperMethod();
                    }
                
                    private void helperMethod() {
                        // Helper method logic
                    }
                }
                """;
        Files.writeString(testClassFile, testClassContent);

        // Create DependencyClass.java
        Path dependencyClassFile = tempDir.resolve("DependencyClass.java");
        String dependencyClassContent = """
                package com.example;
                
                public class DependencyClass {
                    public void execute() {
                        // Execution logic
                        System.out.println("Executing dependency.");
                    }
                }
                """;
        Files.writeString(dependencyClassFile, dependencyClassContent);

        List<Path> sourcePaths = Collections.singletonList(tempDir);

        // Act
        indexBuilder.buildIndex(sourcePaths);

        // Assert

        // Verify TestClass
        Optional<ClassInfo> testClassOpt = indexBuilder.getClassInfo("com.example.TestClass");
        assertTrue(testClassOpt.isPresent(), "ClassInfo should be present for TestClass");
        ClassInfo testClassInfo = testClassOpt.get();
        assertEquals("com.example.TestClass", testClassInfo.getClassName(), "Class name should match");
        assertEquals("com.example", testClassInfo.getPackageName(), "Package name should match");
        assertEquals(3, testClassInfo.getMethods().size(), "TestClass should have three methods (including constructor)");
        assertTrue(testClassInfo.getMethods().containsKey("com.example.TestClass#<init>(DependencyClass)"), "Constructor should be present");
        assertTrue(testClassInfo.getMethods().containsKey("com.example.TestClass#performAction()"), "Method performAction should be present");
        assertTrue(testClassInfo.getMethods().containsKey("com.example.TestClass#helperMethod()"), "Method helperMethod should be present");

        // Verify DependencyClass
        Optional<ClassInfo> depClassInfoOpt = indexBuilder.getClassInfo("com.example.DependencyClass");
        assertTrue(depClassInfoOpt.isPresent(), "ClassInfo should be present for DependencyClass");
        ClassInfo depClassInfo = depClassInfoOpt.get();
        assertEquals("com.example.DependencyClass", depClassInfo.getClassName(), "DependencyClass name should match");
        assertEquals(1, depClassInfo.getMethods().size(), "DependencyClass should have one method");
        assertTrue(depClassInfo.getMethods().containsKey("com.example.DependencyClass#execute()"), "Method execute should be present");

        // Check Dependency type in TestClass
        Optional<DependencyInfo> depOpt = testClassInfo.getDependency("dependency");
        assertTrue(depOpt.isPresent(), "Dependency 'dependency' should be present");
        DependencyInfo dependencyInfo = depOpt.get();
        assertEquals("com.example.DependencyClass", dependencyInfo.getType(), "Dependency type should match");
        assertTrue(dependencyInfo.isInjected(), "Dependency should be marked as injected");
        assertTrue(dependencyInfo.isField(), "Dependency should be a field");
    }

    @Test
    void buildIndex_ShouldPopulateClassInfoMapWithInjectionAnnotation(@TempDir Path tempDir) throws IOException {
        // Arrange

        // Create TestClass.java
        Path testClassFile = tempDir.resolve("TestClass.java");
        String testClassContent = """
                package com.example;
                
                import javax.inject.Inject;
                
                public class TestClass {
                
                    private DependencyClass dependency;
                    @Inject
                    public TestClass(DependencyClass dependency) {
                        this.dependency = dependency;
                    }
                
                    public void performAction() {
                        dependency.execute();
                        helperMethod();
                    }
                
                    private void helperMethod() {
                        // Helper method logic
                    }
                }
                """;
        Files.writeString(testClassFile, testClassContent);

        // Create DependencyClass.java
        Path dependencyClassFile = tempDir.resolve("DependencyClass.java");
        String dependencyClassContent = """
                package com.example;
                
                public class DependencyClass {
                    public void execute() {
                        // Execution logic
                        System.out.println("Executing dependency.");
                    }
                }
                """;
        Files.writeString(dependencyClassFile, dependencyClassContent);

        List<Path> sourcePaths = Collections.singletonList(tempDir);

        // Act
        indexBuilder.buildIndex(sourcePaths);

        // Assert

        // Verify TestClass
        Optional<ClassInfo> testClassOpt = indexBuilder.getClassInfo("com.example.TestClass");
        assertTrue(testClassOpt.isPresent(), "ClassInfo should be present for TestClass");
        ClassInfo testClassInfo = testClassOpt.get();
        assertEquals("com.example.TestClass", testClassInfo.getClassName(), "Class name should match");
        assertEquals("com.example", testClassInfo.getPackageName(), "Package name should match");
        assertEquals(3, testClassInfo.getMethods().size(), "TestClass should have three methods (including constructor)");
        assertTrue(testClassInfo.getMethods().containsKey("com.example.TestClass#<init>(DependencyClass)"), "Constructor should be present");
        assertTrue(testClassInfo.getMethods().containsKey("com.example.TestClass#performAction()"), "Method performAction should be present");
        assertTrue(testClassInfo.getMethods().containsKey("com.example.TestClass#helperMethod()"), "Method helperMethod should be present");

        // Verify DependencyClass
        Optional<ClassInfo> depClassInfoOpt = indexBuilder.getClassInfo("com.example.DependencyClass");
        assertTrue(depClassInfoOpt.isPresent(), "ClassInfo should be present for DependencyClass");
        ClassInfo depClassInfo = depClassInfoOpt.get();
        assertEquals("com.example.DependencyClass", depClassInfo.getClassName(), "DependencyClass name should match");
        assertEquals(1, depClassInfo.getMethods().size(), "DependencyClass should have one method");
        assertTrue(depClassInfo.getMethods().containsKey("com.example.DependencyClass#execute()"), "Method execute should be present");

        // Check Dependency type in TestClass
        Optional<DependencyInfo> depOpt = testClassInfo.getDependency("dependency");
        assertTrue(depOpt.isPresent(), "Dependency 'dependency' should be present");
        DependencyInfo dependencyInfo = depOpt.get();
        assertEquals("com.example.DependencyClass", dependencyInfo.getType(), "Dependency type should match");
        assertTrue(dependencyInfo.isInjected(), "Dependency should be marked as injected");
        assertTrue(dependencyInfo.isField(), "Dependency should be a field");
    }

    @Test
    void buildIndex_WithMultipleClasses_ShouldPopulateAllClasses(@TempDir Path tempDir) throws IOException {
        // Arrange
        Path testFile1 = tempDir.resolve("TestClass.java");
        String content1 = """
                package com.example;
                
                public class TestClass {
                    public void methodOne() {}
                }
                """;
        Files.writeString(testFile1, content1);

        Path testFile2 = tempDir.resolve("AnotherClass.java");
        String content2 = """
                package com.example;
                
                public class AnotherClass {
                    public void methodTwo() {}
                }
                """;
        Files.writeString(testFile2, content2);

        List<Path> sourcePaths = Arrays.asList(tempDir);

        // Act
        indexBuilder.buildIndex(sourcePaths);

        // Assert
        Optional<ClassInfo> testClassOpt = indexBuilder.getClassInfo("com.example.TestClass");
        assertTrue(testClassOpt.isPresent(), "ClassInfo should contain TestClass");
        ClassInfo testClassInfo = testClassOpt.get();
        assertEquals(1, testClassInfo.getMethods().size(), "TestClass should have one method");
        assertTrue(testClassInfo.getMethods().containsKey("com.example.TestClass#methodOne()"), "Method methodOne should be present");

        Optional<ClassInfo> anotherClassOpt = indexBuilder.getClassInfo("com.example.AnotherClass");
        assertTrue(anotherClassOpt.isPresent(), "ClassInfo should contain AnotherClass");
        ClassInfo anotherClassInfo = anotherClassOpt.get();
        assertEquals(1, anotherClassInfo.getMethods().size(), "AnotherClass should have one method");
        assertTrue(anotherClassInfo.getMethods().containsKey("com.example.AnotherClass#methodTwo()"), "Method methodTwo should be present");
    }

    @Test
    void buildIndex_WithNoJavaFiles_ShouldHaveEmptyClassInfoMap(@TempDir Path tempDir) throws IOException {
        // Arrange
        Path nonJavaFile = tempDir.resolve("README.md");
        String content = """
                # This is a README file.
                
                It should be ignored by the Java parser.
                """;
        Files.writeString(nonJavaFile, content);

        List<Path> sourcePaths = Collections.singletonList(tempDir);

        // Act
        indexBuilder.buildIndex(sourcePaths);

        // Assert
        Optional<ClassInfo> classInfoOpt = indexBuilder.getClassInfo("com.example.TestClass");
        assertFalse(classInfoOpt.isPresent(), "ClassInfo should be empty when no Java files are present");
    }

    @Test
    void buildIndex_NestedClasses_ShouldParseAllClasses(@TempDir Path tempDir) throws IOException {
        // Arrange
        Path nestedClassFile = tempDir.resolve("NestedTestClass.java");
        String content = """
                package com.example;
                
                public class NestedTestClass {
                
                    public void outerMethod() {
                        InnerClass inner = new InnerClass();
                        inner.innerMethod();
                    }
                
                    public class InnerClass {
                        public void innerMethod() {
                            // Inner method logic
                        }
                    }
                }
                """;
        Files.writeString(nestedClassFile, content);

        List<Path> sourcePaths = Collections.singletonList(tempDir);

        // Act
        indexBuilder.buildIndex(sourcePaths);

        // Assert
        Optional<ClassInfo> outerClassOpt = indexBuilder.getClassInfo("com.example.NestedTestClass");
        assertTrue(outerClassOpt.isPresent(), "ClassInfo should contain NestedTestClass");
        ClassInfo outerClassInfo = outerClassOpt.get();
        assertEquals(1, outerClassInfo.getMethods().size(), "NestedTestClass should have one method");
        assertTrue(outerClassInfo.getMethods().containsKey("com.example.NestedTestClass#outerMethod()"), "Method outerMethod should be present");

        // Inner classes are handled separately based on implementation
        Optional<ClassInfo> innerClassOpt = indexBuilder.getClassInfo("com.example.NestedTestClass$InnerClass");
        assertTrue(innerClassOpt.isPresent(), "ClassInfo should contain InnerClass");
        ClassInfo innerClassInfo = innerClassOpt.get();
        assertEquals(1, innerClassInfo.getMethods().size(), "InnerClass should have one method");
        assertTrue(innerClassInfo.getMethods().containsKey("com.example.NestedTestClass$InnerClass#innerMethod()"), "Method innerMethod should be present");
    }
}
