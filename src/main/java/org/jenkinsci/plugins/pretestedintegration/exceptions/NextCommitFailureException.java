/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.jenkinsci.plugins.pretestedintegration.exceptions;

import javax.imageio.IIOException;

/**
 *
 * @author Mads
 */
public class NextCommitFailureException extends IIOException {
    public NextCommitFailureException() {
        super("Failed to get the next commit to integrate");
    }
    
    public NextCommitFailureException(String message) {
        super("Failed to get the next commit to integrate: "+message);
    }
    
    public NextCommitFailureException(Exception ex) {
        super("Failed to get the next commit to integrate", ex);
    }
}
