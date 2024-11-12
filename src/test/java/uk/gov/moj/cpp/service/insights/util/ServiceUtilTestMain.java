package uk.gov.moj.cpp.service.insights.util;

import static java.lang.System.out;
import static uk.gov.moj.cpp.service.insights.html.ServiceHtmlGenerator.generateHTML;
import static uk.gov.moj.cpp.service.insights.html.ServiceHtmlGenerator.saveToFile;

import uk.gov.moj.cpp.service.insights.CQRSModelMapper;
import uk.gov.moj.cpp.service.insights.common.MethodProcessingResult;
import uk.gov.moj.cpp.service.insights.indexer.IndexBuilderImpl;
import uk.gov.moj.cpp.service.insights.model.Model;
import uk.gov.moj.cpp.service.insights.model.ModelBuilder;
import uk.gov.moj.cpp.service.insights.parser.JavaFileParser;
import uk.gov.moj.cpp.service.insights.parser.JavaFileParserImpl;
import uk.gov.moj.cpp.service.insights.resolver.CallGraphResolver;
import uk.gov.moj.cpp.service.insights.resolver.CallGraphResolverImpl;
import uk.gov.moj.cpp.service.insights.service.MethodStackTracerService;
import uk.gov.moj.cpp.service.insights.service.MethodStackTracerServiceImpl;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;

/**
 * Test class to verify the functionality of ServiceUtil.
 * Scans the specified directory for Maven modules and extracts:
 * - Static String variables with predefined service name prefixes.
 * - Classes annotated with @Event.
 * - Methods annotated with @Handles.
 */
public class ServiceUtilTestMain {

    public static void main(String[] args) {
        // Initialize services and parsers
        JavaFileParser parser = new JavaFileParserImpl();
        IndexBuilderImpl indexBuilder = new IndexBuilderImpl(parser);
        CallGraphResolver callGraphResolver = new CallGraphResolverImpl(indexBuilder);
        MethodStackTracerService tracerService = new MethodStackTracerServiceImpl(indexBuilder, callGraphResolver);

        // Define the test directory (can be replaced with a dynamic input)
        String testDirectory = "/Users/satishkumar/moj/all/cpp.context.progression";
        List<Path> sourcePaths = Collections.singletonList(Path.of(testDirectory));

        // Initialize logger
        Log log = new SystemStreamLog();

        try {
            // Build the index from source paths
            tracerService.buildIndex(sourcePaths);

            // Scan modules and resolve handles
            Map<String, ServiceUtil.ModuleScanResult> scanResults = ServiceUtil.resolveHandlesValues(
                    ServiceUtil.scanModules(testDirectory, log), log);

            if (scanResults.isEmpty()) {
                log.info("No matching static String variables, @Event-annotated classes, @Handles-annotated methods, or Aggregate-implementing classes found.");
                return;
            }

            // Initialize mappings and collections
            Map<String, String> classNameEventNameMapping = new HashMap<>();
            Map<String, Set<String>> commandUseAggregatesNames = new HashMap<>();
            Map<String, Set<String>> commandGenerateEvents = new HashMap<>();
            Map<String, Set<String>> processorGenerateEvents = new HashMap<>();
            List<String> aggregatesNames = new ArrayList<>();

            // Process each module scan result
            scanResults.forEach((moduleName, moduleResult) -> {
                logModuleDetails(moduleName, moduleResult, classNameEventNameMapping, aggregatesNames, log);
            });

            // Process command-handler modules
            processHandlers(scanResults, tracerService, classNameEventNameMapping, aggregatesNames,
                    commandUseAggregatesNames, commandGenerateEvents, processorGenerateEvents,log);

            // Map handlers and aggregates
            CQRSModelMapper mapper = new CQRSModelMapper();
            Map<String, Set<String>> commandHandler = mapper.aggregateHandlesByClassName(scanResults, "command-handler");
            Map<String, Set<String>> processHandler = mapper.aggregateHandlesByClassName(scanResults, "event-processor");
            Map<String, Set<String>> listenerHandler = mapper.aggregateHandlesByClassName(scanResults, "event-listener");
            List<String> aggregateSimpleClassNames = mapper.getAggregateSimpleClassNames(scanResults);

            log.info("Aggregates: " + aggregateSimpleClassNames);

            // Build the model
            Model model = ModelBuilder.buildModel(commandHandler, processHandler, aggregateSimpleClassNames);

            // Generate HTML content
            String htmlContent = generateHTML(
                    flattenSet(commandHandler),
                    flattenSet(processHandler),
                    aggregateSimpleClassNames,
                    commandUseAggregatesNames,
                    commandGenerateEvents,
                    flattenSet(listenerHandler),
                    processorGenerateEvents);

            // Define the output file path
            Path outputPath = Path.of("/Users/satishkumar/moj/all/com.moj.cpp/final/cpp-cqrs-service-insights/target/hearingcqrs.html");

            // Save the HTML content to the file
            saveHtmlContent(htmlContent, outputPath, log);

        } catch (IOException e) {
            log.error("An error occurred while scanning the modules: " + e.getMessage(), e);
        }
    }

