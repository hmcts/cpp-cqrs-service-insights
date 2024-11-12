package uk.gov.moj.cpp.service.insights.common;

import java.util.HashSet;
import java.util.Set;

public class MethodProcessingResult {
    private Set<String> generatedEvents;
    private Set<String> usedAggregates;
    private Set<String> commandCalled;

    public MethodProcessingResult() {
        this.generatedEvents = new HashSet<>();
        this.usedAggregates = new HashSet<>();
        this.commandCalled = new HashSet<>();
    }

    public Set<String> getGeneratedEvents() {
        return generatedEvents;
    }

    public void addGeneratedEvent(String event) {
        this.generatedEvents.add(event);
    }

    public Set<String> getUsedAggregates() {
        return usedAggregates;
    }

    public void addUsedAggregate(String aggregate) {
        this.usedAggregates.add(aggregate);
    }

    public Set<String> getCommandCalled() {
        return commandCalled;
    }
    public void addCommandCalled(String commandCalled) {
        this.commandCalled.add(commandCalled);
    }
}

