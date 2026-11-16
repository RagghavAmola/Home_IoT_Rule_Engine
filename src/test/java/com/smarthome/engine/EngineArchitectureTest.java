package com.smarthome.engine;

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
 * Architectural test enforcing Rule 2 from AGENTS.md:
 * Engine and Service layers must be pure Java with zero imports of javafx.* or java.sql.*.
 */
class EngineArchitectureTest {

    @Test
    @DisplayName("Verify engine layer contains no javafx.*, java.sql.*, or dao imports")
    void verifyNoForbiddenImportsInEngine() throws IOException {
        Path engineDir = Paths.get("src", "main", "java", "com", "smarthome", "engine");
        assertTrue(Files.exists(engineDir), "Engine directory must exist");

        try (Stream<Path> files = Files.walk(engineDir)) {
            List<Path> violations = files
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(this::containsForbiddenEngineImports)
                    .toList();

            assertTrue(violations.isEmpty(), "Engine layer contains forbidden imports: " + violations);
        }
    }

    @Test
    @DisplayName("Verify service layer contains no javafx.* or java.sql.* imports")
    void verifyNoForbiddenImportsInService() throws IOException {
        Path serviceDir = Paths.get("src", "main", "java", "com", "smarthome", "service");
        assertTrue(Files.exists(serviceDir), "Service directory must exist");

        try (Stream<Path> files = Files.walk(serviceDir)) {
            List<Path> violations = files
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(this::containsForbiddenImports)
                    .toList();

            assertTrue(violations.isEmpty(), "Service layer contains forbidden imports: " + violations);
        }
    }

    private boolean containsForbiddenEngineImports(Path path) {
        try {
            List<String> lines = Files.readAllLines(path);
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("import javafx.") || trimmed.startsWith("import java.sql.") || trimmed.startsWith("import com.smarthome.dao.")) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read " + path, e);
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
            throw new RuntimeException("Failed to read " + path, e);
        }
    }
}

