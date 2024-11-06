package uk.gov.moj.cpp.service.insights.model;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents information about a Java class.
 */
public class ClassInfo {
    private final String className;
    private final String packageName;
    private final Map<String, String> importMap;
    private final Map<String, MethodInfo> methods = new ConcurrentHashMap<>();
    private final Map<String, DependencyInfo> dependencies = new ConcurrentHashMap<>();
    private final Set<String> implementedInterfaces = ConcurrentHashMap.newKeySet();

    public ClassInfo(String className, String packageName, Map<String, String> importMap) {
        this.className = className;
        this.packageName = packageName;
        this.importMap = new HashMap<>(importMap);
    }

    public String getClassName() {
        return className;
    }

    public String getPackageName() {
        return packageName;
    }

    public Map<String, String> getImportMap() {
        return importMap;
    }

    public void addMethod(MethodInfo methodInfo) {
        methods.put(methodInfo.getSignature(), methodInfo);
    }

    public Optional<DependencyInfo> getDependency(String name) {
        return Optional.ofNullable(dependencies.get(name));
    }

    public Map<String, MethodInfo> getMethods() {
        return methods;
    }

    public Collection<DependencyInfo> getDependencies() {
        return dependencies.values();
    }

    public void addDependency(DependencyInfo dependencyInfo) {
        dependencies.putIfAbsent(dependencyInfo.getName(), dependencyInfo);
        if (dependencyInfo.isInjected()) {
            dependencies.put(dependencyInfo.getName(), dependencyInfo);
        }
    }

    public void addImplementedInterface(String interfaceName) {
        implementedInterfaces.add(interfaceName);
    }

    public Set<String> getImplementedInterfaces() {
        return implementedInterfaces;
    }
}
