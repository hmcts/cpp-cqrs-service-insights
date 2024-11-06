package uk.gov.moj.cpp.service.insights.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a dependency of a class, such as a field or constructor parameter.
 */
public class DependencyInfo {
    private final String name;
    private final boolean injected;
    private final boolean isField;
    private final List<String> implementingClasses;
    private String type;

    public DependencyInfo(String name, String type, boolean injected, boolean isField) {
        this.name = name;
        this.type = type;
        this.injected = injected;
        this.isField = isField;
        this.implementingClasses = new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isInjected() {
        return injected;
    }

    public boolean isField() {
        return isField;
    }

    public List<String> getImplementingClasses() {
        return implementingClasses;
    }

    public void addImplementingClass(String implClass) {
        this.implementingClasses.add(implClass);
    }
}
