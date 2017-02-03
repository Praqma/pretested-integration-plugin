package org.jenkinsci.plugins.pretestedintegration.scm.git;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.plugins.git.Branch;
import hudson.plugins.git.GitException;
import hudson.plugins.git.util.BuildData;
import java.io.FileNotFoundException;
import org.jenkinsci.plugins.pretestedintegration.*;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.jgit.lib.PersonIdent;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.pretestedintegration.exceptions.IntegrationFailedException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.NothingToDoException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.UnsupportedConfigurationException;
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

    /**
     * {@inheritDoc}
     */
    @Override
    public void integrate(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, AbstractSCMBridge bridge) throws IntegrationFailedException, NothingToDoException, UnsupportedConfigurationException {
        GitBridge gitbridge = (GitBridge) bridge;

        if(tryFastForward(build, launcher, listener, gitbridge)) return;
        if(tryRebase(build, launcher, listener, gitbridge)) return;

        GitClient client;
        try {
            client = gitbridge.findScm(build, listener).createClient(listener, build.getEnvironment(listener), build, build.getWorkspace());
        } catch (InterruptedException | IOException ex) {
            LOGGER.log(Level.SEVERE, "Failed to initialize GitClient", ex);
            throw new IntegrationFailedException(ex);
        }

        String expandedBranchName;
        try {
            expandedBranchName = gitbridge.getExpandedBranch(build.getEnvironment(listener));
        } catch (IOException | InterruptedException ex) {
            expandedBranchName = gitbridge.getBranch();
        }

        //TODO: How can you add more than 1 action, MultiSCM plugin with two separate gits?
        BuildData buildData = gitbridge.findRelevantBuildData(build, listener);
        Branch builtBranch = buildData.lastBuild.revision.getBranches().iterator().next();

        String logMessage = String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Preparing to merge changes in commit %s on development branch %s to integration branch %s", builtBranch.getSHA1String(), builtBranch.getName(), expandedBranchName);
        LOGGER.log(Level.INFO, logMessage);
        listener.getLogger().println(logMessage);
        if (!containsRemoteBranch(client, builtBranch)) {
            LOGGER.fine("Found no remote branches.");
            try {
                LOGGER.fine("Setting build description 'Nothing to do':");
                build.setDescription(String.format("Nothing to do"));
                LOGGER.fine("Done setting build description.");
            } catch (IOException ex) {
                LOGGER.log(Level.FINE, "Failed to update build description", ex);
            }
            logMessage = GitMessages.noRelevantSCMchange(builtBranch.getName());
            LOGGER.log(Level.WARNING, logMessage);
            throw new NothingToDoException(logMessage);
        }

        String commitAuthor = null; //leaving un-assigned, want to fail later if not assigned
        try {
            // Collect author
            logMessage = String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Collecting author of last commit on development branch");
            LOGGER.log(Level.INFO, logMessage);
            listener.getLogger().println(logMessage);
            commitAuthor = client.withRepository(new FindCommitAuthorCallback(listener, builtBranch.getSHA1()));
            logMessage = String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Done collecting last commit author: %s", commitAuthor);
            LOGGER.log(Level.INFO, logMessage);
            listener.getLogger().println(logMessage);

            logMessage = String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Starting squash merge - without commit:");
            LOGGER.info(logMessage);
            listener.getLogger().println(logMessage);
            listener.getLogger().println(String.format("%s merge --squash %s", PretestedIntegrationBuildWrapper.LOG_PREFIX, builtBranch.getName())); // Output asserted in tests.
            client.merge().setSquash(true).setRevisionToMerge(builtBranch.getSHA1()).execute();
            logMessage = String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Squash merge done");
            LOGGER.info(logMessage);
            listener.getLogger().println(logMessage);
        } catch (IOException | InterruptedException | GitException ex) {
            logMessage = String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Exception while merging. Logging exception msg: %s", ex.getMessage());
            LOGGER.log(Level.SEVERE, logMessage, ex);
            listener.getLogger().println(logMessage);
            throw new IntegrationFailedException(ex);
        }

        logMessage = String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Merge was successful");
        LOGGER.log(Level.INFO, logMessage);
        listener.getLogger().println(logMessage);

        try {
            logMessage = String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Starting to commit squash merge changes:");
            LOGGER.info(logMessage);
            listener.getLogger().println(logMessage);
            PersonIdent author = getPersonIdent(commitAuthor);
            String message = client.getWorkTree().child(".git/SQUASH_MSG").readToString();
            client.setAuthor(author);
            client.commit(message);
            logMessage = String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Commit of squashed merge done");
            LOGGER.info(logMessage);
            listener.getLogger().println(logMessage);
        } catch (FileNotFoundException | GitException ex) {
            //See Github issue #29 (https://github.com/Praqma/pretested-integration-plugin/issues/29)
            logMessage = String.format("%sUnable to commit changes. Most likely you are trying to integrate a change that was already integrated. Message was:%n%s", PretestedIntegrationBuildWrapper.LOG_PREFIX, ex.getMessage());
            LOGGER.log(Level.SEVERE, logMessage, ex);
            listener.getLogger().println(logMessage);
            throw new IntegrationFailedException(ex);
        } catch (IOException | InterruptedException ex) {
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
