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
    public final String integrationBranch;
    public final String integrationRepo;
    public final String ucCredentialsId;

    public PretestTriggerCommitAction( final Branch triggerBranch, final String integrationBranch, final String integrationRepo, final String ucCredentialsId ) {
        this.triggerBranch = triggerBranch;
        this.integrationBranch = integrationBranch;
        this.integrationRepo = integrationRepo;
        this.ucCredentialsId = ucCredentialsId;

    }

    public PretestTriggerCommitAction( final Branch triggerBranch ) {
        this.triggerBranch = triggerBranch;
        this.integrationBranch = null;
        this.integrationRepo = null;
        this.ucCredentialsId = null;

    }
}
