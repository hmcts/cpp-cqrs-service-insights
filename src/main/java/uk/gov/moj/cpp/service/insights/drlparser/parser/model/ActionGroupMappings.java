package uk.gov.moj.cpp.service.insights.drlparser.parser.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds mappings between actions and their associated groups/permissions.
 */
public class ActionGroupMappings {

    private final Map<String, Set<String>> commandActionToGroupsMap = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> queryActionToGroupsMap = new ConcurrentHashMap<>();

    public Map<String, Set<String>> getCommandActionToGroupsMap() {
        return commandActionToGroupsMap;
    }

    public Map<String, Set<String>> getQueryActionToGroupsMap() {
        return queryActionToGroupsMap;
    }

    public void addCommandAction(String actionName, Set<String> groupsAndPermissions) {
        this.commandActionToGroupsMap.put(actionName, groupsAndPermissions);
    }

    public void addQueryAction(String actionName, Set<String> groupsAndPermissions) {
        this.queryActionToGroupsMap.put(actionName, groupsAndPermissions);
    }

    /**
     * Merges another ActionGroupMappings into this one.
     *
     * @param other The other ActionGroupMappings to merge.
     */
    public void merge(ActionGroupMappings other) {
        other.commandActionToGroupsMap.forEach((action, groups) ->
                this.commandActionToGroupsMap
                        .computeIfAbsent(action, k -> new HashSet<>())
                        .addAll(groups)
        );

        other.queryActionToGroupsMap.forEach((action, groups) ->
                this.queryActionToGroupsMap
                        .computeIfAbsent(action, k -> new HashSet<>())
                        .addAll(groups)
        );
    }

    /**
     * Returns the total number of actions (command and query).
     *
     * @return The total number of actions.
     */
    public int size() {
        return commandActionToGroupsMap.size() + queryActionToGroupsMap.size();
    }

    /**
     * Checks if there are no actions mapped.
     *
     * @return True if both command and query maps are empty, false otherwise.
     */
    public boolean isEmpty() {
        return commandActionToGroupsMap.isEmpty() && queryActionToGroupsMap.isEmpty();
    }

    @Override
    public String toString() {
        return "ActionGroupMappings{" +
                "commandActionToGroupsMap=" + commandActionToGroupsMap +
                ", queryActionToGroupsMap=" + queryActionToGroupsMap +
                '}';
    }

    public Map<String, Set<String>> collectAllElements() {
        Map<String, Set<String>> combinedMap = new HashMap<>();

        // Collect elements from commandActionToGroupsMap
        commandActionToGroupsMap.forEach((key, value) -> {
            combinedMap.merge(key, new HashSet<>(value), (oldSet, newSet) -> {
                oldSet.addAll(newSet);
                return oldSet;
            });
        });

        // Collect elements from queryActionToGroupsMap
        queryActionToGroupsMap.forEach((key, value) -> {
            combinedMap.merge(key, new HashSet<>(value), (oldSet, newSet) -> {
                oldSet.addAll(newSet);
                return oldSet;
            });
        });

        return combinedMap;
    }
}

