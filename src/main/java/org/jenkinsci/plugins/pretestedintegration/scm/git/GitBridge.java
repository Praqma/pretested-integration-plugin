package org.jenkinsci.plugins.pretestedintegration.scm.git;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.plugins.git.*;
import hudson.plugins.git.extensions.impl.RelativeTargetDirectory;
import hudson.plugins.git.util.BuildData;
import hudson.scm.SCM;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Set;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.RefSpec;
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
     * @param allowedNoCommits The Integration Repository name
     */
    @DataBoundConstructor
    public GitBridge(IntegrationStrategy integrationStrategy, final String integrationBranch, final String repoName, final boolean integrationFailedStatusUnstable, final Integer allowedNoCommits) {
        super(integrationStrategy, integrationFailedStatusUnstable);
        this.integrationBranch = integrationBranch;
        this.repoName = repoName;
        this.allowedNoCommits = allowedNoCommits;
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
     * @throws IOException
     * @throws InterruptedException
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
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            EnvVars environment = build.getEnvironment(listener);
            String expandedRepo = getExpandedRepository(environment);
            String expandedBranch = getExpandedIntegrationBranch(environment);

            GitSCM gitSCM = findScm(build, listener);
            GitClient client = gitSCM.createClient(listener, build.getEnvironment(listener), build, build.getWorkspace());
            LOGGER.log(Level.INFO, "Pushing changes to integration branch:");
            listener.getLogger().println(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Pushing changes to integration branch:");
            client.push(expandedRepo, "refs/heads/" + expandedBranch);
            LOGGER.log(Level.INFO, "Done pushing changes");
            listener.getLogger().println(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Done pushing changes");
        } catch (IOException | InterruptedException ex) {
            LOGGER.log(Level.SEVERE, "Failed to push changes to integration branch. Exception:", ex);
            listener.getLogger().println(PretestedIntegrationBuildWrapper.LOG_PREFIX + String.format("Failed to push changes to integration branch. Exception %s", ex));
            throw new PushFailedException(String.format("Failed to push changes to integration branch, message was:%n%s", output.toString()));
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
    public void deleteIntegratedBranch(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws BranchDeletionFailedException, NothingToDoException, UnsupportedConfigurationException, IOException {
            String triggerBranch = build.getAction(PretestTriggerCommitAction.class).triggerBranch.getName();
            try {
                LOGGER.log(Level.INFO, "Deleting development branch:");
                String expandedRepo = getExpandedRepository(build.getEnvironment(listener));
                listener.getLogger().println(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Deleting development branch:");
                GitClient client = findScm(build, listener).createClient(listener, build.getEnvironment(listener), build, build.getWorkspace());
                client.push(expandedRepo, ":" + removeRepository(triggerBranch));
                listener.getLogger().println("push " + expandedRepo + " :" + removeRepository(triggerBranch));
                LOGGER.log(Level.INFO, "Done deleting development branch");
                listener.getLogger().println(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Done deleting development branch");
            } catch (InterruptedException | IOException ex) {
                LOGGER.log(Level.SEVERE, "Failed to delete development branch. Exception:", ex);
                listener.getLogger().println(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Failed to delete development branch. Exception:" + ex.getMessage());
                throw new BranchDeletionFailedException(String.format("Failed to delete development branch %s with the following error:%n%s", triggerBranch, ex.getMessage()));
            }
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public void updateBuildDescription(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws NothingToDoException, UnsupportedConfigurationException, IOException, InterruptedException {
        BuildData gitBuildData = PretestedIntegrationGitUtils.findRelevantBuildData(build, listener.getLogger(), getExpandedRepository(build.getEnvironment(listener)));

        if (gitBuildData != null) {
            Branch gitDataBranch = gitBuildData.lastBuild.revision.getBranches().iterator().next();
            String text;
            if (!StringUtils.isBlank(build.getDescription())) {
                text = String.format("%s\n%s", build.getDescription(),
                        "(" + getRequiredResult() + "):" + gitDataBranch.getName() + " -> " + integrationBranch);
            } else {
                text = String.format("%s", gitDataBranch.getName() + " -> " + integrationBranch);
            }
            try {
                build.setDescription(text);
            } catch (Exception ex) {
                LOGGER.log(Level.FINE, "Failed to update description", ex); /* Dont care */ }
        }
    }

    @Override
    public void updateBuildDescription(Run<?, ?> run) throws IOException {
        try {
        Branch triggerBranch = run.getAction(PretestTriggerCommitAction.class).triggerBranch;
        String text = createBuildDescription(triggerBranch.getName());
        if (!StringUtils.isBlank(run.getDescription())) {
            text = run.getDescription() + "\n" + text;
        }
            run.setDescription(text);
        } catch (Exception ex) {
            LOGGER.log(Level.FINE, "Failed to update description", ex); /* Dont care */ }
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public String createBuildDescription(String triggerBranchName) throws NothingToDoException, UnsupportedConfigurationException, IOException, InterruptedException {
//        return String.format("%s", "(" + getResultInfo() + "):" + triggerBranchName + " -> " + integrationBranch);
        return String.format("%s", triggerBranchName + " -> " + integrationBranch);
    }



    /**
     * Removes the repository from the integrationBranch.
     * e.g. 'origin/integrationBranch' -> 'integrationBranch'
     * @param branch the integrationBranch to strip the repository from
     * @return the integrationBranch name
     */
    private String removeRepository(String branch) {
        return branch.substring(branch.indexOf('/') + 1, branch.length());
    }

    /**
     * Returns the workspace
     * @param build The Build
     * @param listener The Listener
     * @return a FilePath representing the workspace
     * @throws InterruptedException
     * @throws IOException
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
     * @param scms
     * @throws UnsupportedConfigurationException
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
//            setResultInfo("Push");
            pushToIntegrationBranch(build, listener);
//            setResultInfo("CleanUpRemote");
            deleteIntegratedBranch(build, launcher, listener);
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
