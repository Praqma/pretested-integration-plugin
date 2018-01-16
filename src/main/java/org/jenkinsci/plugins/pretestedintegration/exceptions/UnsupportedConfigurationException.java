package org.jenkinsci.plugins.pretestedintegration.exceptions;

import java.io.IOException;

/**
 * Used when discovering illegal or unsupported configuration.
 */
public class UnsupportedConfigurationException extends IOException {

    /**
     * Predefined message.
     * Used when there's multiple repositories defined that don't match the Pretested Integration repository.
     */
    public static final String ILLEGAL_CONFIG_NO_REPO_NAME_DEFINED = String.format("You have multiple git repositories defined, but none of them match your pretested integration repostiory. When using more than 1 repository, remotes must be explicitly named in the Git configuration");

    /**
     * Predefined message.
     * Used when there's multiple repositories defined with similar names.
     */
    public static final String AMBIGUITY_IN_REMOTE_NAMES = "You have multiple git repositories defined, and more than one have the same name (or defaults to the same name). Pretested Integration is unable to select the correct one.";

    /**
     * Predefined message.
     * Used when multiple revisions using the same remote were found.
     */
    public static final String AMBIGUITY_IN_BUILD_DATA = "Multiple revisions with same remote detected. Cannot determine which one to use.";

    /**
     * Constructor for UnsupportedConfigurationException.
     * @param message The Exception message
     */
    public UnsupportedConfigurationException(String message) {
        super(message);
    }
}
