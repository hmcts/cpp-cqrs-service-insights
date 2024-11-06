package uk.gov.moj.cpp.service.insights.example;


import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class MethodStackTracer {

    private final ClassParser classParser;
    private final DependencyResolver dependencyResolver;
    private final StaticVariableReplacer staticVariableReplacer;
    private final MethodAnalyzer methodAnalyzer;
    private final FilterManager filterManager;
    private final StateVariableMapper stateVariableMapper;

    public MethodStackTracer() throws IOException {
        this.classParser = new ClassParser(Constants.PROJECT_DIR);
        this.stateVariableMapper = new StateVariableMapper(Constants.STATE_VARIABLES_FILE);
        this.staticVariableReplacer = new StaticVariableReplacer(stateVariableMapper.getStaticVariables());
        this.dependencyResolver = new DependencyResolver(classParser);
        this.methodAnalyzer = new MethodAnalyzer(classParser, dependencyResolver, staticVariableReplacer);
        this.filterManager = new FilterManager();

        // Add desired filters
        this.filterManager.addFilter(new ExcludeDollarSignFilter());
        this.filterManager.addFilter(new ExcludeSquareBracketsFilter());
        this.filterManager.addFilter(new ExcludeEndsWithJsonFilter());
    }

    public static void main(String[] args) {

        String className = "uk.gov.moj.cpp.hearing.event.PublishResultsV3EventProcessor";
        String methodName = "resultsShared";

        try {
            MethodStackTracer tracer = new MethodStackTracer();
            Map<String, Set<String>> trace = tracer.trace(className, methodName);

            trace.forEach((method, calls) -> {
                System.out.println("Method: " + method);
                calls.forEach(call -> System.out.println("\tCalls: " + call));
            });

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Traces method stack starting from the given class and method.
     *
     * @param className  Fully qualified class name.
     * @param methodName Method name to start tracing from.
     * @return A map with method names and their nested method calls.
     */
    public Map<String, Set<String>> trace(String className, String methodName) {
        Map<String, Set<String>> methodMap = new HashMap<>();
        Set<String> methodCalls = methodAnalyzer.analyze(className, methodName, 10); // maxDepth = 10
        Set<String> filteredCalls = filterManager.applyFilters(methodCalls);
        methodMap.put(methodName, filteredCalls);
        return methodMap;
    }
}
