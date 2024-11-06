package uk.gov.moj.cpp.service.insights.example;


import java.util.Set;
import java.util.stream.Collectors;

public class ExcludeSquareBracketsFilter implements Filter {

    @Override
    public Set<String> apply(Set<String> input) {
        return input.stream()
                .filter(methodName -> !methodName.contains("[") && !methodName.contains("]"))
                .collect(Collectors.toSet());
    }
}