    /**
     * Logs the details of each module including handles, events, and aggregates.
     *
     * @param moduleName                 The name of the module.
     * @param moduleResult               The scan result of the module.
     * @param classNameEventNameMapping  Mapping of class names to event names.
     * @param aggregatesNames            List to collect aggregate class names.
     * @param log                        Logger instance.
     */
    private static void logModuleDetails(String moduleName, ServiceUtil.ModuleScanResult moduleResult,
                                         Map<String, String> classNameEventNameMapping, List<String> aggregatesNames,
                                         Log log) {
        log.info("Module: " + moduleName);

        // Log @Handles annotated methods
        List<ServiceUtil.HandlesInfo> handles = moduleResult.handles();
        if (!handles.isEmpty()) {
            log.info("  Methods Annotated with @Handles:");
            handles.forEach(handleInfo -> {
                log.info("    Class: " + handleInfo.className());
                log.info("    Method: " + handleInfo.methodName());
                log.info("    Handles: " + handleInfo.handlesValue());
                log.info(""); // Blank line for readability
            });
        }

        // Log @Event annotated classes
        List<ServiceUtil.EventInfo> events = moduleResult.events();
        if (!events.isEmpty()) {
            log.info("  Classes Annotated with @Event:");
            events.forEach(eventInfo -> {
                String simpleClassName = CQRSModelMapper.getSimpleClassName(eventInfo.className());
                classNameEventNameMapping.put(simpleClassName, eventInfo.eventValue());
                log.info("    " + simpleClassName + " -> " + eventInfo.eventValue());
            });
        }

        // Log Aggregate-implementing classes
        List<ServiceUtil.AggregateInfo> aggregates = moduleResult.aggregates();
        if (!aggregates.isEmpty()) {
            log.info("  Classes Implementing Aggregate Interface:");
            aggregates.forEach(aggregateInfo -> {
                String simpleClassName = CQRSModelMapper.getSimpleClassName(aggregateInfo.className()) + ".class";
                aggregatesNames.add(simpleClassName);
                log.info("    " + simpleClassName);
            });
        }

        log.info("-----------------------------------------------------"); // Separator between modules
    }

