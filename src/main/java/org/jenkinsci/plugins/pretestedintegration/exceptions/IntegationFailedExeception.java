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
public class IntegationFailedExeception extends IOException {
    
    public IntegationFailedExeception() {
        super("Merge failure");
    }
    
    public IntegationFailedExeception(String message) {
        super(message);
    }
       
    public IntegationFailedExeception(Exception ex) {
        super("Merge failure",ex);
    }
    
    public IntegationFailedExeception(String mesage, Exception ex) {
            super(mesage,ex);
    }
}
