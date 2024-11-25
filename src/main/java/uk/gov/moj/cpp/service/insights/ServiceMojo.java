package uk.gov.moj.cpp.service.insights;

import uk.gov.moj.cpp.service.insights.common.MethodProcessingResult;
import uk.gov.moj.cpp.service.insights.html.ServiceHtmlGenerator;
import uk.gov.moj.cpp.service.insights.indexer.IndexBuilderImpl;
import uk.gov.moj.cpp.service.insights.model.Model;
import uk.gov.moj.cpp.service.insights.model.ModelBuilder;
import uk.gov.moj.cpp.service.insights.parser.JavaFileParser;
import uk.gov.moj.cpp.service.insights.parser.JavaFileParserImpl;
import uk.gov.moj.cpp.service.insights.resolver.CallGraphResolver;
import uk.gov.moj.cpp.service.insights.resolver.CallGraphResolverImpl;
import uk.gov.moj.cpp.service.insights.service.MethodStackTracerService;
import uk.gov.moj.cpp.service.insights.service.MethodStackTracerServiceImpl;
import uk.gov.moj.cpp.service.insights.util.MethodTracer;
import uk.gov.moj.cpp.service.insights.util.ServiceUtil;
import uk.gov.moj.cpp.service.insights.util.ServiceUtil.EventInfo;
import uk.gov.moj.cpp.service.insights.util.ServiceUtil.HandlesInfo;
import uk.gov.moj.cpp.service.insights.util.ServiceUtil.ModuleScanResult;

