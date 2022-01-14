package org.jenkinsci.plugins.pretestedintegration.scm.git;

import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.Branch;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.Revision;
import org.eclipse.jgit.lib.ObjectId;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.pretestedintegration.AbstractSCMBridge;
import org.jenkinsci.plugins.pretestedintegration.IntegrationStrategyDescriptor;
import org.jenkinsci.plugins.pretestedintegration.exceptions.IntegrationFailedException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.IntegrationUnknownFailureException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.NothingToDoException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.UnsupportedConfigurationException;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Integration strategy for merging multiple commits.
 * Merges in all the commits without squashing them.
 * Provides a custom merge commit message.
 */
public class FFOnlyStrategy extends GitIntegrationStrategy {

    private static final Logger LOGGER = Logger.getLogger(FFOnlyStrategy.class.getName());

    /**
     * Strategy name. Used in UI.
     * Strategies used to be called Behaviors, hence the field name.
     */
    private static final String B_NAME = "Fast forward only";

    private void doTheIntegration(Run build, TaskListener listener, GitBridge gitbridge, ObjectId commitId,
            GitClient client, String expandedIntegrationBranch, Branch triggerBranch) throws IntegrationFailedException,
            NothingToDoException, UnsupportedConfigurationException, IntegrationUnknownFailureException {
        // Get the commit count
        int commitCount;
        try {
            commitCount = PretestedIntegrationGitUtils.countCommits(commitId, client, expandedIntegrationBranch);
            String text = "Branch commit count: " + commitCount;
            LOGGER.log(Level.INFO, GitMessages.LOG_PREFIX + text);
            listener.getLogger().println(GitMessages.LOG_PREFIX + text);
        } catch (IOException | InterruptedException ex) {
            throw new IntegrationFailedException("Failed to count commits.", ex);
        }
        if (commitCount == 0) {
            throw new NothingToDoException(
                    "Commit count is 0. Already integrated/part of integration branch: " + expandedIntegrationBranch);
        }

        if (tryFastForward(commitId, listener.getLogger(), client)) {
            return;
        } else {
            throw new IntegrationFailedException("FastForward --ff-only failed");
        }
    }

    private boolean shortCommitMessage = false;

    /**
     * Constructor for FFOnlyStrategy.
     * DataBound to work in UI.
     */
    @DataBoundConstructor
    public FFOnlyStrategy() {
    }

    @Override
    public void integrate(GitSCM scm, Run<?, ?> build, GitClient git, TaskListener listener, Revision marked,
            Branch triggeredBranch, GitBridge gitbridge) throws IOException, InterruptedException {
        String expandedRepoName;
        try {
            expandedRepoName = gitbridge.getExpandedRepository(build.getEnvironment(listener));
        } catch (IOException | InterruptedException ex) {
            expandedRepoName = gitbridge.getRepoName();
        }

        if (!PretestedIntegrationGitUtils.isRelevant(triggeredBranch, expandedRepoName)) {
            throw new NothingToDoException("No revision matches configuration in 'Integration repository'");
        }

        String expandedIntegrationBranch = gitbridge.getExpandedIntegrationBranch(build.getEnvironment(listener));
        doTheIntegration((Run) build, listener, gitbridge, triggeredBranch.getSHA1(), git, expandedIntegrationBranch,
                triggeredBranch);
    }

    public boolean isShortCommitMessage() {
        return shortCommitMessage;
    }

    @DataBoundSetter
    public void setShortCommitMessage(boolean shortCommitMessage) {
        this.shortCommitMessage = shortCommitMessage;
    }

    /**
     * Descriptor implementation for FFOnlytStrategy
     */
    @Symbol("ffonly")
    @Extension
    public static final class DescriptorImpl extends IntegrationStrategyDescriptor<FFOnlyStrategy> {

        /**
         * Constructor for the Descriptor
         */
        public DescriptorImpl() {
            load();
        }

        /**
         * {@inheritDoc }
         */
        @Override
        public String getDisplayName() {
            return B_NAME;
        }

        /**
         * {@inheritDoc }
         */
        @Override
        public boolean isApplicable(Class<? extends AbstractSCMBridge> bridge) {
            return GitBridge.class.equals(bridge);
        }
    }
}
