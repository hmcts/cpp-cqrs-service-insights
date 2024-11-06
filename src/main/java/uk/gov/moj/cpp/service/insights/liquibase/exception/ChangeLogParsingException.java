package uk.gov.moj.cpp.service.insights.liquibase.exception;

public class ChangeLogParsingException extends Exception {
    public ChangeLogParsingException(String message) {
        super(message);
    }

    public ChangeLogParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}

