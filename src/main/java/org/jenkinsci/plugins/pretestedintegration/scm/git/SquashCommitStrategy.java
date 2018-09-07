package org.jenkinsci.plugins.pretestedintegration.scm.git;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.Branch;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.Revision;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.pretestedintegration.AbstractSCMBridge;
import org.jenkinsci.plugins.pretestedintegration.IntegrationStrategyDescriptor;
import org.jenkinsci.plugins.pretestedintegration.exceptions.IntegrationFailedException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.IntegrationUnknownFailureException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.NothingToDoException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.UnsupportedConfigurationException;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Commit Strategy that squashes multiple commits into a single commit.
 */
public class SquashCommitStrategy extends GitIntegrationStrategy {

    private static final Logger LOGGER = Logger.getLogger(SquashCommitStrategy.class.getName());

    /**
     * Strategy name. Used in UI.
     * Strategies used to be called Behaviors, hence the field name.
     */
    private static final String B_NAME = "Squashed commit";

    /**
     * Constructor for SquashCommitStrategy.
     * DataBound to work with the UI.
     */
    @DataBoundConstructor
    public SquashCommitStrategy() {
    }

    private void doTheIntegration(Run build, TaskListener listener, GitBridge gitbridge, ObjectId commitId, GitClient client, String expandedIntegrationBranch, Branch triggerBranch) throws IntegrationFailedException, NothingToDoException, UnsupportedConfigurationException, IntegrationUnknownFailureException {
        {
            int commitCount;
            try {
                commitCount = PretestedIntegrationGitUtils.countCommits(commitId, client, expandedIntegrationBranch);
                String text = "Branch commit count: " + commitCount;
                LOGGER.log(Level.INFO, GitMessages.LOG_PREFIX + text);
                listener.getLogger().println(GitMessages.LOG_PREFIX + text);
            } catch (IOException | InterruptedException ex) {
                throw new IntegrationFailedException("Failed to count commits.", ex);
            }
            if ( commitCount == 0 ){
                throw new NothingToDoException("Commit count is 0. Already integrated/part of integration branch: " + expandedIntegrationBranch);
            }

            if (tryFastForward(commitId, listener.getLogger(), client, commitCount)) return;
            if (tryRebase(commitId, client, listener.getLogger(), expandedIntegrationBranch)) return;

            String expandedBranchName;
            try {
                expandedBranchName = gitbridge.getExpandedIntegrationBranch(build.getEnvironment(listener));
            } catch (IOException | InterruptedException ex) {
                expandedBranchName = gitbridge.getIntegrationBranch();
            }

            String logMessage = String.format(GitMessages.LOG_PREFIX + "Preparing to squash changes in commit %s on development branch %s to integration branch %s", triggerBranch.getSHA1String(), triggerBranch.getName(), expandedBranchName);
            LOGGER.log(Level.INFO, logMessage);
            listener.getLogger().println(logMessage);
            if (!containsRemoteBranch(client, triggerBranch)) {
                LOGGER.fine("Found no remote branches.");
                try {
                    LOGGER.fine("Setting build description 'Nothing to do':");
                    build.setDescription("Nothing to do");
                    LOGGER.fine("Done setting build description.");
                } catch (IOException ex) {
                    LOGGER.log(Level.FINE, "Failed to update build description", ex);
                }
                logMessage = GitMessages.noRelevantSCMchange(triggerBranch.getName());
                LOGGER.log(Level.WARNING, logMessage);
                throw new NothingToDoException(logMessage);
            }

            String commitAuthor = null; //leaving un-assigned, want to fail later if not assigned;
            try {
                // Collect author
                logMessage = GitMessages.LOG_PREFIX + "Collecting author of last commit on development branch";
                LOGGER.log(Level.INFO, logMessage);
                listener.getLogger().println(logMessage);
                commitAuthor = client.withRepository(new FindCommitAuthorCallback(triggerBranch.getSHA1()));
                logMessage = String.format(GitMessages.LOG_PREFIX + "Done collecting last commit author: %s", commitAuthor);
                LOGGER.log(Level.INFO, logMessage);
                listener.getLogger().println(logMessage);

                logMessage = GitMessages.LOG_PREFIX + "Starting squash merge - without commit:";
                LOGGER.info(logMessage);
                listener.getLogger().println(logMessage);
                listener.getLogger().println(String.format("%s merge --squash %s", GitMessages.LOG_PREFIX, triggerBranch.getName())); // Output asserted in tests.
                client.merge().setSquash(true).setRevisionToMerge(triggerBranch.getSHA1()).execute();
                logMessage = GitMessages.LOG_PREFIX + "Squash merge done";
                LOGGER.info(logMessage);
                listener.getLogger().println(logMessage);
            } catch (IOException | InterruptedException | GitException ex) {
                logMessage = String.format(GitMessages.LOG_PREFIX + "Exception while merging. Logging exception msg: %s", ex.getMessage());
                LOGGER.log(Level.SEVERE, logMessage, ex);
                listener.getLogger().println(logMessage);
                throw new IntegrationFailedException(ex);
            }

            logMessage = GitMessages.LOG_PREFIX + "Merge was successful";
            LOGGER.log(Level.INFO, logMessage);
            listener.getLogger().println(logMessage);

            try {
                logMessage = GitMessages.LOG_PREFIX + "Starting to commit squash merge changes:";
                LOGGER.info(logMessage);
                listener.getLogger().println(logMessage);
                PersonIdent author = getPersonIdent(commitAuthor);

                //relying on git default behaviour to create a SQUAH_MSG file
                // if there is no squash merge message, then there were no changes, and we will therefore do nothing
                FilePath p = new FilePath(client.getWorkTree(), ".git/SQUASH_MSG");
                if (!p.exists()) {
                    throw new NothingToDoException("No SQUASH_MSG found in .git, there was nothing to merge");
                }

                String message_commits = p.readToString().replaceAll("\"", "'");
                String message = String.format("Squashed commit of branch '%s'%n%n%s", triggerBranch.getName(), message_commits);
                client.setAuthor(author);
                client.commit(message);
                logMessage = GitMessages.LOG_PREFIX + "Commit of squashed merge done";
                LOGGER.info(logMessage);
                listener.getLogger().println(logMessage);
            } catch (NothingToDoException ex) {
                throw ex;
            } catch (IOException | GitException | InterruptedException ex) {
                LOGGER.log(Level.SEVERE, logMessage, ex);
                listener.getLogger().println(logMessage);
                throw new IntegrationUnknownFailureException(ex);
            }
            logMessage = GitMessages.LOG_PREFIX + "Commit was successful";
            LOGGER.log(Level.INFO, logMessage);
            listener.getLogger().println(logMessage);
        }
    }

