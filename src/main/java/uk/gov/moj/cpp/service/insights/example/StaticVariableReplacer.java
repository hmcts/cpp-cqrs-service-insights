package uk.gov.moj.cpp.service.insights.example;


import java.util.Map;

public class StaticVariableReplacer {

    private final Map<String, String> staticVariables;

    public StaticVariableReplacer(Map<String, String> staticVariables) {
        this.staticVariables = staticVariables;
    }

    public String replace(String code) {
        String replacedCode = code;
        for (Map.Entry<String, String> entry : staticVariables.entrySet()) {
            String variable = entry.getKey();
            String value = entry.getValue();
            replacedCode = replacedCode.replaceAll("\\b" + variable + "\\b", value);
        }
        return replacedCode;
    }
}
