package uk.gov.moj.cpp.service.insights.liquibase.collector;


import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FileCollector {
    public static List<File> collectChangeLogFiles(String directoryPath) throws IOException {
        Path startPath = Paths.get(directoryPath);

        try (Stream<Path> paths = Files.walk(startPath)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".xml"))
                    .map(Path::toFile)
                    .sorted(Comparator.comparing(File::getName))
                    .collect(Collectors.toList());
        }
    }
}

