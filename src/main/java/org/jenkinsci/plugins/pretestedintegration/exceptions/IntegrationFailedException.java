package org.jenkinsci.plugins.pretestedintegration.exceptions;

import java.io.IOException;

public class IntegrationFailedException extends IOException {

    public IntegrationFailedException() {
        super("Merge failure");
    }

    public IntegrationFailedException(String message) {
        super(message);
    }

    public IntegrationFailedException(Exception ex) {
        super("Merge failure", ex);
    }

    public IntegrationFailedException(String mesage, Exception ex) {
        super(mesage, ex);
    }
}
