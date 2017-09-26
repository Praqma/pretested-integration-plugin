package org.jenkinsci.plugins.pretestedintegration.exceptions;

import java.io.IOException;

/**
 * Used when applying commits failed unexpectedly.
 */
public class PushFailedException extends IOException {

    /**
     * Constructor for PushFailedException
     * @param message The Exception message.
     */
    public PushFailedException(String message) {
        super(message);
    }
}
