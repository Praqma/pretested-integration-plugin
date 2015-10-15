package org.jenkinsci.plugins.pretestedintegration.exceptions;

import javax.imageio.IIOException;

public class NextCommitFailureException extends IIOException {

    public NextCommitFailureException() {
        super("Failed to get the next commit to integrate");
    }

    public NextCommitFailureException(String message) {
        super("Failed to get the next commit to integrate: " + message);
    }

    public NextCommitFailureException(Exception ex) {
        super("Failed to get the next commit to integrate", ex);
    }
}
