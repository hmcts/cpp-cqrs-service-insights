package uk.gov.moj.cpp.service.insights.liquibase.collector;


import uk.gov.moj.cpp.service.insights.liquibase.model.Service;

import java.util.List;

public interface IServiceCollector {
    List<Service> collectServices(String rootDirectory);
}

