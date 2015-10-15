package org.jenkinsci.plugins.pretestedintegration.exceptions;

import java.io.IOException;

public class EstablishWorkspaceException extends IOException {

    public EstablishWorkspaceException(Exception ex) {
        super("Failed to establish workspace. Trace written to log", ex);
    }
}
