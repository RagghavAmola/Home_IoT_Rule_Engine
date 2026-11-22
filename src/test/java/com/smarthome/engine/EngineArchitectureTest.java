package com.smarthome.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Architectural test enforcing pure-Java boundaries across engine, service, and simulation layers
 * (zero imports of javafx.* or java.sql.*, and no dao imports in engine).
 */
class EngineArchitectureTest {

    private static final List<String> ENGINE_FORBIDDEN_PREFIXES = List.of(
            "javafx.",
            "java.sql.",
            "com.smarthome.dao."
    );

    private static final List<String> LAYER_FORBIDDEN_PREFIXES = List.of(
            "javafx.",
            "java.sql."
    );

    @Test
    @DisplayName("Verify engine layer contains no javafx.*, java.sql.*, or dao imports")
    void verifyNoForbiddenImportsInEngine() throws IOException {
        assertLayerClean(Paths.get("src", "main", "java", "com", "smarthome", "engine"), ENGINE_FORBIDDEN_PREFIXES, "Engine");
    }

    @Test
    @DisplayName("Verify service layer contains no javafx.* or java.sql.* imports")
    void verifyNoForbiddenImportsInService() throws IOException {
        assertLayerClean(Paths.get("src", "main", "java", "com", "smarthome", "service"), LAYER_FORBIDDEN_PREFIXES, "Service");
    }

    @Test
    @DisplayName("Verify simulation layer contains no javafx.* or java.sql.* imports")
    void verifyNoForbiddenImportsInSimulation() throws IOException {
        assertLayerClean(Paths.get("src", "main", "java", "com", "smarthome", "simulation"), LAYER_FORBIDDEN_PREFIXES, "Simulation");
    }

    private void assertLayerClean(Path layerDir, List<String> forbiddenPrefixes, String layerName) throws IOException {
        assertTrue(Files.exists(layerDir), layerName + " directory must exist");

        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(layerDir)) {
            List<Path> javaFiles = files
                    .filter(p -> p.toString().endsWith(".java"))
                    .toList();

            for (Path javaFile : javaFiles) {
                List<String> matched = findForbiddenImports(javaFile, forbiddenPrefixes);
                for (String match : matched) {
                    violations.add(javaFile + " -> " + match);
                }
            }
        }

        assertTrue(violations.isEmpty(), layerName + " layer contains forbidden imports: " + violations);
    }

    private List<String> findForbiddenImports(Path path, List<String> forbiddenPrefixes) {
        try {
            List<String> lines = Files.readAllLines(path);
            List<String> matches = new ArrayList<>();
            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.startsWith("import ")) {
                    continue;
                }
                String importTarget = trimmed.substring("import ".length()).trim();
                if (importTarget.startsWith("static ")) {
                    importTarget = importTarget.substring("static ".length()).trim();
                }
                for (String prefix : forbiddenPrefixes) {
                    if (importTarget.startsWith(prefix)) {
                        matches.add(trimmed);
                        break;
                    }
                }
            }
            return matches;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read " + path, e);
        }
    }
}