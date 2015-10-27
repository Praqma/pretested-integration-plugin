package org.jenkinsci.plugins.pretestedintegration.scm.git;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.plugins.git.Branch;
import hudson.plugins.git.GitException;
import hudson.plugins.git.util.BuildData;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
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
        GitBridge gitbridge = (GitBridge) bridge;

        if (tryFastForward(build, launcher, listener, gitbridge)) {
            return;
        }

        BuildData buildData = gitbridge.findRelevantBuildData(build, listener);
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

        GitClient client;
        try {
            client = gitbridge.findScm(build, listener).createClient(listener, build.getEnvironment(listener), build, build.getWorkspace());
        } catch (InterruptedException | IOException ex) {
            LOGGER.log(Level.SEVERE, "Failed to initialize GitClient", ex);
            throw new IntegrationFailedException(ex);
        }

        String logMessage = String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Preparing to merge changes in commit %s on development branch %s to integration branch %s", builtSha, builtBranch.getName(), expandedIntegrationBranch);
        LOGGER.log(Level.INFO, logMessage);
        listener.getLogger().println(logMessage);
        if (!containsRemoteBranch(client, builtBranch)) {
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
            String commits = client.withRepository(new GetAllCommitsFromBranchCallback(listener, builtBranch.getSHA1(), expandedIntegrationBranch));
            logMessage = String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Done collecting commit messages");
            LOGGER.log(Level.INFO, logMessage);
            listener.getLogger().println(logMessage);
            LOGGER.log(Level.INFO, String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Collecting author of last commit on development branch"));

            // Collect author
            listener.getLogger().println(String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Collecting author of last commit on development branch"));
            commitAuthor = client.withRepository(new FindCommitAuthorCallback(listener, builtBranch.getSHA1()));
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
        } catch (IOException | GitException | InterruptedException ex) {
            logMessage = String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Exception while committing. Logging exception msg: %s", ex.getMessage());
            LOGGER.log(Level.SEVERE, logMessage, ex);
            listener.getLogger().println(logMessage);
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
