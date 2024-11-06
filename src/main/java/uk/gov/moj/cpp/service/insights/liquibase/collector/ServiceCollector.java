package uk.gov.moj.cpp.service.insights.liquibase.collector;

import uk.gov.moj.cpp.service.insights.liquibase.model.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class ServiceCollector implements IServiceCollector {
    private static final String SERVICE_PREFIX = "cpp.context.";

    @Override
    public List<Service> collectServices(String rootDirectory) {
        List<Service> services = new ArrayList<>();
        File root = new File(rootDirectory);

        if (!root.exists() || !root.isDirectory()) {
            System.out.println("Invalid root directory: " + rootDirectory);
            return services;
        }

        // List all subdirectories in the root directory
        File[] subDirs = root.listFiles(File::isDirectory);
        if (subDirs == null) {
            System.out.println("No subdirectories found in root directory: " + rootDirectory);
            return services;
        }

        for (File subDir : subDirs) {
            String dirName = subDir.getName();
            if (dirName.startsWith(SERVICE_PREFIX)) {
                String serviceName = dirName
                        .substring(SERVICE_PREFIX.length())
                        .replaceAll("[.-]", "");
                // Construct the Liquibase directory path
                String liquibaseDirPath = subDir.getAbsolutePath() + "/" + serviceName + "-viewstore/"
                        + serviceName + "-viewstore-liquibase/src/main/resources/liquibase";
                File liquibaseDir = new File(liquibaseDirPath);
                if (liquibaseDir.exists() && liquibaseDir.isDirectory()) {
                    Service service = new Service(
                            serviceName,
                            liquibaseDirPath,
                            new ArrayList<>(),
                            new LinkedHashMap<>()
                    );
                    services.add(service);
                    System.out.println("Found service: " + serviceName + " with Liquibase directory: " + liquibaseDirPath);
                } else {
                    System.out.println("Liquibase directory not found for service: " + serviceName
                            + " at " + liquibaseDirPath);
                }
            }
        }

        return services;
    }
}
