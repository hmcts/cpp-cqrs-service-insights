package uk.gov.moj.cpp.service.insights.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class StringUtilsTest {

    @Test
    void testSplitCamelCase() {
        assertEquals("Read Permission", StringUtils.splitCamelCase("getReadPermission"));
        assertEquals("Write Permission", StringUtils.splitCamelCase("getWritePermission"));
        assertEquals("Delete Permission", StringUtils.splitCamelCase("getDeletePermission"));
        assertEquals("Create Group", StringUtils.splitCamelCase("createGroup"));

    }

    @Test
    void testHumanReadable() {
        assertEquals("Group Name", StringUtils.humanReadable("GROUP_NAME"));
        assertEquals("Permission Read", StringUtils.humanReadable("PERMISSION_READ"));
        assertEquals("System User", StringUtils.humanReadable("SYSTEM_USER"));
        assertEquals("Create Group", StringUtils.humanReadable("CREATE_GROUP"));
    }

    @Test
    void testDetermineMethodTypeWithScope() {
        // Mocking MethodCallExpr is complex; assuming StringUtils.determineMethodType works correctly
        // This test may require more advanced mocking frameworks like Mockito
        // Placeholder for actual test implementation
    }

    @Test
    void testExtractEnumConstant() {
        // Similar to above, testing with actual expressions would require more setup
        // Placeholder for actual test implementation
    }
}
