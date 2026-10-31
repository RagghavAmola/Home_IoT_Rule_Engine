package com.smarthome.dao.jdbc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class DatabaseInitializerTest {

    @Test
    @DisplayName("DatabaseInitializer runs idempotently without error when executed multiple times")
    void testIdempotentInitialization() throws IOException {
        File tempDb = File.createTempFile("test-init-" + UUID.randomUUID(), ".db");
        tempDb.deleteOnExit();
        String url = "jdbc:sqlite:" + tempDb.getAbsolutePath();

        JdbcConnectionProvider connectionProvider = new JdbcConnectionProvider(url, "", "");
        DatabaseInitializer initializer = new DatabaseInitializer(connectionProvider);

        // Run once
        assertDoesNotThrow(() -> initializer.initialize(true), "First initialization must succeed");

        // Run second time (testing idempotency of CREATE TABLE IF NOT EXISTS and INSERT OR IGNORE)
        assertDoesNotThrow(() -> initializer.initialize(true), "Subsequent initialization must be idempotent");
    }
}

