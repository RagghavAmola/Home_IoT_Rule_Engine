package com.smarthome.exception;

/**
 * Unchecked exception thrown when an underlying data access or persistence operation fails.
 */
public class DataAccessException extends RuntimeException {

    public DataAccessException(String message) {
        super(message);
    }

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}

