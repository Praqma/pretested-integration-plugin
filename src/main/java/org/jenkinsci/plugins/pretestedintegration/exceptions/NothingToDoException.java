package org.jenkinsci.plugins.pretestedintegration.exceptions;

import java.io.IOException;

public class NothingToDoException extends IOException {

    public NothingToDoException() {
        super("Nothing to do.");
    }

    public NothingToDoException(String message) {
        super("Nothing to do. The reason is: " + message);
    }
}
