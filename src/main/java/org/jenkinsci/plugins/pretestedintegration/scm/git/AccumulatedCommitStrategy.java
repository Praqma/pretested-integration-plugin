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
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.MergeCommand;
import org.jenkinsci.plugins.pretestedintegration.AbstractSCMBridge;
import org.jenkinsci.plugins.pretestedintegration.exceptions.IntegrationFailedException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.NothingToDoException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.UnsupportedConfigurationException;
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
    public void integrate(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, AbstractSCMBridge bridge) throws IntegrationFailedException, NothingToDoException, UnsupportedConfigurationException {

 //       Run r = (Run)build;
        GitBridge gitbridge = (GitBridge) bridge;
        GitClient client;
        try {
            client = gitbridge.findScm(build, listener).createClient(listener, build.getEnvironment(listener), build, build.getWorkspace());
        } catch (InterruptedException | IOException ex) {
            LOGGER.log(Level.SEVERE, "Failed to initialize GitClient", ex);
            throw new IntegrationFailedException(ex);
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
        // (JENKINS-24909). Check branch spec before doing anything
        Branch builtBranch = buildData.lastBuild.revision.getBranches().iterator().next();
        String builtSha = buildData.lastBuild.revision.getSha1String();
        String expandedIntegrationBranch;
        try {
            expandedIntegrationBranch = gitbridge.getExpandedBranch(build.getEnvironment(listener));
        } catch (IOException | InterruptedException ex) {
            expandedIntegrationBranch = gitbridge.getBranch();
        }

        ObjectId commitId = buildData.lastBuild.revision.getSha1();
        if (tryFastForward(commitId, listener.getLogger(), client, expandedIntegrationBranch)) {
            return;
        }

        String logMessage = String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Preparing to merge changes in commit %s on development branch %s to integration branch %s", builtSha, builtBranch.getName(), expandedIntegrationBranch);
        LOGGER.log(Level.INFO, logMessage);
        listener.getLogger().println(logMessage);
        if (!containsRemoteBranch(client, builtBranch)) {
            LOGGER.fine("Found no remote branches.");
            try {
                LOGGER.fine("Setting build description 'Nothing to do':");
                build.setDescription("Noting to do");
            } catch (IOException ex) {
                LOGGER.log(Level.FINE, "Failed to update build description", ex);
            }
            logMessage = GitMessages.noRelevantSCMchange(builtBranch.getName());
            LOGGER.log(Level.WARNING, logMessage);
            throw new NothingToDoException(logMessage);
        }

        String commitAuthor; //leaving un-assigned, want to fail later if not assigned
        try {
            // FIXME I don't like this call back design.
            // We build the commit message based on a series of commits which aren't guaranteed to match we end up merging.
            // The method that gets all the commits from a branch walks the git tree using JGit.
            // It's complete independent from the following merge.
            // Worst case scenario: The merge commit message is based on different commits than those actually merged.
            logMessage = String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Collecting commit messages on development branch %s", builtBranch.getName());
            LOGGER.log(Level.INFO, logMessage);
            listener.getLogger().println(logMessage);

            String headerLine = String.format("Accumulated commit of the following from branch '%s':%n", builtBranch.getName());
            // Collect commits
            String commits = client.withRepository(new GetAllCommitsFromBranchCallback( builtBranch.getSHA1(), expandedIntegrationBranch));
            logMessage = String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Done collecting commit messages");
            LOGGER.log(Level.INFO, logMessage);
            listener.getLogger().println(logMessage);
            LOGGER.log(Level.INFO, String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Collecting author of last commit on development branch"));

            // Collect author
            listener.getLogger().println(String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Collecting author of last commit on development branch"));
            commitAuthor = client.withRepository(new FindCommitAuthorCallback( builtBranch.getSHA1()));
            logMessage = String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Done colecting last commit author: %s", commitAuthor);
            LOGGER.log(Level.INFO, logMessage);
            listener.getLogger().println(logMessage);

            logMessage = PretestedIntegrationBuildWrapper.LOG_PREFIX + "Starting accumulated merge (no-ff) - without commit:";
            LOGGER.info(logMessage);
            listener.getLogger().println(logMessage);
            String commitMsg = String.format("%s%n%s", headerLine, commits);
            String modifiedCommitMsg = commitMsg.replaceAll("\"", "'");
            client.merge()
                    .setMessage(modifiedCommitMsg)
                    .setCommit(false)
                    .setGitPluginFastForwardMode(MergeCommand.GitPluginFastForwardMode.NO_FF)
                    .setRevisionToMerge(buildData.lastBuild.revision.getSha1())
                    .execute();
            logMessage = PretestedIntegrationBuildWrapper.LOG_PREFIX + "Accumulated merge done";
            LOGGER.info(logMessage);
            listener.getLogger().println(logMessage);
        } catch (IOException | InterruptedException | GitException ex) {
            logMessage = String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Exception while merging. Logging exception msg: %s", ex.getMessage());
            LOGGER.log(Level.SEVERE, logMessage, ex);
            listener.getLogger().println(logMessage);
            throw new IntegrationFailedException(ex);
        }

        LOGGER.log(Level.INFO, String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Merge was successful"));
        listener.getLogger().println(String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Merge was successful"));
        try {
            logMessage = String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Starting to commit accumulated merge changes:");
            LOGGER.info(logMessage);
            listener.getLogger().println(logMessage);
            String message = client.getWorkTree().child(".git/MERGE_MSG").readToString();
            PersonIdent author = getPersonIdent(commitAuthor);
            client.setAuthor(author);
            client.commit(message);
            logMessage = String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Commit of accumulated merge done");
            LOGGER.info(logMessage);
            listener.getLogger().println(logMessage);
        }  catch (IOException | GitException | InterruptedException ex) {
            // If ".git/MERGE_MSG" wasn't found the most likely culrprit is that the merge was an empty
            // one (No changes) for some reason the merge() command does not complain or throw exception when that happens
            if(ex.getMessage().contains("Cannot commit") || ex.getMessage().contains("MERGE_MSG (No such file or directory)")) {
                logMessage = String.format("%sUnable to commit changes. Most likely you are trying to integrate a change that was already integrated. Message was:%n%s", PretestedIntegrationBuildWrapper.LOG_PREFIX, ex.getMessage());
            } else {
                logMessage = String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Exception while committing. Logging exception msg: %s", ex.getMessage());
            }
            LOGGER.log(Level.SEVERE, logMessage, ex);
            listener.getLogger().println(logMessage);
            ex.printStackTrace(listener.getLogger());
            throw new IntegrationFailedException(ex);
        }

        logMessage = String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Commit was successful");
        LOGGER.log(Level.INFO, logMessage);
        listener.getLogger().println(logMessage);
    }

    @Override
    public void integrateAsGitPluginExt(GitSCM scm, Run<?, ?> build, GitClient git, TaskListener listener, Revision marked, Revision rev, GitBridge bridge) throws NothingToDoException, IntegrationFailedException{

// TODO: replace buildData med Branch/rev!!! NOTE
        BuildData buildData = scm.getBuildData(build);

        String expandedRepoName;
        try {
            expandedRepoName = bridge.getExpandedRepository(build.getEnvironment(listener));
        } catch (IOException | InterruptedException ex) {
            expandedRepoName = bridge.getRepoName();
        }

        //TODO: Implement robustness, in which situations does this one contain
        // multiple revisons, when two branches point to the same commit?
        // (JENKINS-24909). Check branch spec before doing anything

        Branch builtBranch = rev.getBranches().iterator().next();

        String expandedIntegrationBranch;
        try {
            expandedIntegrationBranch = bridge.getExpandedBranch(build.getEnvironment(listener));
        } catch (IOException | InterruptedException ex) {
            expandedIntegrationBranch = bridge.getBranch();
        }

// TODO: CLS: Fast-forward not working as intended
        if ( tryFastForward(rev.getSha1(), listener.getLogger() , git, expandedIntegrationBranch )) {
            return;
        }

        GitClient client = git;

        String logMessage = String.format( PretestedIntegrationBuildWrapper.LOG_PREFIX
                        + "Preparing to merge changes in commit %s on development branch %s to integration branch %s",
                builtBranch.getSHA1(),
                builtBranch.getName(),
                expandedIntegrationBranch);
        LOGGER.log(Level.INFO, logMessage);
        listener.getLogger().println(logMessage);
        if (!containsRemoteBranch(git, builtBranch)) {
            LOGGER.fine("Found no remote branches.");
            try {
                LOGGER.fine("Setting build description 'Nothing to do':");
                build.setDescription(String.format("Noting to do"));
            } catch (IOException ex) {
                LOGGER.log(Level.FINE, "Failed to update build description", ex);
            }
            logMessage = GitMessages.noRelevantSCMchange(builtBranch.getName());
            LOGGER.log(Level.WARNING, logMessage);
            throw new NothingToDoException(logMessage);
        }

        String commitAuthor; //leaving un-assigned, want to fail later if not assigned
        try {
            // FIXME I don't like this call back design.
            // We build the commit message based on a series of commits which aren't guaranteed to match we end up merging.
            // The method that gets all the commits from a branch walks the git tree using JGit.
            // It's complete independent from the following merge.
            // Worst case scenario: The merge commit message is based on different commits than those actually merged.
            logMessage = String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Collecting commit messages on development branch %s", builtBranch.getName());
            LOGGER.log(Level.INFO, logMessage);
            listener.getLogger().println(logMessage);

            String headerLine = String.format("Accumulated commit of the following from branch '%s':%n", builtBranch.getName());
            // Collect commits
            String commits = client.withRepository(new GetAllCommitsFromBranchCallback( builtBranch.getSHA1(), expandedIntegrationBranch));
            logMessage = String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Done collecting commit messages");
            LOGGER.log(Level.INFO, logMessage);
            listener.getLogger().println(logMessage);
            LOGGER.log(Level.INFO, String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Collecting author of last commit on development branch"));

            // Collect author
            listener.getLogger().println(String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Collecting author of last commit on development branch"));
            commitAuthor = client.withRepository(new FindCommitAuthorCallback( builtBranch.getSHA1()));
            logMessage = String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Done colecting last commit author: %s", commitAuthor);
            LOGGER.log(Level.INFO, logMessage);
            listener.getLogger().println(logMessage);

            logMessage = PretestedIntegrationBuildWrapper.LOG_PREFIX + "Starting accumulated merge (no-ff) - without commit:";
            LOGGER.info(logMessage);
            listener.getLogger().println(logMessage);
            String commitMsg = String.format("%s%n%s", headerLine, commits);
            String modifiedCommitMsg = commitMsg.replaceAll("\"", "'");
            client.merge()
                    .setMessage(modifiedCommitMsg)
                    .setCommit(false)
                    .setGitPluginFastForwardMode(MergeCommand.GitPluginFastForwardMode.NO_FF)
                    .setRevisionToMerge(rev.getSha1())
                    .execute();
            logMessage = PretestedIntegrationBuildWrapper.LOG_PREFIX + "Accumulated merge done";
            LOGGER.info(logMessage);
            listener.getLogger().println(logMessage);
        } catch (IOException | InterruptedException | GitException ex) {
            logMessage = String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Exception while merging. Logging exception msg: %s", ex.getMessage());
            LOGGER.log(Level.SEVERE, logMessage, ex);
            listener.getLogger().println(logMessage);
            throw new IntegrationFailedException(ex);
        }

        LOGGER.log(Level.INFO, String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Merge was successful"));
        listener.getLogger().println(String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Merge was successful"));
        try {
            logMessage = String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Starting to commit accumulated merge changes:");
            LOGGER.info(logMessage);
            listener.getLogger().println(logMessage);
            String message = client.getWorkTree().child(".git/MERGE_MSG").readToString();
            PersonIdent author = getPersonIdent(commitAuthor);
            client.setAuthor(author);
            client.commit(message);
            logMessage = String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Commit of accumulated merge done");
            LOGGER.info(logMessage);
            listener.getLogger().println(logMessage);
        }  catch (IOException | GitException | InterruptedException ex) {
            // If ".git/MERGE_MSG" wasn't found the most likely culrprit is that the merge was an empty
            // one (No changes) for some reason the merge() command does not complain or throw exception when that happens
            if(ex.getMessage().contains("Cannot commit") || ex.getMessage().contains("MERGE_MSG (No such file or directory)")) {
                logMessage = String.format("%sUnable to commit changes. Most likely you are trying to integrate a change that was already integrated. Message was:%n%s", PretestedIntegrationBuildWrapper.LOG_PREFIX, ex.getMessage());
            } else {
                logMessage = String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Exception while committing. Logging exception msg: %s", ex.getMessage());
            }
            LOGGER.log(Level.SEVERE, logMessage, ex);
            listener.getLogger().println(logMessage);
            ex.printStackTrace(listener.getLogger());
            throw new IntegrationFailedException(ex);
        }

        logMessage = String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Commit was successful");
        LOGGER.log(Level.INFO, logMessage);
        listener.getLogger().println(logMessage);
    }



    /**
     * Descriptor implementation for AccumulatedCommitStrategy
     */
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
