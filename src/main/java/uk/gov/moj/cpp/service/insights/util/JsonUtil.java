package uk.gov.moj.cpp.service.insights.util;

import java.util.List;
import java.util.Map;

/**
 * Utility class for JSON operations.
 * Provides methods to convert Java Maps and Lists to JSON-formatted strings.
 * This class is immutable and cannot be instantiated.
 */
public final class JsonUtil {

    /**
     * Private constructor to prevent instantiation.
     */
    private JsonUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated.");
    }

    /**
     * Converts a {@code Map<String, String>} to a JSON-formatted string.
     *
     * @param map the map to convert
     * @return a JSON-formatted string representing the map
     */
    public static String mapToJson(Map<String, String> map) {
        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("{");

        int entryCount = 0;
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (entryCount > 0) {
                jsonBuilder.append(",");
            }
            jsonBuilder.append("\"")
                    .append(escapeJson(entry.getKey()))
                    .append("\":\"")
                    .append(escapeJson(entry.getValue()))
                    .append("\"");
            entryCount++;
        }

        jsonBuilder.append("}");
        return jsonBuilder.toString();
    }

    /**
     * Converts a list of strings to a JSON array string.
     *
     * @param list the list of strings to convert
     * @return a JSON array string
     */
    public static String toJsonArray(List<String> list) {
        StringBuilder jsonArrayBuilder = new StringBuilder();
        jsonArrayBuilder.append("[");

        for (int i = 0; i < list.size(); i++) {
            jsonArrayBuilder.append("\"")
                    .append(escapeJson(list.get(i)))
                    .append("\"");
            if (i < list.size() - 1) {
                jsonArrayBuilder.append(",");
            }
        }

        jsonArrayBuilder.append("]");
        return jsonArrayBuilder.toString();
    }

    /**
     * Escapes special characters in a string for JSON formatting.
     *
     * @param text the string to escape
     * @return the escaped string
     */
    private static String escapeJson(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder escaped = new StringBuilder();
        for (char c : text.toCharArray()) {
            switch (c) {
                case '\"':
                    escaped.append("\\\"");
                    break;
                case '\\':
                    escaped.append("\\\\");
                    break;
                case '/':
                    escaped.append("\\/");
                    break;
                case '\b':
                    escaped.append("\\b");
                    break;
                case '\f':
                    escaped.append("\\f");
                    break;
                case '\n':
                    escaped.append("\\n");
                    break;
                case '\r':
                    escaped.append("\\r");
                    break;
                case '\t':
                    escaped.append("\\t");
                    break;
                default:
                    if (c < 0x20 || c > 0x7E) {
                        // Unicode escape
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
            }
        }
        return escaped.toString();
    }
}
