package org.jenkinsci.plugins.pretestedintegration.scm.git;

import hudson.*;
import hudson.model.*;
import hudson.plugins.git.*;
import hudson.plugins.git.extensions.impl.RelativeTargetDirectory;
import hudson.plugins.git.util.BuildData;
import hudson.scm.SCM;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.RefSpec;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.MergeCommand;
import org.jenkinsci.plugins.multiplescms.MultiSCM;
import org.jenkinsci.plugins.pretestedintegration.AbstractSCMBridge;
import org.jenkinsci.plugins.pretestedintegration.exceptions.*;
import org.jenkinsci.plugins.pretestedintegration.IntegrationStrategy;
import org.jenkinsci.plugins.pretestedintegration.IntegrationStrategyDescriptor;
import org.jenkinsci.plugins.pretestedintegration.PretestedIntegrationBuildWrapper;
import org.jenkinsci.plugins.pretestedintegration.SCMBridgeDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.swing.text.html.HTMLDocument;

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
     * @param integrationStrategy The selected IntegrationStrategy
     * @param integrationBranch The Integration Branch name
     * @param repoName The Integration Repository name
     */
    @DataBoundConstructor
    public GitBridge(IntegrationStrategy integrationStrategy, final String integrationBranch, final String repoName){
        super(integrationStrategy);
        this.integrationBranch = integrationBranch;
        this.repoName = repoName;
    }

    /***
     * Returns the Git SCM for the relevant build data.
     * @param build The Build
     * @param listener The BuildListener
     * @return the Git SCM for the relevant build data.
     * @throws InterruptedException
     * When no matching SCMs are found
     * @throws NothingToDoException
     * When no relevant BuildData is found.
     * @throws UnsupportedConfigurationException
     * When multiple, ambiguous relevant BuildDatas are found.
     */
    protected GitSCM findScm(AbstractBuild<?, ?> build, TaskListener listener) throws InterruptedException, NothingToDoException, UnsupportedConfigurationException, IOException, InterruptedException {

        SCM scm = build.getProject().getScm();
        if (scm instanceof GitSCM) {
            GitSCM gitScm = (GitSCM) scm;
            LOGGER.fine(String.format("Found GitSCM"));
            return gitScm;
        }

        if (Jenkins.getInstance().getPlugin("multiple-scms") == null) {
            throw new InterruptedException("The selected SCM isn't Git and the MultiSCM plugin was not found.");
        }

        if (!(scm instanceof MultiSCM)) {
            throw new InterruptedException("The selected SCM is neither Git nor MultiSCM.");
        }

        MultiSCM multiScm = (MultiSCM) scm;
        LOGGER.fine(String.format("Found MultiSCM"));
        for (SCM subScm : multiScm.getConfiguredSCMs()) {
            if (subScm instanceof GitSCM) {
                LOGGER.fine(String.format("Detected Git under MultiSCM"));
                GitSCM gitscm = (GitSCM) subScm;

                // ASSUMPTION:
                // There's only one Git SCM that matches the integration branch in our build data.
                // Returning the first match should be fine, as the origin name is part of the integrationBranch name
                // and we require all MultiSCM Git configurations to be explicitly and uniquely named.
                BuildData buildData = PretestedIntegrationGitUtils.findRelevantBuildData(build, listener.getLogger(), getExpandedRepository(build.getEnvironment(listener)));
                Revision revision = gitscm.getBuildData(build).lastBuild.revision;
                for (Branch bdBranch : buildData.lastBuild.revision.getBranches()) { // More than one if several integrationBranch heads are in the same commit
                    if (revision.containsBranchName(bdBranch.getName())) {
                        LOGGER.fine(String.format("Git SCM matches relevant integration branch."));
                        return gitscm;
                    } else {
                        LOGGER.fine(String.format("Git SCM doesn't match relevant branch."));
                    }
                }
            }
        }
        throw new InterruptedException("No Git repository configured in MultiSCM that matches the build data branch.");
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public void ensureBranch(AbstractBuild<?, ?> build,  Launcher launcher , BuildListener listener, String branch) throws EstablishingWorkspaceFailedException {
        try {
            EnvVars environment = build.getEnvironment(listener);
            String expandedBranch = getExpandedIntegrationBranch(environment);
            String expandedRepo = getExpandedRepository(environment);
            GitClient client = findScm(build, listener).createClient(listener, environment, build, build.getWorkspace());
            listener.getLogger().println(String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Checking out integration branch %s:", expandedBranch));
            client.checkout().branch(expandedBranch).ref(expandedRepo + "/" + expandedBranch).deleteBranchIfExist(true).execute();
        } catch (IOException | InterruptedException ex) {
            LOGGER.log(Level.SEVERE, "ensureBranch", ex);
            throw new EstablishingWorkspaceFailedException(ex);
        }
    }

    /**
     * Pulls in the remote branch
     * @param build The Build
     * @param listener The Listener
     * @throws IOException An unforeseen IO issue
     * @throws InterruptedException An foreseen issue
     */
    protected void update(AbstractBuild<?, ?> build, BuildListener listener) throws IOException, InterruptedException {
        GitClient client;
        ObjectId sha1;
        try {
            EnvVars environment = build.getEnvironment(listener);
            String expandedRepo = getExpandedRepository(environment);
            String expandedBranch = getExpandedIntegrationBranch(environment);
            client = findScm(build, listener).createClient(listener, build.getEnvironment(listener), build, build.getWorkspace());
            sha1 = client.revParse(expandedRepo + "/" + expandedBranch);
            client.merge().setRevisionToMerge(sha1);
        } catch (InterruptedException | IOException ex) {
            throw new EstablishingWorkspaceFailedException(ex);
        }
        try {
            client.merge().execute();
        } catch ( GitException ex ){
            throw new IntegrationFailedException(ex);
        }

    }

    /**
     * {@inheritDoc }
     */
    @Override
    public void pushToIntegrationBranch(AbstractBuild<?, ?> build, BuildListener listener) throws PushFailedException {
        try {
            EnvVars environment = build.getEnvironment(listener);
            String expandedRepo = getExpandedRepository(environment);
            String expandedBranch = getExpandedIntegrationBranch(environment);

            GitSCM gitSCM = findScm(build, listener);
            GitClient client = gitSCM.createClient(listener, build.getEnvironment(listener), build, build.getWorkspace());
            pushToBranch(listener, client, expandedBranch, expandedRepo);
        } catch (IOException | InterruptedException ex) {
            LOGGER.log(Level.SEVERE, "Failed to push changes to integration branch. Exception:", ex);
            listener.getLogger().println(PretestedIntegrationBuildWrapper.LOG_PREFIX + String.format("Failed to push changes to integration branch. Exception %s", ex));
            throw new PushFailedException(String.format("Failed to push changes to integration branch, message was:%n%s", ex));
        }
    }
    public static void pushToIntegrationBranchGit(Run<?, ?> run, TaskListener listener, GitClient client, String expandedRepo, String expandedBranch) throws PushFailedException {
        try {
            pushToBranch(listener, client, expandedBranch, expandedRepo);
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Failed to push changes to integration branch. Exception:", ex);
            listener.getLogger().println(PretestedIntegrationBuildWrapper.LOG_PREFIX + String.format("Failed to push changes to integration branch. Exception %s", ex));
            throw new PushFailedException(String.format("Failed to push changes to integration branch, message was:%n%s", ex));
        }
    }

    public static void pushToBranch( TaskListener listener, GitClient client, String branchToPush, String expandedRepo  ) throws PushFailedException {
        try {
            LOGGER.log(Level.INFO, "Pushing changes to: " + branchToPush );
            listener.getLogger().println(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Pushing changes to integration branch:");
            client.push(expandedRepo, "HEAD:refs/heads/" + branchToPush);
            LOGGER.log(Level.INFO, "Done pushing changes");
            listener.getLogger().println(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Done pushing changes");
        } catch ( InterruptedException ex) {
            LOGGER.log(Level.SEVERE, "Failed to push changes to: " + branchToPush +".\nException:", ex);
            listener.getLogger().println(PretestedIntegrationBuildWrapper.LOG_PREFIX + String.format("Failed to push changes to: " + branchToPush + ".\nException: %s", ex));
            throw new PushFailedException(String.format("Failed to push changes to integration branch, message was:%n%s", ex));
        }
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public void isApplicable(AbstractBuild<?, ?> build, BuildListener listener) throws NothingToDoException, UnsupportedConfigurationException, IOException, InterruptedException {
        PretestedIntegrationGitUtils.findRelevantBuildData(build, listener.getLogger(), getExpandedRepository(build.getEnvironment(listener)));
    }
    /**
     * {@inheritDoc }
     */
    @Override
    public void deleteIntegratedBranch(AbstractBuild<?, ?> build, TaskListener listener) throws BranchDeletionFailedException, NothingToDoException, UnsupportedConfigurationException, IOException {
        String triggeredBranch = build.getAction(PretestTriggerCommitAction.class).triggerBranch.getName();

        try {
            String expandedRepo = getExpandedRepository(build.getEnvironment(listener));
            GitClient client = findScm(build, listener).createClient(listener, build.getEnvironment(listener), build, build.getWorkspace());
            deleteBranch(build, listener, client, triggeredBranch , expandedRepo);

        } catch (InterruptedException | IOException ex) {
            throw new BranchDeletionFailedException(String.format("Failed to delete development branch %s with the following error:%n%s", triggeredBranch, ex.getMessage()));
        }
    }

    public static void deleteBranch(Run<?, ?> run, TaskListener listener, GitClient client, String branchToBeDeleted, String expandedRepo) throws BranchDeletionFailedException, IOException {
        try {
            LOGGER.log(Level.INFO, "Deleting branch:");
            listener.getLogger().println(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Deleting branch:");
            client.push(expandedRepo, ":refs/heads" + branchToBeDeleted.replace(expandedRepo,""));
            listener.getLogger().println("push " + expandedRepo + " :" + branchToBeDeleted);
            LOGGER.log(Level.INFO, "Done deleting branch");
            listener.getLogger().println(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Done deleting development branch");
        } catch (InterruptedException ex) {
            LOGGER.log(Level.SEVERE, "Failed to delete branch. Exception:", ex);
            listener.getLogger().println(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Failed to delete development branch. Exception:" + ex.getMessage());
            throw new BranchDeletionFailedException(String.format("Failed to delete branch %s with the following error:%n%s", branchToBeDeleted, ex.getMessage()));
        }
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public void updateBuildDescription(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws NothingToDoException, UnsupportedConfigurationException, IOException, InterruptedException {
        Branch triggerBranch = build.getAction(PretestTriggerCommitAction.class).triggerBranch;
        if (triggerBranch != null) {
            String postfixText = "";
            Result result = build.getResult();
            if ( result != null && result.isBetterOrEqualTo(getRequiredResult())) {
                postfixText = " -> " + integrationBranch;
            }
            String finalDescription;
            if (!StringUtils.isBlank(build.getDescription())) {
                finalDescription = String.format("%s%n%s",
                        build.getDescription(),
                        triggerBranch.getName() + postfixText );
            } else {
                finalDescription = String.format("%s", triggerBranch.getName() + postfixText);
            }
            try {
                build.setDescription(finalDescription);
            } catch (Exception ex) {
                LOGGER.log(Level.FINE, "Failed to update description", ex); /* Dont care */ }
        }
    }
    public static void updateBuildDescription(Run<?, ?> run, TaskListener listener, String integrationBranch, String triggeredBranch) {
        if (triggeredBranch != null) {
            String postfixText = "";
            Result result = run.getResult();
            if ( result == null || result.isBetterOrEqualTo(getRequiredResult())) {
                postfixText = " -> " + integrationBranch;
            }
            String finalDescription;
            if (!StringUtils.isBlank(run.getDescription())) {
                finalDescription = String.format("%s%n%s",
                        run.getDescription(),
                        triggeredBranch + postfixText );
            } else {
                finalDescription = String.format("%s", triggeredBranch + postfixText);
            }
            try {
                run.setDescription(finalDescription);
            } catch (Exception ex) {
                LOGGER.log(Level.FINE, "Failed to update description", ex); /* Dont care */ }
        }
    }

    /**
     * Returns the workspace
     * @param build The Build
     * @param listener The Listener
     * @return a FilePath representing the workspace
     * @throws InterruptedException Unforeseen issue
     * @throws IOException Unforeseen IO issue
     */
    public FilePath resolveWorkspace(AbstractBuild<?, ?> build, BuildListener listener) throws InterruptedException, IOException {
        FilePath workspace = build.getWorkspace();
        GitSCM scm = findScm(build, listener);
        RelativeTargetDirectory dir = scm.getExtensions().get(RelativeTargetDirectory.class);

        if (dir != null) {
            workspace = dir.getWorkingDirectory(scm, build.getProject(), workspace, build.getEnvironment(listener), listener);
        }

        LOGGER.log(Level.FINE, "Resolved workspace to {0}", workspace);
        return workspace;
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public void validateConfiguration(AbstractProject<?, ?> project) throws UnsupportedConfigurationException {
        if (project.getScm() instanceof GitSCM) {
            /* We don't need to verify when we're using Git SCM,
             * since we will never have ambiguity in remote names
             * because the plugin renames them if they clash.
             */
            PretestedIntegrationAsGitPluginExt pretestedGitPluginExt = ((GitSCM)project.getScm()).getExtensions().get(PretestedIntegrationAsGitPluginExt.class) ;
            if ( pretestedGitPluginExt != null ) {
                 throw new UnsupportedConfigurationException(
                         "You have configurated the Pretested Integration plugin as Git Plugin Extension already.\n" +
                         "It does not make sense to have both. Please remove it in the Build Environment section"
                 );
            }
            return;
        }

        if (Jenkins.getInstance().getPlugin("multiple-scms") != null && project.getScm() instanceof MultiSCM) {
            MultiSCM multiscm = (MultiSCM) project.getScm();
            validateMultiScm(multiscm.getConfiguredSCMs());
        } else {
            throw new UnsupportedConfigurationException("We only support 'Git' and 'Multiple SCMs' plugins");
        }
    }

    /**
     * Validate the Git configurations in MultiSCM.
     * JENKINS-24754
     * @param scms The list of the configured SCMs
     * @return boolean indicating if the MultiSCM is ok
     * @throws UnsupportedConfigurationException Mismatch combination in job configuration
     */
    public boolean validateMultiScm(List<SCM> scms) throws UnsupportedConfigurationException {
        Set<String> remoteNames = new HashSet<>();
        for (SCM scm : scms) {
            if (scm instanceof GitSCM) {
                List<UserRemoteConfig> configs = ((GitSCM) scm).getUserRemoteConfigs();
                for (UserRemoteConfig config : configs) {
                    if (StringUtils.isBlank(config.getName())) {
                        throw new UnsupportedConfigurationException(UnsupportedConfigurationException.MULTISCM_REQUIRE_EXPLICIT_NAMING);
                    }
                    if (!remoteNames.add(config.getName())) {
                        throw new UnsupportedConfigurationException(UnsupportedConfigurationException.AMBIGUITY_IN_REMOTE_NAMES);
                    }
                }
            }
        }
        return true;
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public void handlePostBuild(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IOException {

        Result result = build.getResult();
        if (result != null && result.isBetterOrEqualTo(getRequiredResult())) {
            pushToIntegrationBranch(build, listener);
            deleteIntegratedBranch(build, listener);
        } else {
            LOGGER.log(Level.WARNING, "Build result not satisfied - skipped post-build step.");
            listener.getLogger().println(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Build result not satisfied - skipped post-build step.");
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
     * @param workingDirectory the workingDirectory to set
     */
    public void setWorkingDirectory(FilePath workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    /**
     * @return the workingDirectory
     */
    public FilePath getWorkingDirectory() {
        return this.workingDirectory;
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
     * @param triggeredBranch The triggered branch
     * @param integrationBranch  The integration branch
     * @param repoName The repo name like 'origin'
     * @throws AbortException The triggered branch and the integration is the same - not allowed
     */
    public void evalBranchConfigurations (Branch triggeredBranch, String integrationBranch, String repoName )
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

    public void handleIntegrationExceptionsGit(Run run,  TaskListener listener, Exception e, GitClient client) throws IOException, InterruptedException {
        if ( e instanceof NothingToDoException ) {
            run.setResult(Result.NOT_BUILT);
            String logMessage = LOG_PREFIX + String.format("%s - setUp() - NothingToDoException - %s", LOG_PREFIX, e.getMessage());
            listener.getLogger().println(logMessage);
            LOGGER.log(Level.SEVERE, logMessage, e);
            throw new AbortException(e.getMessage());
        }
        if ( e instanceof IntegrationFailedException  ) {
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
        if ( e instanceof UnsupportedConfigurationException ||
                e instanceof IntegrationUnknownFailureException ||
                e instanceof EstablishingWorkspaceFailedException ) {
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
            return new SquashCommitStrategy();
        }
    }
}
