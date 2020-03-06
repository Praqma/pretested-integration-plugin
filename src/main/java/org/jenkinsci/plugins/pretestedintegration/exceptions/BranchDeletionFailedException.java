package org.jenkinsci.plugins.pretestedintegration.exceptions;

import java.io.IOException;

/**
 * Used when deleting a branch failed unexpectedly.
 */
public class BranchDeletionFailedException extends IOException {

    /**
     *
     */
    private static final long serialVersionUID = 8367153982104936614L;

    /**
     * Constructor for BranchDeletionFailedException
     */
    public BranchDeletionFailedException() {
        super("Failed to delete branch.");
    }

    /**
     * Constructor for BranchDeletionFailedException
     * @param message The Exception message
     */
    public BranchDeletionFailedException(String message) {
        super(message);
    }

    /**
     * Constructor for BranchDeletionFailedException
     * @param message The Exception message
     * @param cause The causal exception
     */
    public BranchDeletionFailedException(String message, Exception cause) {
        super(message, cause);
    }
}
