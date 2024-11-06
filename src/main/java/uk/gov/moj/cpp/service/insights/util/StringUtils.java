package uk.gov.moj.cpp.service.insights.util;


import uk.gov.moj.cpp.service.insights.drlparser.parser.JavaClassIndexer;

import java.util.Optional;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;

/**
 * Utility class for string operations.
 */
public class StringUtils {

    /**
     * Splits a camel case string into a human-readable format.
     *
     * @param s Camel case string.
     * @return Human-readable string.
     */
    public static String splitCamelCase(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }

        // Remove 'get' prefix if present
        if (s.startsWith("get")) {
            s = s.substring(3);
        }

        // Split the string based on the provided regex patterns
        String[] words = s.replaceAll(
                String.format("%s|%s|%s",
                        "(?<=[A-Z])(?=[A-Z][a-z])",
                        "(?<=[^A-Z])(?=[A-Z])",
                        "(?<=[A-Za-z])(?=[^A-Za-z])"
                ),
                " "
        ).split(" ");

        // Capitalize the first letter of each word
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            if (!word.isEmpty()) {
                // Capitalize the first character and append the rest of the word
                String capitalizedWord = Character.toUpperCase(word.charAt(0)) +
                        (word.length() > 1 ? word.substring(1) : "");
                result.append(capitalizedWord);
                if (i < words.length - 1) {
                    result.append(" ");
                }
            }
        }

        return result.toString();
    }

    /**
     * Converts a string to a human-readable format.
     * <p>
     * Rules:
     * 1. If the string contains underscores ('_'):
     * - Replace underscores with spaces.
     * - Capitalize the first letter of each word.
     * - Convert the remaining letters of each word to lowercase.
     * 2. Else if the string is entirely in uppercase and contains no underscores:
     * - Capitalize only the first character.
     * - Convert the remaining characters to lowercase.
     * 3. Else:
     * - Return the original string as-is.
     *
     * @param input The input string.
     * @return Human-readable string.
     */
    public static String humanReadable(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        if (input.contains("_")) {
            return capitalizeWords(input.replace('_', ' '));
        } else if (isAllUpperCase(input)) {
            return capitalizeFirstLetter(input);
        } else {
            return input;
        }
    }

    /**
     * Capitalizes the first letter of each word in the input string.
     * Converts the remaining letters of each word to lowercase.
     *
     * @param input The input string with words separated by spaces.
     * @return Capitalized string.
     */
    private static String capitalizeWords(String input) {
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;

        for (char ch : input.toCharArray()) {
            if (Character.isWhitespace(ch)) {
                capitalizeNext = true;
                result.append(ch);
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(ch));
                capitalizeNext = false;
            } else {
                result.append(Character.toLowerCase(ch));
            }
        }

        return result.toString();
    }

    /**
     * Capitalizes only the first letter of the input string.
     * Converts the remaining characters to lowercase.
     *
     * @param input The input string.
     * @return String with only the first character capitalized.
     */
    private static String capitalizeFirstLetter(String input) {
        if (input.length() == 1) {
            return input.toUpperCase();
        }
        return Character.toUpperCase(input.charAt(0)) + input.substring(1).toLowerCase();
    }

    /**
     * Checks if the entire string is in uppercase.
     *
     * @param input The input string.
     * @return True if all characters are uppercase letters; false otherwise.
     */
    private static boolean isAllUpperCase(String input) {
        for (char ch : input.toCharArray()) {
            if (Character.isLetter(ch) && !Character.isUpperCase(ch)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Determines the method type based on its scope and static imports.
     *
     * @param mce       MethodCallExpr instance.
     * @param classInfo ClassInfo containing static imports.
     * @return A string representing the method type.
     */
    public static String determineMethodType(MethodCallExpr mce, JavaClassIndexer.ClassInfo classInfo) {
        String methodName = mce.getNameAsString();
        return mce.getScope()
                .map(scope -> scope + "." + methodName + "()")
                .orElseGet(() -> {
                    for (String staticImport : classInfo.getStaticImports()) {
                        if (staticImport.endsWith("." + methodName)) {
                            String classScope = staticImport.substring(0, staticImport.lastIndexOf('.'));
                            return classScope + "." + methodName + "()";
                        } else if (staticImport.endsWith(".*")) {
                            String classScope = staticImport.substring(0, staticImport.length() - 2);
                            return classScope + "." + methodName + "()";
                        }
                    }
                    return methodName + "()"; // Default if not found
                });
    }

    /**
     * Extracts enum constant from an expression.
     *
     * @param expr      Expression instance.
     * @param classInfo ClassInfo for resolving variables.
     * @param indexer   JavaClassIndexer instance.
     * @return Enum constant as a string, or null if not applicable.
     */
    public static String extractEnumConstant(Expression expr, JavaClassIndexer.ClassInfo classInfo, JavaClassIndexer indexer) {
        if (expr.isNameExpr()) {
            String varName = expr.asNameExpr().getNameAsString();
            return resolveStaticVariable(varName, classInfo, indexer).orElse(varName);
        } else if (expr.isStringLiteralExpr()) {
            return expr.asStringLiteralExpr().asString();
        }
        return null;
    }

    /**
     * Extracts string value from an expression.
     *
     * @param expr      Expression instance.
     * @param classInfo ClassInfo for resolving variables.
     * @param indexer   JavaClassIndexer instance.
     * @return String value, or null if not applicable.
     */
    public static String extractStringValue(Expression expr, JavaClassIndexer.ClassInfo classInfo, JavaClassIndexer indexer) {
        if (expr.isStringLiteralExpr()) {
            String result = expr.asStringLiteralExpr().asString();
            return resolveStaticVariable(result, classInfo, indexer).orElse(result);
        } else if (expr.isNameExpr()) {
            String varName = expr.asNameExpr().getNameAsString();
            return resolveStaticVariable(varName, classInfo, indexer).orElse(null);
        }
        return null;
    }

    /**
     * Resolves the value of a static variable within a class.
     *
     * @param varName   Variable name.
     * @param classInfo ClassInfo containing variable details.
     * @param indexer   JavaClassIndexer instance.
     * @return Optional containing the resolved value.
     */
    private static Optional<String> resolveStaticVariable(String varName, JavaClassIndexer.ClassInfo classInfo, JavaClassIndexer indexer) {
        return classInfo.getField(varName)
                .flatMap(fieldDecl -> fieldDecl.getVariable(0).getInitializer())
                .map(Expression::toString)
                .map(init -> init.replace("\"", ""));
    }
}

