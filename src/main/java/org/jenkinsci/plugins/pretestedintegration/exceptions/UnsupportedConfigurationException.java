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
    
    public static final String ILLEGAL_CONFIG_NO_REPO_NAME_DEFINED = String.format("You have not defined a repository name in your Pre Tested Integration configuration, but you have multiple scm checkouts listed in your scm config");

    public UnsupportedConfigurationException(String message) {        
        super(message);        
    }
}
