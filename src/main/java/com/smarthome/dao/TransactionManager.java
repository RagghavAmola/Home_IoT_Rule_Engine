package com.smarthome.dao;

/**
 * Transaction boundary abstraction allowing services to orchestrate multi-DAO operations atomically
 * without coupling business logic to JDBC or database drivers.
 */
public interface TransactionManager {

    /**
     * Executes a unit of work within a managed transaction that returns a result.
     */
    <T> T executeInTransaction(TransactionCallable<T> callable);

    /**
     * Executes a unit of work within a managed transaction that produces no result.
     */
    void executeInTransaction(TransactionRunnable runnable);

    /**
     * Functional interface for transactional operations that return a value.
     *
     * @param <T> return type
     */
    @FunctionalInterface
    interface TransactionCallable<T> {
        T call() throws Exception;
    }

    /**
     * Functional interface for transactional operations that return void.
     */
    @FunctionalInterface
    interface TransactionRunnable {
        void run() throws Exception;
    }
}

