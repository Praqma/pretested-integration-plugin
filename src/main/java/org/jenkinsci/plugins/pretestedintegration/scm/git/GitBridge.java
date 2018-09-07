package org.jenkinsci.plugins.pretestedintegration.scm.git;

import hudson.*;
import hudson.model.*;
import hudson.plugins.git.Branch;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.impl.RelativeTargetDirectory;
import hudson.scm.SCM;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.lib.ObjectId;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.pretestedintegration.AbstractSCMBridge;
import org.jenkinsci.plugins.pretestedintegration.IntegrationStrategy;
import org.jenkinsci.plugins.pretestedintegration.IntegrationStrategyDescriptor;
import org.jenkinsci.plugins.pretestedintegration.SCMBridgeDescriptor;
import org.jenkinsci.plugins.pretestedintegration.exceptions.*;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * The Git SCM Bridge.
 */
public class GitBridge extends AbstractSCMBridge {
    private static final Logger LOGGER = Logger.getLogger(GitBridge.class.getName());

    /**
     * The name of the integration repository.
     */
    private String repoName;


    /**
     * The integration integrationBranch.
     * This is the integrationBranch into which pretested commits will be merged.
     */
    private String integrationBranch;


    /**
     * FilePath of Git working directory
     */
    private FilePath workingDirectory;

    /**
     * Constructor for GitBridge.
     * DataBound for use in the UI.
     *
     * @param integrationStrategy The selected IntegrationStrategy
     * @param integrationBranch   The Integration Branch name
     * @param repoName            The Integration Repository name
     */
    @DataBoundConstructor
    public GitBridge(IntegrationStrategy integrationStrategy, final String integrationBranch, final String repoName) {
        super(integrationStrategy);
        this.integrationBranch = integrationBranch;
        this.repoName = repoName;
    }

    public static void pushToIntegrationBranchGit(Run<?, ?> run, TaskListener listener, GitClient client, String expandedRepo, String expandedBranch) throws PushFailedException {
        try {
            pushToBranch(listener, client, expandedBranch, expandedRepo, 0);
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Failed to push changes to integration branch. Exception:", ex);
            listener.getLogger().println(GitMessages.LOG_PREFIX + String.format("Failed to push changes to integration branch. Exception %s", ex));
            throw new PushFailedException(String.format("Failed to push changes to integration branch, message was:%n%s", ex));
        }
    }

    public static void pushToBranch(TaskListener listener, GitClient client, String branchToPush, String expandedRepo) throws PushFailedException {
        try {
            LOGGER.log(Level.INFO, "Pushing changes to: " + branchToPush);
            listener.getLogger().println(GitMessages.LOG_PREFIX+ "Pushing changes to integration branch:");
            client.push(expandedRepo, "HEAD:refs/heads/" + branchToPush);
            LOGGER.log(Level.INFO, "Done pushing changes");
            listener.getLogger().println(GitMessages.LOG_PREFIX+ "Done pushing changes");
        } catch (InterruptedException ex) {
            LOGGER.log(Level.SEVERE, "Failed to push changes to: " + branchToPush + ".\nException:", ex);
            listener.getLogger().println(GitMessages.LOG_PREFIX + String.format("Failed to push changes to: " + branchToPush + ".\nException: %s", ex));
            throw new PushFailedException(String.format("Failed to push changes to integration branch, message was:%n%s", ex));
        }
    }

    public static void pushToBranch(TaskListener listener, GitClient client, String sourceLocalBranch, String targetRemoteBranch, String expandedRepo) throws PushFailedException {
        pushToBranch(listener, client, targetRemoteBranch, expandedRepo, 3);
    }

