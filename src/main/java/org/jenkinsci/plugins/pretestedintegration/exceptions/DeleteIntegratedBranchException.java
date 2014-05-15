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
