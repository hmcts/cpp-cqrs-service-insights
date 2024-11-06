package uk.gov.moj.cpp.service.insights.liquibase.parser;


import uk.gov.moj.cpp.service.insights.liquibase.exception.ChangeLogParsingException;
import uk.gov.moj.cpp.service.insights.liquibase.model.Table;

import java.io.File;
import java.util.Map;

public interface IChangeLogParser {
    void parseChangeLog(File xmlFile, Map<String, Table> tables) throws ChangeLogParsingException;
}