    public static void pushToBranch(TaskListener listener, GitClient client, String targetRemoteBranch, String expandedRepo, int retries) throws PushFailedException {
        try {
            LOGGER.log(Level.INFO, "Pushing changes from HEAD to remote branch: " + targetRemoteBranch);
            listener.getLogger().println(GitMessages.LOG_PREFIX + "Pushing changes to branch:");
            client.push(expandedRepo, "HEAD:refs/heads/" + targetRemoteBranch.replace(expandedRepo + "/", ""));
            LOGGER.log(Level.INFO, "Done pushing changes");
            listener.getLogger().println(GitMessages.LOG_PREFIX + "Done pushing changes");
        } catch (InterruptedException ex) {
            LOGGER.log(Level.SEVERE, "Failed to push changes to: " + targetRemoteBranch + ".\nException:", ex);
            listener.getLogger().println(GitMessages.LOG_PREFIX + String.format("Failed to push changes to: " + targetRemoteBranch + ".\nException: %s", ex));
            throw new PushFailedException(String.format("Failed to push changes to branch, message was:%n%s", ex));
        } catch (GitException gex) {
            final Pattern nonFastForward = Pattern.compile(".*[rejected].*\\(non-fast-forward\\).*", Pattern.DOTALL);
            //Something is wrong on the remote and it's not a fast forward issue...try again
            if (gex.getMessage() != null && !nonFastForward.matcher(gex.getMessage()).matches() && retries > 0) {
                LOGGER.log(Level.WARNING, LOG_PREFIX + "Failed to push...retrying in 5 seconds");
                listener.getLogger().println(LOG_PREFIX + "Failed to push...retrying in 5 seconds");
                try {
                    Thread.sleep(5000); //Wait 5 seconds
                } catch (InterruptedException e) { /* NOOP */ }
                GitBridge.pushToBranch(listener, client, targetRemoteBranch, expandedRepo, --retries);
            } else {
                throw gex;
            }
        }
    }

    public static void deleteBranch(Run<?, ?> run, TaskListener listener, GitClient client, String branchToBeDeleted, String expandedRepo) throws BranchDeletionFailedException, IOException {
        try {
            LOGGER.log(Level.INFO, "Deleting branch:");
            listener.getLogger().println(GitMessages.LOG_PREFIX+ "Deleting branch:");
            client.push(expandedRepo, ":refs/heads" + branchToBeDeleted.replace(expandedRepo, ""));
            listener.getLogger().println("push " + expandedRepo + " :" + branchToBeDeleted);
            LOGGER.log(Level.INFO, "Done deleting branch");
            listener.getLogger().println(GitMessages.LOG_PREFIX+ "Done deleting development branch");
        } catch (InterruptedException ex) {
            LOGGER.log(Level.SEVERE, "Failed to delete branch. Exception:", ex);
            listener.getLogger().println(GitMessages.LOG_PREFIX+ "Failed to delete development branch. Exception:" + ex.getMessage());
            throw new BranchDeletionFailedException(String.format("Failed to delete branch %s with the following error:%n%s", branchToBeDeleted, ex.getMessage()));
        }
    }

