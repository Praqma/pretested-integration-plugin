/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jenkinsci.plugins.pretestedintegration.exceptions;

import java.io.IOException;

/**
 *
 * @author Mads
 */
public class UnsupportedConfigurationException extends IOException {
    
    public static final String ILLEGAL_CONFIG_NO_REPO_NAME_DEFINED = String.format("You have multiple git repositories defined, but none of them matches your pretested integration repostiory. If using more than 1 repository, remotes must be explicitly named in the Git configuration");
    public static final String AMBIGUIUTY_IN_REMOTE_NAMES = "You have multiple git repositories defined, and more than one have the same name (or defaults to the same name). Pretested Integration is unable to select the correct one.";

    public UnsupportedConfigurationException(String message) {        
        super(message);        
    }
}
