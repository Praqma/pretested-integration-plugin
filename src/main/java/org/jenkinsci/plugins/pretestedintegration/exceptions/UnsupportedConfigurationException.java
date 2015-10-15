package org.jenkinsci.plugins.pretestedintegration.exceptions;

import java.io.IOException;

public class UnsupportedConfigurationException extends IOException {

    public static final String ILLEGAL_CONFIG_NO_REPO_NAME_DEFINED = String.format("You have multiple git repositories defined, but none of them matches your pretested integration repostiory. If using more than 1 repository, remotes must be explicitly named in the Git configuration");
    public static final String AMBIGUIUTY_IN_REMOTE_NAMES = "You have multiple git repositories defined, and more than one have the same name (or defaults to the same name). Pretested Integration is unable to select the correct one.";
    public static final String MULTISCM_REQUIRE_EXPLICIT_NAMING = "You have not explicitly named all your repositories. When using Multiple SCM with Git SCM, we require that all repositories are named. Fill out the 'Name' field for all your repositories";
    public static final String AMBIGUIUITY_IN_BUILD_DATA = "Multiple revisions with same remote detected. Cannot determine which one to use.";

    public UnsupportedConfigurationException(String message) {
        super(message);
    }
}
