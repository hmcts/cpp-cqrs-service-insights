package uk.gov.moj.cpp.service.insights.example;


import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class StateVariableMapper {

    private final Map<String, String> staticVariables = new HashMap<>();

    public StateVariableMapper(String propertiesFilePath) throws IOException {
        loadStaticVariables(propertiesFilePath);
    }

    private void loadStaticVariables(String propertiesFilePath) throws IOException {
        Properties properties = new Properties();
        try (FileInputStream fis = new FileInputStream(propertiesFilePath)) {
            properties.load(fis);
        }
        for (String key : properties.stringPropertyNames()) {
            staticVariables.put(key, properties.getProperty(key));
        }
    }

    public Map<String, String> getStaticVariables() {
        return staticVariables;
    }
}

