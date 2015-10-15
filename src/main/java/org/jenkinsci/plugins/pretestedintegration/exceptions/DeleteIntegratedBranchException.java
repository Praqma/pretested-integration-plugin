package org.jenkinsci.plugins.pretestedintegration.exceptions;

import java.io.IOException;

public class DeleteIntegratedBranchException extends IOException {

    public DeleteIntegratedBranchException() {
        super("Failed to delete integrated branch");
    }

    public DeleteIntegratedBranchException(String message) {
        super(message);
    }

    public DeleteIntegratedBranchException(String message, Exception cause) {
        super(message, cause);
    }
}
