package org.jenkinsci.plugins.pretestedintegration.exceptions;

import java.io.IOException;

/**
 * Used when applying commits failed unexpectedly.
 */
public class CommitFailedException extends IOException {

    /**
     * Constructor for CommitFailedException
     * @param message The Exception message.
     */
    public CommitFailedException(String message) {
        super(message);
    }
}