import java.io.File;
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

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "service-insights", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public class ServiceMojo extends AbstractMojo {

    @Parameter(property = "rootDirectory", defaultValue = "${project.basedir}", required = true)
    private String rootDirectory;

    @Parameter(property = "outputDir", required = true, defaultValue = "target/html")
    private File outputDir;

    @Parameter(property = "schemaFileName", required = false, defaultValue = "service-visualization.html")
    private String serviceFileName;

    public static MethodProcessingResult processMethodBody(String methodBody,
                                                           Map<String, String> classNameEventNameMapping,
                                                           List<String> aggregatesNames, final Log log) {
        MethodProcessingResult result = new MethodProcessingResult();
        List<String> generatedEvents = new ArrayList<>();
        List<String> matchingLines = new ArrayList<>();

        String[] lines = methodBody.split("\\R");

        classNameEventNameMapping.forEach((className, eventName) -> {
            String regex = "\\b" + Pattern.quote(className) + "\\b(?!\\.class)";
            Pattern pattern = Pattern.compile(regex);

            for (String line : lines) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    if (!generatedEvents.contains(eventName)) {
                        result.addGeneratedEvent(eventName);
                        generatedEvents.add(eventName);
                    }
                    matchingLines.add(line.trim());
                }
            }
        });

        if (!generatedEvents.isEmpty()) {
            log.info("Generated Events: " + generatedEvents);
        }
        if (!matchingLines.isEmpty()) {
            log.info("Matching Lines:");
            matchingLines.forEach(log::info);
        }

        aggregatesNames.forEach(aggregate -> {
            String regex = "\\b" + Pattern.quote(aggregate) + "\\b";
            if (Pattern.compile(regex).matcher(methodBody).find()) {
                result.addUsedAggregate(aggregate.replace(".class", ""));
            }
        });

        return result;
    }

    @Override
    public void execute() throws MojoExecutionException {
        Log log = getLog();
        log.info("Starting Service Insights Mojo...");

        JavaFileParser parser = new JavaFileParserImpl();
        IndexBuilderImpl indexBuilder = new IndexBuilderImpl(parser);
        CallGraphResolver callGraphResolver = new CallGraphResolverImpl(indexBuilder);
        MethodStackTracerService tracerService = new MethodStackTracerServiceImpl(indexBuilder, callGraphResolver);

        List<Path> sourcePaths = Collections.singletonList(Path.of(rootDirectory));

        try {
            tracerService.buildIndex(sourcePaths);
            log.info("Index built successfully from source paths.");

            Map<String, ModuleScanResult> scanResults = ServiceUtil.resolveHandlesValues(
                    ServiceUtil.scanModules(rootDirectory, log), log);

            if (scanResults.isEmpty()) {
                log.info("No matching static String variables, @Event-annotated classes, @Handles-annotated methods, or Aggregate-implementing classes found.");
                return;
            }

            Map<String, String> classNameEventNameMapping = new HashMap<>();
            Map<String, Set<String>> commandUseAggregatesNames = new HashMap<>();
            Map<String, Set<String>> commandGenerateEvents = new HashMap<>();
            Map<String, Set<String>> processorGenerateEvents = new HashMap<>();
            List<String> aggregatesNames = new ArrayList<>();

            scanResults.forEach((moduleName, moduleResult) -> {
                logModuleDetails(moduleName, moduleResult, classNameEventNameMapping, aggregatesNames, log);
            });

            processHandlers(scanResults, tracerService, classNameEventNameMapping, aggregatesNames,
                    commandUseAggregatesNames, commandGenerateEvents, processorGenerateEvents, log);

            CQRSModelMapper mapper = new CQRSModelMapper();
            Map<String, Set<String>> commandHandler = mapper.aggregateHandlesByClassName(scanResults, "command-handler");
            Map<String, Set<String>> processHandler = mapper.aggregateHandlesByClassName(scanResults, "event-processor");
            Map<String, Set<String>> listenerHandler = mapper.aggregateHandlesByClassName(scanResults, "event-listener");
            List<String> aggregateSimpleClassNames = mapper.getAggregateSimpleClassNames(scanResults);

            log.info("Aggregates: " + aggregateSimpleClassNames);

            Model model = ModelBuilder.buildModel(commandHandler, processHandler, aggregateSimpleClassNames);

            String htmlContent = ServiceHtmlGenerator.generateHTML(
                    flattenSet(commandHandler),
                    flattenSet(processHandler),
                    aggregateSimpleClassNames,
                    commandUseAggregatesNames,
                    commandGenerateEvents,
                    flattenSet(listenerHandler),
                    processorGenerateEvents);

            String filePath = new File(outputDir, serviceFileName).getAbsolutePath();
            Path outputPath = Path.of(filePath);

            saveHtmlContent(htmlContent, outputPath, log);

            log.info("Service Insights Mojo completed successfully.");

        } catch (IOException e) {
            throw new MojoExecutionException("An error occurred while scanning the modules: " + e.getMessage(), e);
        }
    }

    private void logModuleDetails(String moduleName, ModuleScanResult moduleResult,
                                  Map<String, String> classNameEventNameMapping, List<String> aggregatesNames,
                                  Log log) {
        log.info("Module: " + moduleName);

        List<HandlesInfo> handles = moduleResult.handles();
        if (!handles.isEmpty()) {
            log.info("  Methods Annotated with @Handles:");
            handles.forEach(handleInfo -> {
                log.info("    Class: " + handleInfo.className());
                log.info("    Method: " + handleInfo.methodName());
                log.info("    Handles: " + handleInfo.handlesValue());
            });
        }

        List<EventInfo> events = moduleResult.events();
        if (!events.isEmpty()) {
            log.info("  Classes Annotated with @Event:");
            events.forEach(eventInfo -> {
                String simpleClassName = CQRSModelMapper.getSimpleClassName(eventInfo.className());
                classNameEventNameMapping.put(simpleClassName, eventInfo.eventValue());
                log.info("    " + simpleClassName + " -> " + eventInfo.eventValue());
            });
        }

        List<ServiceUtil.AggregateInfo> aggregates = moduleResult.aggregates();
        if (!aggregates.isEmpty()) {
            log.info("  Classes Implementing Aggregate Interface:");
            aggregates.forEach(aggregateInfo -> {
                String simpleClassName = CQRSModelMapper.getSimpleClassName(aggregateInfo.className()) + ".class";
                aggregatesNames.add(simpleClassName);
                log.info("    " + simpleClassName);
            });
        }
        log.info("-----------------------------------------------------");
    }

    private void processHandlers(Map<String, ModuleScanResult> scanResults,
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

                    MethodProcessingResult processingResult = ServiceMojo.processMethodBody(
                            methodBody, classNameEventNameMapping, aggregatesNames, log);

                    if (!processingResult.getGeneratedEvents().isEmpty()) {
                        commandGenerateEvents.merge(handleInfo.handlesValue(),
                                processingResult.getGeneratedEvents(),
                                (existing, newEvents) -> {
                                    existing.addAll(newEvents);
                                    return existing;
                                });
                    }

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

                    String modifiedMethodBody = moduleResult.variables().stream()
                            .reduce(methodBody, (body, variableInfo) ->
                                            body.replace(variableInfo.variableName(), ("\"" + variableInfo.variableValue() + "\"")),
                                    (body1, body2) -> body1);

                    processorGenerateEvents.put(handleInfo.handlesValue(),
                            ServiceUtil.scanForServiceInQuotes(modifiedMethodBody));
                });
            }
        });
    }

    private Set<String> flattenSet(Map<String, Set<String>> handlerMap) {
        return handlerMap.values().stream()
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
    }

    private void saveHtmlContent(String htmlContent, Path outputPath, Log log) {
        try {
            ServiceHtmlGenerator.saveToFile(htmlContent, outputPath);
            log.info("HTML file generated successfully at: " + outputPath.toAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to generate HTML file at " + outputPath.toAbsolutePath(), e);
        }
    }
}
