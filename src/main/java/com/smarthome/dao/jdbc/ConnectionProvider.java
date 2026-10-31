package com.smarthome.dao.jdbc;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Abstraction for supplying database connections.
 * <p>
 * Thread-safety strategy: Implementations must ensure thread-safe connection retrieval.
 * </p>
 */
public interface ConnectionProvider extends AutoCloseable {

    /**
     * Obtains a JDBC connection for executing database queries.
     *
     * @return active JDBC Connection
     * @throws SQLException if a database access error occurs
     */
    Connection getConnection() throws SQLException;

    @Override
    default void close() {
        // Default no-op for providers that do not require explicit cleanup
    }
}

