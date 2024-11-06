package uk.gov.moj.cpp.service.insights.example;


import java.util.Set;

public interface Filter {
    Set<String> apply(Set<String> input);
}

