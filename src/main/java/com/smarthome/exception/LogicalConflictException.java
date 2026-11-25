package com.smarthome.exception;

import java.util.Collections;
import java.util.List;

/**
 * Checked exception thrown when a rule validation or domain operation causes a logical conflict,
 * such as introducing a circular dependency in the rule execution graph.
 */
public class LogicalConflictException extends Exception {

    private final List<String> cyclePath;

    /**
     * Constructs a LogicalConflictException with a message.
     */
    public LogicalConflictException(String message) {
        super(message);
        this.cyclePath = Collections.emptyList();
    }

    /**
     * Constructs a LogicalConflictException with a message and cycle path.
     */
    public LogicalConflictException(String message, List<String> cyclePath) {
        super(message);
        this.cyclePath = cyclePath != null ? List.copyOf(cyclePath) : Collections.emptyList();
    }

    /**
     * Constructs a LogicalConflictException with a message and cause.
     */
    public LogicalConflictException(String message, Throwable cause) {
        super(message, cause);
        this.cyclePath = Collections.emptyList();
    }

    /**
     * Retrieves the sequence of rule identifiers forming the detected cycle.
     */
    public List<String> getCyclePath() {
        return cyclePath;
    }
}
