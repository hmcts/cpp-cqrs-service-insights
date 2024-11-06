package uk.gov.moj.cpp.service.insights.util;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class StringListSorter {

    /**
     * Sorts the provided list of strings in ascending order in a case-insensitive manner.
     * Strings equal to "id" (case-insensitive) are prioritized and moved to the top of the list.
     *
     * <p>
     * The sorting is performed in-place. If you need to preserve the original list,
     * consider creating a copy before invoking this method.
     * </p>
     *
     * @param strings the list of strings to be sorted
     * @throws NullPointerException if the provided list is null
     */
    public static void sortStringsWithIdFirst(List<String> strings) {
        Objects.requireNonNull(strings, "The list of strings cannot be null.");

        Comparator<String> customComparator = Comparator
                // Primary Criterion: Strings equal to "id" (case-insensitive) come first
                .comparing((String s) -> !s.equalsIgnoreCase("id"))
                // Secondary Criterion: Ascending order, case-insensitive
                .thenComparing(String::compareToIgnoreCase);

        strings.sort(customComparator);
    }
}
