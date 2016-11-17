package org.jenkinsci.plugins.pretestedintegration.exceptions;

import java.io.IOException;

/**
 * Used when integration of the commit fails unexpectedly.
 */
public class IntegrationAllowedNoCommitException extends IOException {

    /**
     * Constructor for IntegrationFailedException
     */
    public IntegrationAllowedNoCommitException() {
        super("Allowed number of commits");
    }

    /**
     * Constructor for IntegrationFailedException
     * @param cause The causal exception
     */
    public IntegrationAllowedNoCommitException(Exception cause) {
        super("Merge failure", cause);
    }

    /**
     * Constructor for IntegrationFailedException
     * @param message The Exception message
     */
    public IntegrationAllowedNoCommitException(String message) {
        super(message);
    }

    /**
     * Constructor for IntegrationFailedException
     * @param message The Exception message
     * @param cause The causal exception
     */
    public IntegrationAllowedNoCommitException(String message, Exception cause) {
        super(message, cause);
    }
}
