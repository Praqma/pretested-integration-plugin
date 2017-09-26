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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.MergeCommand;
import org.jenkinsci.plugins.pretestedintegration.AbstractSCMBridge;
import org.jenkinsci.plugins.pretestedintegration.exceptions.*;
import org.jenkinsci.plugins.pretestedintegration.IntegrationStrategyDescriptor;
import org.jenkinsci.plugins.pretestedintegration.PretestedIntegrationBuildWrapper;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Integration strategy for merging multiple commits.
 * Merges in all the commits without squashing them.
 * Provides a custom merge commit message.
 */
public class AccumulatedCommitStrategy extends GitIntegrationStrategy {

    private static final Logger LOGGER = Logger.getLogger(AccumulatedCommitStrategy.class.getName());

    /**
     * Strategy name. Used in UI.
     * Strategies used to be called Behaviors, hence the field name.
     */
    private static final String B_NAME = "Accumulated commit";

    private void doTheIntegration (Run build, TaskListener listener, GitBridge gitbridge, ObjectId commitId, GitClient client, String expandedIntegrationBranch, Branch triggerBranch) throws IntegrationFailedException, NothingToDoException, UnsupportedConfigurationException, IntegrationUnknownFailureException {
        //Get the commit count
        int commitCount;
        try {
            commitCount = PretestedIntegrationGitUtils.countCommits(commitId, client, expandedIntegrationBranch);
            String text = "Branch commit count: " + commitCount;
            LOGGER.log(Level.INFO, PretestedIntegrationBuildWrapper.LOG_PREFIX + text);
            listener.getLogger().println(PretestedIntegrationBuildWrapper.LOG_PREFIX + text);
        } catch (IOException | InterruptedException ex) {
            throw new IntegrationFailedException("Failed to count commits.", ex);
        }

        if ( tryFastForward(commitId, listener.getLogger() , client, commitCount) ) {
            return;
        }

        String logMessage = String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Preparing to merge changes in commit %s on development branch %s to integration branch %s", commitId.getName(), triggerBranch.getName(), expandedIntegrationBranch);
        LOGGER.log(Level.INFO, logMessage);
        listener.getLogger().println(logMessage);
        if (!containsRemoteBranch(client, triggerBranch)) {
            LOGGER.fine("Found no remote branches.");
            try {
                LOGGER.fine("Setting build description 'Nothing to do':");
                build.setDescription(String.format("Nothing to do"));
            } catch (IOException ex) {
                LOGGER.log(Level.FINE, "Failed to update build description", ex);
            }
            logMessage = GitMessages.noRelevantSCMchange(triggerBranch.getName());
            LOGGER.log(Level.WARNING, logMessage);
            throw new NothingToDoException(logMessage);
        }

        String commitAuthor; //leaving un-assigned, want to fail later if not assigned
        try {
            // FIXME I don't like this call back design.
            // We build the commit message based on a series of commits which aren't guaranteed to match we end up merging.
            // The method that gets all the commits from a integrationBranch walks the git tree using JGit.
            // It's complete independent from the following merge.
            // Worst case scenario: The merge commit message is based on different commits than those actually merged.
            logMessage = String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Collecting commit messages on development branch %s", triggerBranch.getName());
            LOGGER.log(Level.INFO, logMessage);
            listener.getLogger().println(logMessage);

            String headerLine = String.format("Accumulated commit of the following from branch '%s':%n", triggerBranch.getName());
            // Collect commits
            String commits = client.withRepository(new GetAllCommitsFromBranchCallback( triggerBranch.getSHA1(), expandedIntegrationBranch));
            logMessage = String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Done collecting commit messages");
            LOGGER.log(Level.INFO, logMessage);
            listener.getLogger().println(logMessage);
            LOGGER.log(Level.INFO, String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Collecting author of last commit on development branch"));

            // Collect author
            listener.getLogger().println(String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Collecting author of last commit on development branch"));
            commitAuthor = client.withRepository(new FindCommitAuthorCallback(triggerBranch.getSHA1()));
            logMessage = String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Done collecting last commit author: %s", commitAuthor);
            LOGGER.log(Level.INFO, logMessage);
            listener.getLogger().println(logMessage);

            logMessage = PretestedIntegrationBuildWrapper.LOG_PREFIX + "Starting accumulated merge (no-ff) - without commit:";
            LOGGER.info(logMessage);
            listener.getLogger().println(logMessage);
            String commitMsg = String.format("%s%n%s", headerLine, commits);
            String modifiedCommitMsg = commitMsg.replaceAll("\"", "'");
            try {
                client.merge()
                        .setMessage(modifiedCommitMsg)
                        .setCommit(false)
                        .setGitPluginFastForwardMode(MergeCommand.GitPluginFastForwardMode.NO_FF)
                        .setRevisionToMerge(commitId).execute();
            } catch ( GitException | InterruptedException ex ){
                logMessage = String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Exception while merging. Logging exception msg: %s", ex.getMessage());
                LOGGER.log(Level.SEVERE, logMessage, ex);
                listener.getLogger().println(logMessage);
                throw new IntegrationFailedException(ex);
            }
        } catch ( IOException | InterruptedException ex) {
            if ( ex instanceof IntegrationFailedException ) {
                throw new IntegrationFailedException(ex);
            } else {
                logMessage = String.format(
                        PretestedIntegrationBuildWrapper.LOG_PREFIX + "Exception while setting up merging. Logging exception msg: %s",
                        ex.getMessage());
                LOGGER.log(Level.SEVERE, logMessage, ex);
                listener.getLogger().println(logMessage);
                throw new IntegrationUnknownFailureException(ex);
            }
        }

        LOGGER.log(Level.INFO, String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Merge was successful"));
        listener.getLogger().println(String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Merge was successful"));
        String message = "";
        try {
            logMessage = String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Starting to commit accumulated merge changes:");
            LOGGER.info(logMessage);
            listener.getLogger().println(logMessage);
            message = client.getWorkTree().child(".git/MERGE_MSG").readToString();
            PersonIdent author = getPersonIdent(commitAuthor);
            client.setAuthor(author);
            client.commit(message);
            logMessage = String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Commit of accumulated merge done");
            LOGGER.info(logMessage);
            listener.getLogger().println(logMessage);
        } catch (IOException | GitException | InterruptedException ex) {
            // A) Git commit failed for some wierd reason
            // b) If ".git/MERGE_MSG" wasn't found the most likely culrprit is that the merge was an empty
            //    one (No changes) for some reason the merge() command does not complain or throw exception when that happens
            if (    ex.getMessage().contains("Cannot commit") ||
                    ex.getMessage().contains("MERGE_MSG (No such file or directory)") ||
                    ex.getMessage().contains("MERGE_MSG (The system cannot find the file specified)") ) {
                logMessage = String.format("%sUnable to commit changes. There are two known reasons:%n" +
                        "A) You are trying to integrate a change that was already integrated.%n" +
                        "B) You have pushed an empty commit( presumably used --allow-empty ) that needed a merge. %n" +
                        "   If you REALLY want the empty commit to be accepted, you can rebase your empty commit(s) on top %n" +
                        "   of the integration branch and it will be fast-forwarded. %n" +
                        "Message was:%n%s", PretestedIntegrationBuildWrapper.LOG_PREFIX, ex.getMessage());
                // TODO: SHould we throw a "Nothing to do" exception?
            } else {
                logMessage = String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Exception while committing. Logging exception msg: %s", ex.getMessage());
            }
            LOGGER.log(Level.SEVERE, logMessage, ex);
            listener.getLogger().println(logMessage);
            ex.printStackTrace(listener.getLogger());
            throw new IntegrationUnknownFailureException(ex);
        }
        logMessage = String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Commit was successful");
        LOGGER.log(Level.INFO, logMessage);
        listener.getLogger().println(logMessage);
    }

    /**
     * Constructor for AccumulatedCommitStrategy.
     * DataBound to work in UI.
     */
    @DataBoundConstructor
    public AccumulatedCommitStrategy() {
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
        // (JENKINS-24909). Check branch spec before doing anything ( consider taking the last rather than the first )
        Branch triggerBranch = buildData.lastBuild.revision.getBranches().iterator().next();
        ObjectId commitId = buildData.lastBuild.revision.getSha1();
        String expandedIntegrationBranch;
        try {
            expandedIntegrationBranch = gitbridge.getExpandedIntegrationBranch(build.getEnvironment(listener));
        } catch (IOException | InterruptedException ex) {
            expandedIntegrationBranch = gitbridge.getIntegrationBranch();
        }

        build.addAction(new PretestTriggerCommitAction(triggerBranch));
        doTheIntegration((Run)build, listener, gitbridge, commitId, client, expandedIntegrationBranch, triggerBranch);
    }

    @Override
    public void integrateAsGitPluginExt(GitSCM scm, Run<?, ?> build, GitClient git, TaskListener listener, Revision marked, Revision rev, GitBridge gitbridge) throws NothingToDoException, IntegrationFailedException, IOException, InterruptedException {
        Branch triggerBranch = rev.getBranches().iterator().next();
        String expandedIntegrationBranch = gitbridge.getExpandedIntegrationBranch(build.getEnvironment(listener));
        doTheIntegration((Run) build, listener, gitbridge, triggerBranch.getSHA1(), git, expandedIntegrationBranch, triggerBranch);
    }

    /**
     * Descriptor implementation for AccumulatedCommitStrategy
     */
    @Symbol("accumulated")
    @Extension
    public static final class DescriptorImpl extends IntegrationStrategyDescriptor<AccumulatedCommitStrategy> {

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
