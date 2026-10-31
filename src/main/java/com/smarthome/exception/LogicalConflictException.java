package com.smarthome.exception;

/**
 * Unchecked exception thrown when a domain or engine rule causes a logical conflict or paradox.
 */
public class LogicalConflictException extends RuntimeException {

    public LogicalConflictException(String message) {
        super(message);
    }

    public LogicalConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}

