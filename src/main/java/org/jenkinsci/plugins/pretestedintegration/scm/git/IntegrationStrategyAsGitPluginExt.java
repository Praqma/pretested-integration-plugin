package org.jenkinsci.plugins.pretestedintegration.scm.git;

import hudson.model.*;
import hudson.plugins.git.Branch;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.Revision;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.pretestedintegration.exceptions.IntegrationFailedException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.NothingToDoException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.UnsupportedConfigurationException;

import java.io.IOException;

/**
 * Abstract class representing a strategy to apply when merging pretested commits into the integration integrationBranch.
 */
public abstract interface IntegrationStrategyAsGitPluginExt{

    /**
     * Integrates the commits into the integration integrationBranch.
     *
     * @param scm Current GIT scm
     * @param build The Build
     * @param git current git client
     * @param listener The BuildListener
     * @param marked marked revision for use in GitExtensions
     * @param triggeredBranch the triggered branch
     * @param bridge the gitbridge
     * @throws IntegrationFailedException The integration failed ala merge
     * @throws NothingToDoException The triggered revision is behind the integration
     * @throws UnsupportedConfigurationException You have a combination that is not supported, like the type of job
     * @throws IOException Unforeseen IO problems
     * @throws InterruptedException Unforeseen problems
     */

    public abstract void integrateAsGitPluginExt(GitSCM scm, Run<?, ?> build, GitClient git, TaskListener listener, Revision marked, Branch triggeredBranch, GitBridge bridge)
        throws IntegrationFailedException, NothingToDoException, UnsupportedConfigurationException, IOException, InterruptedException;


}
