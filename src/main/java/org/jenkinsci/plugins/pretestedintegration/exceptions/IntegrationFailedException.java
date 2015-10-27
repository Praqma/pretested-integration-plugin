package org.jenkinsci.plugins.pretestedintegration.exceptions;

import java.io.IOException;

/**
 * Used when integration of the commit fails unexpectedly.
 */
public class IntegrationFailedException extends IOException {

    /**
     * Constructor for IntegrationFailedException
     */
    public IntegrationFailedException() {
        super("Merge failure");
    }

    /**
     * Constructor for IntegrationFailedException
     * @param cause The causal exception
     */
    public IntegrationFailedException(Exception cause) {
        super("Merge failure", cause);
    }

    /**
     * Constructor for IntegrationFailedException
     * @param message The Exception message
     */
    public IntegrationFailedException(String message) {
        super(message);
    }

    /**
     * Constructor for IntegrationFailedException
     * @param message The Exception message
     * @param cause The causal exception
     */
    public IntegrationFailedException(String message, Exception cause) {
        super(message, cause);
    }
}