    /**
     * Processes command-handler modules to collect generated events and used aggregates.
     *
     * @param scanResults                The scan results of all modules.
     * @param tracerService              The method stack tracer service.
     * @param classNameEventNameMapping  Mapping of class names to event names.
     * @param aggregatesNames            List of aggregate class names.
     * @param commandUseAggregatesNames  Mapping of commands to used aggregates.
     * @param commandGenerateEvents      Mapping of commands to generated events.
     * @param log                        Logger instance.
     */
    private static void processHandlers(Map<String, ServiceUtil.ModuleScanResult> scanResults,
                                        MethodStackTracerService tracerService,
                                        Map<String, String> classNameEventNameMapping,
                                        List<String> aggregatesNames,
                                        Map<String, Set<String>> commandUseAggregatesNames,
                                        Map<String, Set<String>> commandGenerateEvents,
                                        Map<String, Set<String>> processorGenerateEvents,
                                        Log log) {
        scanResults.forEach((moduleName, moduleResult) -> {
            if (moduleName.endsWith("command-handler")) {
                moduleResult.handles().forEach(handleInfo -> {
                    String methodBody = MethodTracer.collectMethodOutput(handleInfo.className(),
                            handleInfo.methodName(), tracerService);

                    MethodProcessingResult processingResult = ServiceUtilTestMain.processMethodBody(
                            methodBody, classNameEventNameMapping, aggregatesNames);

                    // Update commandGenerateEvents
                    if (!processingResult.getGeneratedEvents().isEmpty()) {
                        commandGenerateEvents.merge(handleInfo.handlesValue(),
                                processingResult.getGeneratedEvents(),
                                (existing, newEvents) -> {
                                    existing.addAll(newEvents);
                                    return existing;
                                });
                    }

                    // Update commandUseAggregatesNames
                    if (!processingResult.getUsedAggregates().isEmpty()) {
                        commandUseAggregatesNames.merge(handleInfo.handlesValue(),
                                processingResult.getUsedAggregates(),
                                (existing, newAggregates) -> {
                                    existing.addAll(newAggregates);
                                    return existing;
                                });
                    }
                });
            }
            if (moduleName.endsWith("event-processor")) {
                moduleResult.handles().forEach(handleInfo -> {
                    String methodBody = MethodTracer.collectMethodOutput(
                            handleInfo.className(),
                            handleInfo.methodName(),
                            tracerService
                    );
                    if("results.event.informant-register-notified".equals(handleInfo.handlesValue())){
                        out.println("");
                    }
                    String modifiedMethodBody = moduleResult.variables().stream()
                            .reduce(methodBody, (body, variableInfo) ->
                                            body.replace(variableInfo.variableName(), ("\""+variableInfo.variableValue()+"\"")),
                                    (body1, body2) -> body1); // Combiner not used here
                    processorGenerateEvents.put(handleInfo.handlesValue(),ServiceUtil.scanForServiceInQuotes(modifiedMethodBody));

                });
            }
        });
    }

    /**
     * Flattens a map of sets into a single set containing all values.
     *
     * @param handlerMap The map containing sets of strings.
     * @return A flattened set of all strings.
     */
    private static Set<String> flattenSet(Map<String, Set<String>> handlerMap) {
        return handlerMap.values().stream()
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
    }

    /**
     * Saves the generated HTML content to the specified file path.
     *
     * @param htmlContent The HTML content to save.
     * @param outputPath  The path where the HTML file will be saved.
     * @param log         Logger instance.
     */
    private static void saveHtmlContent(String htmlContent, Path outputPath, Log log) {
        try {
            saveToFile(htmlContent, outputPath);
            log.info("HTML file generated successfully at: " + outputPath.toAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to generate HTML file at " + outputPath.toAbsolutePath(), e);
        }
    }

    /**
     * Processes the method body to find generated events and used aggregates.
     *
     * @param methodBody                The body of the method as a String.
     * @param classNameEventNameMapping A map where keys are class names and values are event names.
     * @param aggregatesNames           A list of aggregate names to look for.
     * @return A MethodProcessingResult containing the found events and aggregates.
     */
    public static MethodProcessingResult processMethodBody(String methodBody,
                                                           Map<String, String> classNameEventNameMapping,
                                                           List<String> aggregatesNames) {
        MethodProcessingResult result = new MethodProcessingResult();
        List<String> generatedEvents = new ArrayList<>();
        List<String> matchingLines = new ArrayList<>();

        String[] lines = methodBody.split("\\R");

        // Identify generated events based on class name mappings
        classNameEventNameMapping.forEach((className, eventName) -> {
            String regex = "\\b" + Pattern.quote(className) + "\\b(?!\\.class)";
            Pattern pattern = Pattern.compile(regex);

            for (String line : lines) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    if (!generatedEvents.contains(eventName) && !eventName.contains(".command.")) {
                        result.addGeneratedEvent(eventName);
                    }
                    matchingLines.add(line.trim());
                }
            }
        });

        // Log generated events and matching lines
        out.println("Generated Events: " + generatedEvents);
        out.println("Matching Lines:");
        matchingLines.forEach(out::println);

        // Identify used aggregates
        aggregatesNames.forEach(aggregate -> {
            String regex = "\\b" + Pattern.quote(aggregate) + "\\b";
            if (Pattern.compile(regex).matcher(methodBody).find()) {
                result.addUsedAggregate(aggregate.replace(".class", ""));
            }
        });

        return result;
    }
}