    @Override
    public void integrate(GitSCM scm, Run<?, ?> build, GitClient client, TaskListener listener, Revision marked, Branch triggeredBranch, GitBridge gitbridge) throws IntegrationFailedException, IntegrationUnknownFailureException, NothingToDoException, UnsupportedConfigurationException {

        String expandedRepoName;
        try {
            expandedRepoName = gitbridge.getExpandedRepository(build.getEnvironment(listener));
        } catch (IOException | InterruptedException ex) {
            expandedRepoName = gitbridge.getRepoName();
        }

        if (!PretestedIntegrationGitUtils.isRelevant(triggeredBranch, expandedRepoName)) {
            throw new NothingToDoException("No revision matches configuration in 'Integration repository'");
        }

        String expandedIntegrationBranch;
        try {
            expandedIntegrationBranch = gitbridge.getExpandedIntegrationBranch(build.getEnvironment(listener));
        } catch (IOException | InterruptedException ex) {
            expandedIntegrationBranch = gitbridge.getIntegrationBranch();
        }


        //TODO: Implement robustness, in which situations does this one contain
        // multiple revisons, when two branches point to the same commit?
        // (JENKINS-24909). Check integrationBranch spec before doing anything
        // It could be the last rather than the first that is the wanted
        doTheIntegration((Run) build, listener, gitbridge, triggeredBranch.getSHA1(), client, expandedIntegrationBranch, triggeredBranch);
    }

    /**
     * Descriptor implementation for SquashCommitStrategy
     */
    @Symbol("squash")
    @Extension
    public static final class DescriptorImpl extends IntegrationStrategyDescriptor<SquashCommitStrategy> {

        /**
         * Constructor for the descriptor
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
