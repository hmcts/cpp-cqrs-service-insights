package uk.gov.moj.cpp.service.insights.util;


import uk.gov.moj.cpp.service.insights.indexer.IndexBuilderImpl;
import uk.gov.moj.cpp.service.insights.model.ClassInfo;
import uk.gov.moj.cpp.service.insights.model.MethodInfo;
import uk.gov.moj.cpp.service.insights.parser.JavaFileParser;
import uk.gov.moj.cpp.service.insights.parser.JavaFileParserImpl;
import uk.gov.moj.cpp.service.insights.resolver.CallGraphResolver;
import uk.gov.moj.cpp.service.insights.resolver.CallGraphResolverImpl;
import uk.gov.moj.cpp.service.insights.service.MethodStackTracerService;
import uk.gov.moj.cpp.service.insights.service.MethodStackTracerServiceImpl;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class MethodStackTracerApplication {

    public static final String DIR_PATH = "/Users/satishkumar/moj/all/cpp.context.progression/progression-command/progression-command-handler";

    public static void main(String[] args) {
        // Initialize components
        JavaFileParser parser = new JavaFileParserImpl();
        IndexBuilderImpl indexBuilder = new IndexBuilderImpl(parser);
        CallGraphResolver callGraphResolver = new CallGraphResolverImpl(indexBuilder);
        MethodStackTracerService tracerService = new MethodStackTracerServiceImpl(indexBuilder, callGraphResolver);

        // Define source paths
        List<Path> sourcePaths = Arrays.asList(Path.of(DIR_PATH));

        try {
            // Build the index
            tracerService.buildIndex(sourcePaths);

            // Example: Get method body for a specific method
            String targetClassName = "uk.gov.moj.cpp.progression.handler.UpdateCaseHandler";
            String targetMethodName = "handleIncreaseListingNumber";
            List<String> targetParameters = Arrays.asList("Envelope");

            Optional<ClassInfo> classInfoOpt = tracerService.getClassInfo(targetClassName);
            if (classInfoOpt.isPresent()) {
                ClassInfo classInfo = classInfoOpt.get();
                String methodSignature = tracerService.getMethodSignature(targetClassName, targetMethodName);
                Optional<MethodInfo> methodInfoOpt = classInfo.getMethods().values().stream()
                        .filter(m -> m.getSignature().startsWith(methodSignature))
                        .findFirst();
                if (methodInfoOpt.isPresent()) {
                    Optional<String> methodBodyOpt = tracerService.getMethodBody(methodSignature);
                    methodBodyOpt.ifPresentOrElse(
                            body -> {
                                System.out.println("Method Body for " + methodSignature + ":");
                                System.out.println(body);
                            },
                            () -> System.out.println("No body available for the method: " + methodSignature)
                    );

                    // Now, retrieve and print the bodies of all nested method calls in the call stack
                    List<String> callStack = tracerService.getMethodStack(methodSignature);

                    System.out.println("\nCall Stack Method Bodies:");
                    for (String calleeSignature : callStack) {
                        // Skip the initial method as it's already printed
                        if (calleeSignature.equals(methodSignature)) {
                            continue;
                        }

                        Optional<String> calleeBodyOpt = tracerService.getMethodBody(calleeSignature);
                        if (calleeBodyOpt.isPresent()) {
                            System.out.println("Method Body for " + calleeSignature + ":");
                            System.out.println(calleeBodyOpt.get());
                        } else {
                            System.out.println("No body available for the method: " + calleeSignature);
                        }

                        System.out.println("--------------------------------------------------");
                    }

                } else {
                    System.out.println("Method not found: " + targetClassName + "#" + targetMethodName + "(" + String.join(",", targetParameters) + ")");
                }
            } else {
                System.out.println("Class not found: " + targetClassName);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

