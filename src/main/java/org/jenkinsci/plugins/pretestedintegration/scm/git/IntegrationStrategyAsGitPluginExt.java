package org.jenkinsci.plugins.pretestedintegration.scm.git;

import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.Launcher;
import hudson.model.*;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.Revision;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.pretestedintegration.exceptions.IntegrationFailedException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.NothingToDoException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.UnsupportedConfigurationException;

/**
 * Abstract class representing a strategy to apply when merging pretested commits into the integration integrationBranch.
 */
public abstract interface IntegrationStrategyAsGitPluginExt{

    /**
     * Integrates the commits into the integration integrationBranch.
     *
     * @param scm
     * @param build The Build
     * @param git
     * @param listener The BuildListener
     * @throws IntegrationFailedException when integration fails
     * @throws NothingToDoException when there's nothing to do
     * @throws UnsupportedConfigurationException when part of the configuration isn't supported
     */

    public abstract void integrateAsGitPluginExt(GitSCM scm, Run<?, ?> build, GitClient git, TaskListener listener, Revision marked, Revision rev, GitBridge bridge)
            throws IntegrationFailedException, NothingToDoException, UnsupportedConfigurationException;


}
