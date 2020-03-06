package org.jenkinsci.plugins.pretestedintegration.exceptions;

import java.io.IOException;

/**
 * Used when the establishment of the workspace failed unexpectedly.
 */
public class EstablishingWorkspaceFailedException extends IOException {

    /**
     *
     */
    private static final long serialVersionUID = 3415461276177623060L;

    /**
     * Constructor for EstablishingWorkspaceFailedException
     * 
     * @param cause The causal Exception.
     */
    public EstablishingWorkspaceFailedException(Exception cause) {
        super("Failed to establish workspace. Trace written to log", cause);
    }
}
