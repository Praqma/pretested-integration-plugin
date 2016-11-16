/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jenkinsci.plugins.pretestedintegration.scm.git;

import hudson.model.InvisibleAction;
import hudson.plugins.git.Branch;

/**
 *
 * @author Mads
 */
public class PretestTriggerCommitAction extends InvisibleAction {

    public final Branch triggerBranch;

    public PretestTriggerCommitAction( final Branch triggerBranch) {
        this.triggerBranch = triggerBranch;
    }
}
