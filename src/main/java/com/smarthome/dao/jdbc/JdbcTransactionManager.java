package com.smarthome.dao.jdbc;

import com.smarthome.dao.TransactionManager;
import com.smarthome.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/**
 * JDBC implementation of {@link TransactionManager}.
 * Coordinates atomic transactions by managing JDBC connection transaction boundaries.
 * <p>
 * <b>Thread-safety strategy:</b> The connection provider and transaction state are tracked
 * per-thread using thread-local binding, making transaction coordination thread-safe.
 * </p>
 */
public class JdbcTransactionManager implements TransactionManager {

    private static final Logger logger = LoggerFactory.getLogger(JdbcTransactionManager.class);

    private final JdbcConnectionProvider connectionProvider;

    /**
     * Constructs a JdbcTransactionManager using the provided connection provider.
     *
     * @param connectionProvider connection provider
     */
    public JdbcTransactionManager(JdbcConnectionProvider connectionProvider) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider cannot be null");
    }

    @Override
    public <T> T executeInTransaction(TransactionCallable<T> callable) {
        Objects.requireNonNull(callable, "callable cannot be null");

        // Check if there is already a thread-bound connection (nested transaction)
    if (connectionProvider.hasThreadBoundConnection()) {
        try {
            return callable.call();   // participate in the outer transaction
            } catch (RuntimeException re) {
                throw re;
            } catch (Exception e) {
                throw new DataAccessException("Transaction execution failed", e);
            }
    }

        Connection rawConn = null;
        try {
            rawConn = connectionProvider.getRawConnection();
            rawConn.setAutoCommit(false);
            connectionProvider.bindThreadConnection(rawConn);

            T result = callable.call();

            rawConn.commit();
            return result;
        } catch (Exception ex) {
            if (rawConn != null) {
                try {
                    rawConn.rollback();
                    logger.debug("Rolled back active transaction due to error: {}", ex.getMessage());
                } catch (SQLException rbEx) {
                    logger.error("Failed to rollback transaction", rbEx);
                }
            }
            if (ex instanceof RuntimeException re) {
                throw re;
            }
            throw new DataAccessException("Transaction rolled back due to failure", ex);
        } finally {
            connectionProvider.unbindThreadConnection();
            if (rawConn != null) {
                try {
                    rawConn.setAutoCommit(true);
                    rawConn.close();
                } catch (SQLException closeEx) {
                    logger.warn("Failed to close transactional connection", closeEx);
                }
            }
        }
    }

    @Override
    public void executeInTransaction(TransactionRunnable runnable) {
        Objects.requireNonNull(runnable, "runnable cannot be null");
        executeInTransaction(() -> {
            runnable.run();
            return null;
        });
    }
}

