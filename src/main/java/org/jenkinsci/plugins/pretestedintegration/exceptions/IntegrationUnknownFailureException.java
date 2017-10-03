package org.jenkinsci.plugins.pretestedintegration.exceptions;

import java.io.IOException;

/**
 * Used when integration of the commit fails unexpectedly.
 */
public class IntegrationUnknownFailureException extends IOException {

    /**
     * Constructor for IntegrationFailedException
     */
    public IntegrationUnknownFailureException() {
        super("Unknown root cause");
    }

    /**
     * Constructor for IntegrationFailedException
     * @param cause The causal exception
     */
    public IntegrationUnknownFailureException(Exception cause) {
        super("Unknown root cause", cause);
    }

    /**
     * Constructor for IntegrationFailedException
     * @param message The Exception message
     */
    public IntegrationUnknownFailureException(String message) {
        super(message);
    }

    /**
     * Constructor for IntegrationFailedException
     * @param message The Exception message
     * @param cause The causal exception
     */
    public IntegrationUnknownFailureException(String message, Exception cause) {
        super(message, cause);
    }
}
