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
public class NothingToDoException extends IOException {
    public NothingToDoException() {
        super("Nothing to do");
    }
    
    public NothingToDoException(String message) {
        super("Nothing to do the reason is: "+message);
    }
}
