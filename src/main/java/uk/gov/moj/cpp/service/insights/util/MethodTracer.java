package uk.gov.moj.cpp.service.insights.util;

import uk.gov.moj.cpp.service.insights.model.ClassInfo;
import uk.gov.moj.cpp.service.insights.model.MethodInfo;
import uk.gov.moj.cpp.service.insights.service.MethodStackTracerService;

import java.util.List;
import java.util.Optional;

public class MethodTracer {

    public static String collectMethodOutput(String targetClassName, String targetMethodName, MethodStackTracerService tracerService) {
        // Using StringBuilder to collect output
        StringBuilder outputCollector = new StringBuilder();

        // Capture method body and call stack information without direct console output
        Optional<ClassInfo> classInfoOpt = tracerService.getClassInfo(targetClassName);

        if (classInfoOpt.isPresent()) {
            ClassInfo classInfo = classInfoOpt.get();
            String methodSignature = tracerService.getMethodSignature(targetClassName, targetMethodName);
            Optional<MethodInfo> methodInfoOpt = classInfo.getMethods().values().stream()
                    .filter(m -> m.getSignature().equals(methodSignature))
                    .findFirst();

            if (methodInfoOpt.isPresent()) {
                Optional<String> methodBodyOpt = tracerService.getMethodBody(methodSignature);
                methodBodyOpt.ifPresentOrElse(
                        body -> outputCollector.append("Method Body:\n").append(body).append("\n"),
                        () -> outputCollector.append("No body available for the method.\n")
                );

                // Retrieve and add bodies of all nested method calls in the call stack
                List<String> callStack = tracerService.getMethodStack(methodSignature);

                outputCollector.append("\nCall Stack Method Bodies:\n");
                for (String calleeSignature : callStack) {
                    // Skip the initial method as it's already printed
                    if (calleeSignature.equals(methodSignature) || calleeSignature.endsWith("#apply(Object)")) {
                        continue;
                    }

                    Optional<String> calleeBodyOpt = tracerService.getMethodBody(calleeSignature);
                    if (calleeBodyOpt.isPresent()) {
                        outputCollector.append(calleeBodyOpt.get()).append("\n")
                                .append("--------------------------------------------------\n");
                    } else {
                        outputCollector.append("No body available for the method.\n")
                                .append("--------------------------------------------------\n");
                    }
                }
            } else {
                outputCollector.append("Method not found.\n");
            }
        } else {
            outputCollector.append("Class not found.\n");
        }

        // Return the collected output as a single string
        return outputCollector.toString();
    }
}
