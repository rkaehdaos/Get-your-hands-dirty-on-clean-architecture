package dev.haja.buckpal.archunit;

import com.tngtech.archunit.core.domain.JavaClasses;

import java.util.ArrayList;
import java.util.List;

public class Adapters extends ArchitectureElement {
    private final HexagonalArchitecture parentContext;
    private final List<String> incomingAdapterPackages = new ArrayList<>();
    private final List<String> outgoingAdapterPackages = new ArrayList<>();

    Adapters(HexagonalArchitecture parentContext, String basePackage) {
        super(basePackage);
        this.parentContext = parentContext;
    }

    public Adapters outgoing(String packageName) {
        this.outgoingAdapterPackages.add(fullQualifiedPackage(packageName));
        return this;
    }

    public Adapters incoming(String packageName) {
        this.incomingAdapterPackages.add(fullQualifiedPackage(packageName));
        return this;
    }

    List<String> allAdapterPackages() {
        List<String> allAdapters = new ArrayList<>();
        allAdapters.addAll(incomingAdapterPackages);
        allAdapters.addAll(outgoingAdapterPackages);
        return allAdapters;
    }

    public HexagonalArchitecture and() {
        return parentContext;
    }

    String getBasePackage() {
        return basePackage;
    }

    void dontDependOnEachOther(JavaClasses classes) {
        List<String> allAdapters = allAdapterPackages();
        for (String adapter1 : allAdapters) {
            for (String adapter2 : allAdapters) {
                if (!adapter1.equals(adapter2)) {
                    denyDependency(adapter1, adapter2, classes);
                }
            }
        }
    }

    void doesNotDependOn(String packageName, JavaClasses classes) {
        denyDependency(this.basePackage, packageName, classes);
    }

    void doesNotContainEmptyPackages() {
        denyEmptyPackages(allAdapterPackages());
    }
}