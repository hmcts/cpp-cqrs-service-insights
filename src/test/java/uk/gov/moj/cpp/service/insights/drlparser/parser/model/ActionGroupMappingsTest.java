package uk.gov.moj.cpp.service.insights.drlparser.parser.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

class ActionGroupMappingsTest {

    @Test
    void testAddCommandAction() {
        ActionGroupMappings mappings = new ActionGroupMappings();
        Set<String> groups = Set.of("Group A", "Permission B");
        mappings.addCommandAction("CommandAction1", groups);

        assertTrue(mappings.getCommandActionToGroupsMap().containsKey("CommandAction1"));
        assertEquals(groups, mappings.getCommandActionToGroupsMap().get("CommandAction1"));
    }

    @Test
    void testAddQueryAction() {
        ActionGroupMappings mappings = new ActionGroupMappings();
        Set<String> groups = Set.of("Group X", "Permission Y");
        mappings.addQueryAction("QueryAction1", groups);

        assertTrue(mappings.getQueryActionToGroupsMap().containsKey("QueryAction1"));
        assertEquals(groups, mappings.getQueryActionToGroupsMap().get("QueryAction1"));
    }

    @Test
    void testEmptyMappings() {
        ActionGroupMappings mappings = new ActionGroupMappings();

        assertTrue(mappings.getCommandActionToGroupsMap().isEmpty());
        assertTrue(mappings.getQueryActionToGroupsMap().isEmpty());
    }
}
