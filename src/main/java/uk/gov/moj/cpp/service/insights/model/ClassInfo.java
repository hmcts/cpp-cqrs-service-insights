package uk.gov.moj.cpp.service.insights.model;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
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
    private final Set<MethodInfo> inheritedMethods = new HashSet<>();
    private String superClassFullName;

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

    public void setSuperClass(final String superClassFullName) {
        this.superClassFullName = superClassFullName;
    }

    public String getSuperclassName() {
        return superClassFullName;
    }

    public Set<MethodInfo> getInheritedMethods() {
        return inheritedMethods;
    }

    public void addInheritedMethod(MethodInfo method) {
        this.inheritedMethods.add(method);
    }

    // Modify addMethod to check for duplicates or overrides
    public void addMethod(MethodInfo methodInfo) {
        // Remove any inherited method with the same signature
        inheritedMethods.removeIf(inherited -> inherited.getSignature().equals(methodInfo.getSignature()));
        methods.put(methodInfo.getSignature(), methodInfo);
    }

    /**
     * Checks if the class declares a method with the given signature.
     *
     * @param signature The signature of the method to check.
     * @return True if the method exists in the class's declared methods, false otherwise.
     */
    public boolean hasMethod(String signature) {
        return methods.values().stream().anyMatch(method -> method.getSignature().equals(signature));
    }
}
