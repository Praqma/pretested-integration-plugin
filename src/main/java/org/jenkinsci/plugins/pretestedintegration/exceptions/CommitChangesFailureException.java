package org.jenkinsci.plugins.pretestedintegration.exceptions;

import java.io.IOException;

public class CommitChangesFailureException extends IOException {

    public CommitChangesFailureException(String message) {
        super(message);
    }
}
