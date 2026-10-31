package com.smarthome.dao;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Architecture test enforcing Rules 1 & 2 from AGENTS.md:
 * - com.smarthome.dao contains interfaces only.
 * - com.smarthome.dao, domain, engine, service must NOT import java.sql.* or javafx.*.
 * - com.smarthome.dao.jdbc is the ONLY package where java.sql.* is permitted.
 */
class DaoArchitectureTest {

    @Test
    @DisplayName("Verify com.smarthome.dao contains interfaces only")
    void verifyDaoPackageContainsInterfacesOnly() {
        Class<?>[] daoClasses = new Class<?>[]{
                DeviceDao.class,
                DeviceStateDao.class,
                RuleDao.class,
                ExecutionLogDao.class,
                TransactionManager.class
        };

        for (Class<?> clazz : daoClasses) {
            assertTrue(
                    clazz.isInterface(),
                    "Class in com.smarthome.dao must be an interface: " + clazz.getName()
            );
        }
    }

    @Test
    @DisplayName("Verify com.smarthome.dao contains no java.sql or javafx imports")
    void verifyNoForbiddenImportsInDao() throws IOException {
        Path daoDir = Paths.get("src", "main", "java", "com", "smarthome", "dao");
        assertTrue(Files.exists(daoDir));

        try (Stream<Path> files = Files.list(daoDir)) {
            List<Path> forbidden = files
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(this::hasForbiddenImports)
                    .toList();

            assertTrue(forbidden.isEmpty(), "com.smarthome.dao contains forbidden imports: " + forbidden);
        }
    }

    @Test
    @DisplayName("Verify java.sql.* appears ONLY in com.smarthome.dao.jdbc")
    void verifyJavaSqlOnlyInDaoJdbc() throws IOException {
        Path rootSrc = Paths.get("src", "main", "java", "com", "smarthome");
        assertTrue(Files.exists(rootSrc));

        try (Stream<Path> allJavaFiles = Files.walk(rootSrc)) {
            List<Path> violatingFiles = allJavaFiles
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.toString().contains("dao" + java.io.File.separator + "jdbc"))
                    .filter(this::importsJavaSql)
                    .toList();

            assertTrue(
                    violatingFiles.isEmpty(),
                    "Architectural rule violated: java.sql.* found outside com.smarthome.dao.jdbc: " + violatingFiles
            );
        }
    }

    private boolean hasForbiddenImports(Path path) {
        try {
            List<String> lines = Files.readAllLines(path);
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("import java.sql.") || trimmed.startsWith("import javafx.")) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read " + path, e);
        }
    }

    private boolean importsJavaSql(Path path) {
        try {
            List<String> lines = Files.readAllLines(path);
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("import java.sql.")) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read " + path, e);
        }
    }
}

