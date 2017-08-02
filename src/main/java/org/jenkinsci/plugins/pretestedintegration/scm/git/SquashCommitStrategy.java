package org.jenkinsci.plugins.pretestedintegration.scm.git;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.Branch;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.Revision;
import hudson.plugins.git.util.BuildData;
import org.eclipse.jgit.lib.ObjectId;
import org.jenkinsci.plugins.pretestedintegration.*;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.jgit.lib.PersonIdent;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.pretestedintegration.exceptions.*;
import org.kohsuke.stapler.DataBoundConstructor;

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

    private void doTheIntegration (Run build, TaskListener listener, GitBridge gitbridge, ObjectId commitId, GitClient client, String expandedIntegrationBranch, Branch triggerBranch) throws IntegrationFailedException, NothingToDoException, UnsupportedConfigurationException, IntegrationUnknownFailureException {
        {
            build.addAction(new PretestTriggerCommitAction(triggerBranch));

            int commitCount;
            try {
                commitCount = PretestedIntegrationGitUtils.countCommits(commitId, client, expandedIntegrationBranch);
                String text = "Branch commit count: " + commitCount;
                LOGGER.log(Level.INFO, PretestedIntegrationBuildWrapper.LOG_PREFIX + text);
                listener.getLogger().println(PretestedIntegrationBuildWrapper.LOG_PREFIX + text);
            } catch (IOException | InterruptedException ex) {
                throw new IntegrationFailedException("Failed to count commits.", ex);
            }

            if (tryFastForward(commitId, listener.getLogger(), client, commitCount)) return;
            if (tryRebase(commitId, client, listener.getLogger(), expandedIntegrationBranch)) return;

            String expandedBranchName;
            try {
                expandedBranchName = gitbridge.getExpandedIntegrationBranch(build.getEnvironment(listener));
            } catch (IOException | InterruptedException ex) {
                expandedBranchName = gitbridge.getIntegrationBranch();
            }

            String logMessage = String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Preparing to squash changes in commit %s on development branch %s to integration branch %s", triggerBranch.getSHA1String(), triggerBranch.getName(), expandedBranchName);
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

            String commitAuthor;
            try {
                // Collect author
                logMessage = PretestedIntegrationBuildWrapper.LOG_PREFIX + "Collecting author of last commit on development branch";
                LOGGER.log(Level.INFO, logMessage);
                listener.getLogger().println(logMessage);
                commitAuthor = client.withRepository(new FindCommitAuthorCallback(triggerBranch.getSHA1()));
                logMessage = String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Done colecting last commit author: %s", commitAuthor);
                LOGGER.log(Level.INFO, logMessage);
                listener.getLogger().println(logMessage);

                logMessage = PretestedIntegrationBuildWrapper.LOG_PREFIX + "Starting squash merge - without commit:";
                LOGGER.info(logMessage);
                listener.getLogger().println(logMessage);
                listener.getLogger().println(String.format("%s merge --squash %s", PretestedIntegrationBuildWrapper.LOG_PREFIX, triggerBranch.getName())); // Output asserted in tests.
                client.merge().setSquash(true).setRevisionToMerge(triggerBranch.getSHA1()).execute();
                logMessage = PretestedIntegrationBuildWrapper.LOG_PREFIX + "Squash merge done";
                LOGGER.info(logMessage);
                listener.getLogger().println(logMessage);
            } catch (IOException | InterruptedException | GitException ex) {
                logMessage = String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Exception while merging. Logging exception msg: %s", ex.getMessage());
                LOGGER.log(Level.SEVERE, logMessage, ex);
                listener.getLogger().println(logMessage);
                throw new IntegrationFailedException(ex);
            }

            logMessage = PretestedIntegrationBuildWrapper.LOG_PREFIX + "Merge was successful";
            LOGGER.log(Level.INFO, logMessage);
            listener.getLogger().println(logMessage);

            try {
                logMessage = PretestedIntegrationBuildWrapper.LOG_PREFIX + "Starting to commit squash merge changes:";
                LOGGER.info(logMessage);
                listener.getLogger().println(logMessage);
                PersonIdent author = getPersonIdent(commitAuthor);
                String message_commits = client.getWorkTree().child(".git/SQUASH_MSG").readToString().replaceAll("\"", "'");
                String message = String.format("Squashed commit of branch '%s'%n%n%s", triggerBranch.getName(), message_commits);
                client.setAuthor(author);
                client.commit(message);
                logMessage = PretestedIntegrationBuildWrapper.LOG_PREFIX + "Commit of squashed merge done";
                LOGGER.info(logMessage);
                listener.getLogger().println(logMessage);
            } catch (IOException | GitException | InterruptedException ex) {
                // If ".git/SQUASH_MSG" wasn't found the most likely culrprit is that the merge was an empty
                // one (No changes) for some reason the merge() command does not complain or throw exception when that happens
                if (ex.getMessage().contains("Cannot commit") || ex.getMessage().contains("MERGE_MSG (No such file or directory)")) {
                    logMessage = String.format("%sUnable to commit changes. Most likely you are trying to integrate a change that was already integrated. Message was:%n%s", PretestedIntegrationBuildWrapper.LOG_PREFIX, ex.getMessage());
                } else {
                    logMessage = String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Exception while committing. Logging exception msg: %s", ex.getMessage());
                }
                LOGGER.log(Level.SEVERE, logMessage, ex);
                listener.getLogger().println(logMessage);
                throw new IntegrationUnknownFailureException(ex);
            }
            logMessage = PretestedIntegrationBuildWrapper.LOG_PREFIX + "Commit was successful";
            LOGGER.log(Level.INFO, logMessage);
            listener.getLogger().println(logMessage);
        }
    }
    /**
     * {@inheritDoc}
     */
    @Override
    public void integrate(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, AbstractSCMBridge bridge) throws IntegrationFailedException, IntegrationUnknownFailureException, NothingToDoException, UnsupportedConfigurationException {

        GitBridge gitbridge = (GitBridge) bridge;
        GitClient client;
        try {
            client = gitbridge.findScm(build, listener).createClient(listener, build.getEnvironment(listener), build, build.getWorkspace());
        } catch (InterruptedException | IOException ex) {
            LOGGER.log(Level.SEVERE, "Failed to initialize GitClient", ex);
            throw new IntegrationUnknownFailureException(ex);
        }

        String expandedRepoName;
        try {
            expandedRepoName = gitbridge.getExpandedRepository(build.getEnvironment(listener));
        } catch (IOException | InterruptedException ex) {
            expandedRepoName = gitbridge.getRepoName();
        }
        BuildData buildData = PretestedIntegrationGitUtils.findRelevantBuildData(build, listener.getLogger(), expandedRepoName);

        //TODO: Implement robustness, in which situations does this one contain
        // multiple revisons, when two branches point to the same commit?
        // (JENKINS-24909). Check integrationBranch spec before doing anything
        // It could be the last rather than the first that is the wanted
        Branch triggerBranch = buildData.lastBuild.revision.getBranches().iterator().next();
        ObjectId commitId = buildData.lastBuild.revision.getSha1();

        String expandedIntegrationBranch;
        try {
            expandedIntegrationBranch = gitbridge.getExpandedIntegrationBranch(build.getEnvironment(listener));
        } catch (IOException | InterruptedException ex) {
            expandedIntegrationBranch = gitbridge.getIntegrationBranch();
        }

        doTheIntegration((Run)build, listener, gitbridge, commitId, client, expandedIntegrationBranch, triggerBranch);
    }


    @Override
    public void integrateAsGitPluginExt(GitSCM scm, Run<?, ?> build, GitClient client, TaskListener listener, Revision marked, Revision rev, GitBridge gitbridge) throws IntegrationFailedException, IntegrationUnknownFailureException, NothingToDoException, UnsupportedConfigurationException {

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
        Branch triggerBranch = rev.getBranches().iterator().next();

        doTheIntegration((Run)build, listener, gitbridge, rev.getSha1(), client, expandedIntegrationBranch, triggerBranch);
    }

    /**
     * Descriptor implementation for SquashCommitStrategy
     */
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
