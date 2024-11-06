package uk.gov.moj.cpp.service.insights.example;


import java.util.HashSet;
import java.util.Set;

public class FilterManager {

    private final Set<Filter> filters = new HashSet<>();

    public void addFilter(Filter filter) {
        filters.add(filter);
    }

    public Set<String> applyFilters(Set<String> input) {
        Set<String> result = new HashSet<>(input);
        for (Filter filter : filters) {
            result = filter.apply(result);
        }
        return result;
    }
}

