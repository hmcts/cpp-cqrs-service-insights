package uk.gov.moj.cpp.service.insights.parser;


import java.io.IOException;
import java.nio.file.Path;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;

public class JavaFileParserImpl implements JavaFileParser {

    @Override
    public CompilationUnit parse(Path file) throws IOException {
        try {
            return StaticJavaParser.parse(file);
        } catch (Exception e) {
            throw new IOException("Error parsing file: " + file, e);
        }
    }
}

