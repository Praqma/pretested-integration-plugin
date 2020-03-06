package org.jenkinsci.plugins.pretestedintegration.exceptions;

import java.io.IOException;

/**
 * Used when there is nothing to do.
 */
public class NothingToDoException extends IOException {

    /**
     *
     */
    private static final long serialVersionUID = 9047266663078025643L;

    /**
     * Constructor for NothingToDoException
     */
    public NothingToDoException() {
        super("Nothing to do.");
    }

    /**
     * Constructor for NothingToDoException
     * @param message the Exception message
     */
    public NothingToDoException(String message) {
        super("Nothing to do. The reason is: " + message);
    }
}
