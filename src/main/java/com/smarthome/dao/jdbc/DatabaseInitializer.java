package com.smarthome.dao.jdbc;

import com.smarthome.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Initializes the database schema and default seed data idempotently at application startup.
 * <p>
 * <b>Thread-safety strategy:</b> Stateless execution over supplied connection provider.
 * Should be called once during application bootstrap or test fixture setup.
 * </p>
 */
public class DatabaseInitializer {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseInitializer.class);

    private static final String DEFAULT_SCHEMA_PATH = "/db/schema.sql";
    private static final String DEFAULT_SEED_PATH = "/db/seed.sql";

    private final ConnectionProvider connectionProvider;

    public DatabaseInitializer(ConnectionProvider connectionProvider) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider cannot be null");
    }

    /**
     * Executes both schema creation and initial data seeding idempotently.
     */
    public void initialize() {
        initialize(true);
    }

    /**
     * Executes schema creation and optionally runs the seed script.
     *
     * @param runSeed true to execute seed.sql, false to execute schema.sql only
     */
    public void initialize(boolean runSeed) {
        logger.info("Starting database initialization (runSeed={})...", runSeed);
        executeScript(DEFAULT_SCHEMA_PATH);
        if (runSeed) {
            executeScript(DEFAULT_SEED_PATH);
        }
        logger.info("Database initialization completed successfully.");
    }

    /**
     * Executes an individual SQL script resource from the classpath.
     *
     * @param resourcePath classpath path to the .sql file
     */
    public void executeScript(String resourcePath) {
        Objects.requireNonNull(resourcePath, "resourcePath cannot be null");
        List<String> statements = parseSqlStatements(resourcePath);

        try (Connection conn = connectionProvider.getConnection();
             Statement stmt = conn.createStatement()) {

            for (String sql : statements) {
                if (!sql.isBlank()) {
                    stmt.execute(sql);
                }
            }
            logger.debug("Successfully executed {} statements from {}", statements.size(), resourcePath);
        } catch (SQLException e) {
            logger.error("Failed to execute SQL script {}: {}", resourcePath, e.getMessage(), e);
            throw new DataAccessException("Failed to execute database script: " + resourcePath, e);
        }
    }

    private List<String> parseSqlStatements(String resourcePath) {
        InputStream stream = getClass().getResourceAsStream(resourcePath);
        if (stream == null) {
            throw new DataAccessException("Database script resource not found on classpath: " + resourcePath);
        }

        List<String> statements = new ArrayList<>();
        StringBuilder currentStatement = new StringBuilder();
        boolean inSingleQuote = false;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmedLine = line.trim();
                // Skip full-line comments
                if (trimmedLine.startsWith("--") || trimmedLine.startsWith("//") || trimmedLine.isEmpty()) {
                    continue;
                }

                for (int i = 0; i < line.length(); i++) {
                    char c = line.charAt(i);

                    // Check for single line comment start outside quotes
                    if (c == '-' && i + 1 < line.length() && line.charAt(i + 1) == '-' && !inSingleQuote) {
                        break; // ignore rest of the line
                    }

                    if (c == '\'') {
                        inSingleQuote = !inSingleQuote;
                    }

                    if (c == ';' && !inSingleQuote) {
                        String stmt = currentStatement.toString().trim();
                        if (!stmt.isEmpty()) {
                            statements.add(stmt);
                        }
                        currentStatement.setLength(0);
                    } else {
                        currentStatement.append(c);
                    }
                }
                currentStatement.append(" ");
            }

            String remaining = currentStatement.toString().trim();
            if (!remaining.isEmpty()) {
                statements.add(remaining);
            }
        } catch (Exception e) {
            throw new DataAccessException("Error reading script resource: " + resourcePath, e);
        }

        return statements;
    }
}

