package com.smarthome.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Enforces Architectural Rule 2:
 * com.smarthome.domain must remain pure Java with zero imports from javafx.* or java.sql.*.
 */
class DomainArchitectureTest {

    @Test
    @DisplayName("Verify domain source files contain no forbidden javafx.* or java.sql.* imports")
    void verifyNoForbiddenImportsInDomain() throws IOException {
        Path domainSourceDir = Paths.get("src", "main", "java", "com", "smarthome", "domain");
        assertTrue(Files.exists(domainSourceDir), "Domain source directory must exist: " + domainSourceDir);

        try (Stream<Path> javaFiles = Files.walk(domainSourceDir)) {
            List<Path> forbiddenFiles = javaFiles
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(this::containsForbiddenImports)
                    .toList();

            assertTrue(
                    forbiddenFiles.isEmpty(),
                    "Domain package violates Architectural Rule 2 by importing javafx.* or java.sql.*: " + forbiddenFiles
            );
        }
    }

    private boolean containsForbiddenImports(Path path) {
        try {
            List<String> lines = Files.readAllLines(path);
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("import javafx.") || trimmed.startsWith("import java.sql.")) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file: " + path, e);
        }
    }
}
