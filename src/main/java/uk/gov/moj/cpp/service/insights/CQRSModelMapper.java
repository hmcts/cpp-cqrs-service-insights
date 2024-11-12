package uk.gov.moj.cpp.service.insights;

import uk.gov.moj.cpp.service.insights.util.ServiceUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CQRSModelMapper {

    /**
     * Processes the scan results to aggregate @Handles values by simple class names.
     * Modules with names ending with moduleNameFilter are considered.
     *
     * @param scanResults The map of module names to their scan results.
     * @return A map where each key is a simple class name, and the value is a set of associated handles.
     */
    public Map<String, Set<String>> aggregateHandlesByClassName(Map<String, ServiceUtil.ModuleScanResult> scanResults,String moduleNameFilter) {
        // Initialize the result map
        Map<String, Set<String>> classToHandlesMap = new HashMap<>();

        // Iterate through each module's scan results
        for (Map.Entry<String, ServiceUtil.ModuleScanResult> entry : scanResults.entrySet()) {
            String moduleName = entry.getKey();
            ServiceUtil.ModuleScanResult moduleResult = entry.getValue();

            // Check if the module name ends with "command-handler"
            if (moduleName.endsWith(moduleNameFilter)) {
                // Iterate through each HandlesInfo in the module
                for (ServiceUtil.HandlesInfo handleInfo : moduleResult.handles()) {
                    String fullClassName = handleInfo.className();
                    String handlesValue = handleInfo.handlesValue();

                    // Extract the simple class name (e.g., UploadSubscriptionsCommandHandler)
                    String simpleClassName = getSimpleClassName(fullClassName);

                    // Initialize the set if the class name is encountered for the first time
                    classToHandlesMap.computeIfAbsent(simpleClassName, k -> new HashSet<>());


                    classToHandlesMap.get(simpleClassName).add(handlesValue);

                }
            }
        }

        return classToHandlesMap;
    }
    /**
     * Processes the scan results to get a list of simple class names for classes implementing the Aggregate interface.
     *
     * @param scanResults The map of module names to their scan results.
     * @return A list of simple class names implementing the Aggregate interface.
     */
    public List<String> getAggregateSimpleClassNames(Map<String, ServiceUtil.ModuleScanResult> scanResults) {
        List<String> aggregateClassNames = new ArrayList<>();

        for (Map.Entry<String, ServiceUtil.ModuleScanResult> entry : scanResults.entrySet()) {
            String moduleName = entry.getKey();
            ServiceUtil.ModuleScanResult moduleResult = entry.getValue();


                // Iterate through each ClassInfo in the module
                for (ServiceUtil.AggregateInfo aggregateInfo : moduleResult.aggregates()) {
                    String fullClassName = aggregateInfo.className();
                    String simpleClassName = getSimpleClassName(fullClassName);
                    if (!simpleClassName.isEmpty()) {
                        aggregateClassNames.add(simpleClassName);
                    }
                }

        }

        return aggregateClassNames;
    }
    /**
     * Extracts the simple class name from the full class name.
     *
     * @param fullClassName The fully qualified class name.
     * @return The simple class name.
     */
    public static String getSimpleClassName(String fullClassName) {
        if (fullClassName == null || fullClassName.isEmpty()) {
            return "";
        }
        int lastDotIndex = fullClassName.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == fullClassName.length() - 1) {
            return fullClassName;
        }
        return fullClassName.substring(lastDotIndex + 1);
    }
}
