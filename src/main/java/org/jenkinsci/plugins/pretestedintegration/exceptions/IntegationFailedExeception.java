package org.jenkinsci.plugins.pretestedintegration.exceptions;

import java.io.IOException;

public class IntegationFailedExeception extends IOException {

    public IntegationFailedExeception() {
        super("Merge failure");
    }

    public IntegationFailedExeception(String message) {
        super(message);
    }

    public IntegationFailedExeception(Exception ex) {
        super("Merge failure", ex);
    }

    public IntegationFailedExeception(String mesage, Exception ex) {
        super(mesage, ex);
    }
}
