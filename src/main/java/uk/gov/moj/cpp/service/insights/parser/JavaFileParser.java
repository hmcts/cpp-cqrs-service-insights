package uk.gov.moj.cpp.service.insights.parser;

import java.io.IOException;
import java.nio.file.Path;

import com.github.javaparser.ast.CompilationUnit;

public interface JavaFileParser {
    CompilationUnit parse(Path file) throws IOException;
}