    public static void updateBuildDescription(Run<?, ?> run, TaskListener listener, String integrationBranch, String triggeredBranch) {
        if (triggeredBranch != null) {
            String postfixText = "";
            Result result = run.getResult();

            if (result == null || result.isBetterOrEqualTo(getRequiredResult())) {
                postfixText = " -> " + integrationBranch;
            }
            String finalDescription;
            if (!StringUtils.isBlank(run.getDescription())) {
                finalDescription = String.format("%s%n%s",run.getDescription(),triggeredBranch + postfixText);
            } else {
                finalDescription = String.format("%s", triggeredBranch + postfixText);
            }
            try {
                listener.getLogger().println(LOG_PREFIX + "Updating build description");
                run.setDescription(finalDescription);
            } catch (Exception ex) {
                LOGGER.log(Level.FINE, "Failed to update description", ex); /* Dont care */
            }
        }
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public String getIntegrationBranch() {
        return StringUtils.isBlank(this.integrationBranch) ? "master" : this.integrationBranch;
    }

    /**
     * {@inheritDoc }
     */
    public String getExpandedIntegrationBranch(EnvVars environment) {
        String expandedBranch = environment.expand(this.integrationBranch);
        return StringUtils.isBlank(expandedBranch) ? "master" : expandedBranch;
    }

    /**
     * @return the workingDirectory
     */
    public FilePath getWorkingDirectory() {
        return this.workingDirectory;
    }

    /**
     * @param workingDirectory the workingDirectory to set
     */
    public void setWorkingDirectory(FilePath workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    /**
     * @return the repositoryName
     */
    public String getRepoName() {
        return StringUtils.isBlank(repoName) ? "origin" : repoName;
    }

    /**
     * @param repositoryName the repositoryName to set
     */
    @DataBoundSetter
    public void setRepoName(String repositoryName) {
        this.repoName = repositoryName;
    }

    /**
     * @param environment the environement to look for strings
     * @return the repository name expanded using given environment variables.
     */
    public String getExpandedRepository(EnvVars environment) {
        return environment.expand(getRepoName());
    }

    /**
     * @param triggeredBranch   The triggered branch
     * @param integrationBranch The integration branch
     * @param repoName          The repo name like 'origin'
     * @throws AbortException The triggered branch and the integration is the same - not allowed
     */
    public void evalBranchConfigurations(Branch triggeredBranch, String integrationBranch, String repoName)
            throws AbortException {
        // The purpose of this section of code is to disallow usage of the master or integration branch as the polling branch.
        // TODO: This branch check should be moved to job configuration check method.
        // NOTE: It is important to keep this check at runtime as it could that the 'assumptions' of branch extractions
        //       from sha1 (first branch) could result in the integration branch.
        if (integrationBranch.equals(triggeredBranch.getName()) ||
                integrationBranch.equals(repoName + "/" + triggeredBranch.getName())) {
            String msg = "Using the integration branch for polling and development is not "
                    + "allowed since it will attempt to merge it to other branches and delete it after. Failing build.";
            throw new AbortException(msg);
        }
    }

    public void handleIntegrationExceptionsGit(Run run, TaskListener listener, Exception e, GitClient client) throws IOException, InterruptedException {
        if (e instanceof NothingToDoException) {
            run.setResult(Result.NOT_BUILT);
            String logMessage = LOG_PREFIX + String.format("%s - setUp() - NothingToDoException - %s", LOG_PREFIX, e.getMessage());
            listener.getLogger().println(logMessage);
            LOGGER.log(Level.SEVERE, logMessage, e);
            throw new AbortException(e.getMessage());
        }
        if (e instanceof IntegrationFailedException) {
            String logMessage = String.format(
                    "%s - setUp() - %s%n%s",
                    LOG_PREFIX,
                    e.getClass().getSimpleName(),
                    e.getMessage()
            );
            run.setResult(Result.FAILURE);
            listener.getLogger().println(logMessage);
            LOGGER.log(Level.SEVERE, logMessage, e);
            throw new AbortException(e.getMessage());
        }
        if (e instanceof UnsupportedConfigurationException ||
                e instanceof IntegrationUnknownFailureException ||
                e instanceof EstablishingWorkspaceFailedException) {
            run.setResult(Result.FAILURE);
            String logMessage = String.format("%s - Unforeseen error preparing preparing for integration. %n%s", LOG_PREFIX, e.getMessage());
            LOGGER.log(Level.SEVERE, logMessage, e);
            listener.getLogger().println(logMessage);
            e.printStackTrace(listener.getLogger());
            throw new AbortException(e.getMessage());
        }

        // Any other exceptions (expected: IOException | InterruptedException)
        run.setResult(Result.FAILURE);
        String logMessage = String.format("%s - Unexpected error. %n%s", LOG_PREFIX, e.getMessage());
        LOGGER.log(Level.SEVERE, logMessage, e);
        listener.getLogger().println(logMessage);
        e.printStackTrace(listener.getLogger());
        throw new AbortException(e.getMessage());
    }

    /**
     * Descriptor implementation for GitBridge
     */
    @Extension
    public static final class DescriptorImpl extends SCMBridgeDescriptor<GitBridge> {

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
            return "Git";
        }

        /**
         * @return Descriptors of the Integration Strategies
         */
        public List<IntegrationStrategyDescriptor<?>> getIntegrationStrategies() {
            List<IntegrationStrategyDescriptor<?>> list = new ArrayList<>();
            for (IntegrationStrategyDescriptor<?> descr : IntegrationStrategy.all()) {
                if (descr.isApplicable(this.clazz)) {
                    list.add(descr);
                }
            }
            return list;
        }

        /**
         * @return The default Integration Strategy
         */
        public IntegrationStrategy getDefaultStrategy() {
            return new AccumulatedCommitStrategy();
        }
    }
}
