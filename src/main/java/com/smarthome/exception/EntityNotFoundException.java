package com.smarthome.exception;

/**
 * Unchecked exception thrown when an entity is not found by its identifier.
 */
public class EntityNotFoundException extends RuntimeException {

    public EntityNotFoundException(String message) {
        super(message);
    }

    public EntityNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}

