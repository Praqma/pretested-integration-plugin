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
public class EstablishWorkspaceException extends IOException {
    public EstablishWorkspaceException(Exception ex) {
        super("Failed to establish workspace. Trace written to log", ex);
    }
}
