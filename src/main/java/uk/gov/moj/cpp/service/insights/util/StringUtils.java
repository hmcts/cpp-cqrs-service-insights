package uk.gov.moj.cpp.service.insights.util;

import uk.gov.moj.cpp.service.insights.drlparser.parser.JavaClassIndexer;

import java.util.Optional;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;

/**
 * Utility class for string operations.
 * Provides methods to manipulate and analyze strings in various formats.
 * This class is immutable and cannot be instantiated.
 */
public final class StringUtils {

    /**
     * Private constructor to prevent instantiation.
     */
    private StringUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated.");
    }

    /**
     * Splits a camel case string into a human-readable format.
     *
     * <p>
     * The method performs the following steps:
     * <ol>
     *     <li>Removes the 'get' prefix if present.</li>
     *     <li>Splits the string based on camel case patterns.</li>
     *     <li>Capitalizes the first letter of each resulting word.</li>
     * </ol>
     * </p>
     *
     * <p>
     * Example:
     * <pre>{@code
     * StringUtils.splitCamelCase("getUserName") // Returns "User Name"
     * }</pre>
     * </p>
     *
     * @param s the camel case string to split
     * @return a human-readable string
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
     *
     * <p>
     * The conversion rules are as follows:
     * <ol>
     *     <li>If the string contains underscores ('_'):
     *         <ul>
     *             <li>Replace underscores with spaces.</li>
     *             <li>Capitalize the first letter of each word.</li>
     *             <li>Convert the remaining letters of each word to lowercase.</li>
     *         </ul>
     *     </li>
     *     <li>Else if the string is entirely in uppercase and contains no underscores:
     *         <ul>
     *             <li>Capitalize only the first character.</li>
     *             <li>Convert the remaining characters to lowercase.</li>
     *         </ul>
     *     </li>
     *     <li>Else:
     *         <ul>
     *             <li>Return the original string as-is.</li>
     *         </ul>
     *     </li>
     * </ol>
     * </p>
     *
     * <p>
     * Examples:
     * <pre>{@code
     * StringUtils.humanReadable("USER_NAME") // Returns "User Name"
     * StringUtils.humanReadable("USERNAME")  // Returns "Username"
     * StringUtils.humanReadable("UserName")  // Returns "UserName"
     * }</pre>
     * </p>
     *
     * @param input the input string to convert
     * @return a human-readable string
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
     * @param input the input string with words separated by spaces
     * @return a string with each word capitalized
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
     * @param input the input string
     * @return a string with only the first character capitalized
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
     * @param input the input string to check
     * @return {@code true} if all alphabetic characters are uppercase; {@code false} otherwise
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
     * <p>
     * The method checks whether a method call is qualified with a scope (e.g., {@code this.method()}),
     * matches a static import, or is unqualified.
     * </p>
     *
     * @param methodCallExpr the {@code MethodCallExpr} instance to analyze
     * @param classInfo      the {@code ClassInfo} containing static imports information
     * @return a string representing the method type, including its scope if applicable
     */
    public static String determineMethodType(MethodCallExpr methodCallExpr, JavaClassIndexer.ClassInfo classInfo) {
        String methodName = methodCallExpr.getNameAsString();
        return methodCallExpr.getScope()
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
     * Extracts an enum constant from an expression.
     *
     * <p>
     * The method handles both name expressions and string literals,
     * resolving them using the provided {@code ClassInfo} and {@code JavaClassIndexer}.
     * </p>
     *
     * @param expr      the {@code Expression} instance to extract from
     * @param classInfo the {@code ClassInfo} for resolving variables
     * @param indexer   the {@code JavaClassIndexer} instance
     * @return the enum constant as a string, or {@code null} if not applicable
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
     * Extracts a string value from an expression.
     *
     * <p>
     * The method handles both string literals and name expressions,
     * resolving them using the provided {@code ClassInfo} and {@code JavaClassIndexer}.
     * </p>
     *
     * @param expr      the {@code Expression} instance to extract from
     * @param classInfo the {@code ClassInfo} for resolving variables
     * @param indexer   the {@code JavaClassIndexer} instance
     * @return the extracted string value, or {@code null} if not applicable
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
     * <p>
     * The method attempts to retrieve the initializer of a static variable and returns its string representation.
     * </p>
     *
     * @param varName   the name of the variable to resolve
     * @param classInfo the {@code ClassInfo} containing variable details
     * @param indexer   the {@code JavaClassIndexer} instance
     * @return an {@code Optional} containing the resolved value if found; otherwise, an empty {@code Optional}
     */
    private static Optional<String> resolveStaticVariable(String varName, JavaClassIndexer.ClassInfo classInfo, JavaClassIndexer indexer) {
        return classInfo.getField(varName)
                .flatMap(fieldDecl -> fieldDecl.getVariable(0).getInitializer())
                .map(Expression::toString)
                .map(init -> init.replace("\"", ""));
    }
}
