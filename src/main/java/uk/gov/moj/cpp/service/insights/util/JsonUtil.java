package uk.gov.moj.cpp.service.insights.util;

import java.util.List;
import java.util.Map;

public abstract class JsonUtil {
    /**
     * Converts a Map<String, String> to a JSON-formatted string.
     *
     * @param map The map to convert.
     * @return A JSON-formatted string representing the map.
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
     * @param list The list of strings to convert.
     * @return A JSON array string.
     */
    public static String toJsonArray(List<String> list) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < list.size(); i++) {
            sb.append("\"").append(escapeJson(list.get(i))).append("\"");
            if (i < list.size() - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Escapes special characters in a string for JSON formatting.
     *
     * @param text The string to escape.
     * @return The escaped string.
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
