package org.jenkinsci.plugins.pretestedintegration.scm.git;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.plugins.git.Branch;
import hudson.plugins.git.extensions.impl.RelativeTargetDirectory;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.Revision;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.util.BuildData;
import hudson.scm.SCM;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Set;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.RefSpec;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.multiplescms.MultiSCM;
import org.jenkinsci.plugins.pretestedintegration.AbstractSCMBridge;
import org.jenkinsci.plugins.pretestedintegration.exceptions.CommitFailedException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.BranchDeletionFailedException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.EstablishingWorkspaceFailedException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.NothingToDoException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.UnsupportedConfigurationException;
import org.jenkinsci.plugins.pretestedintegration.IntegrationStrategy;
import org.jenkinsci.plugins.pretestedintegration.IntegrationStrategyDescriptor;
import org.jenkinsci.plugins.pretestedintegration.PretestedIntegrationBuildWrapper;
import org.jenkinsci.plugins.pretestedintegration.SCMBridgeDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

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
     * FilePath of Git working directory
     */
    private FilePath workingDirectory;

    /**
     * Constructor for GitBridge.
     * DataBound for use in the UI.
     * @param integrationStrategy The selected IntegrationStrategy
     * @param branch The Integration Branch name
     * @param repositoryName The Integration Repository name
     */
    @DataBoundConstructor
    public GitBridge(IntegrationStrategy integrationStrategy, final String branch, String repositoryName) {
        super(integrationStrategy);
        this.branch = branch;
        this.repoName = repositoryName;
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
    protected GitSCM findScm(AbstractBuild<?, ?> build, TaskListener listener) throws InterruptedException, NothingToDoException, UnsupportedConfigurationException {
        BuildData buildData = findRelevantBuildData(build, listener);

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
                // There's only one Git SCM that matches the branch in our build data.
                // Returning the first match should be fine, as the origin name is part of the branch name
                // and we require all MultiSCM Git configurations to be explicitly and uniquely named.
                Revision revision = gitscm.getBuildData(build).lastBuild.revision;
                for (Branch bdBranch : buildData.lastBuild.revision.getBranches()) { // More than one if several branch heads are in the same commit
                    if (revision.containsBranchName(bdBranch.getName())) {
                        LOGGER.fine(String.format("Git SCM matches relevant branch."));
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
    public void ensureBranch(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, String branch) throws EstablishingWorkspaceFailedException {
        try {
            EnvVars environment = build.getEnvironment(listener);
            String expandedBranch = getExpandedBranch(environment);
            String expandedRepo = getExpandedRepository(environment);
            GitClient client = findScm(build, listener).createClient(listener, environment, build, build.getWorkspace());
            listener.getLogger().println(String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Checking out integration branch %s:", expandedBranch));
            client.checkout().branch(expandedBranch).ref(expandedRepo + "/" + expandedBranch).deleteBranchIfExist(true).execute();
            update(build, launcher, listener);
        } catch (IOException | InterruptedException ex) {
            LOGGER.log(Level.SEVERE, "ensureBranch", ex);
            throw new EstablishingWorkspaceFailedException(ex);
        }
    }

    /**
     * Pulls in the remote branch
     * @param build The Build
     * @param launcher The Launcher
     * @param listener The Listener
     * @throws IOException
     * @throws InterruptedException
     */
    protected void update(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        try {
            EnvVars environment = build.getEnvironment(listener);
            String expandedRepo = getExpandedRepository(environment);
            String expandedBranch = getExpandedBranch(environment);
            GitClient client = findScm(build, listener).createClient(listener, build.getEnvironment(listener), build, build.getWorkspace());
            client.fetch(expandedRepo, new RefSpec("refs/heads/" + expandedBranch));
            client.merge().setRevisionToMerge(client.revParse(expandedRepo + "/" + expandedBranch)).execute();
        } catch (InterruptedException | IOException ex) {
            throw new EstablishingWorkspaceFailedException(ex);
        }
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public void commit(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws CommitFailedException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            EnvVars environment = build.getEnvironment(listener);
            String expandedRepo = getExpandedRepository(environment);
            String expandedBranch = getExpandedBranch(environment);
            
            GitClient client = findScm(build, listener).createClient(listener, build.getEnvironment(listener), build, build.getWorkspace());
            LOGGER.log(Level.INFO, "Pushing changes to integration branch:");
            listener.getLogger().println(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Pushing changes to integration branch:");
            client.push(expandedRepo, "refs/heads/" + expandedBranch);
            LOGGER.log(Level.INFO, "Done pushing changes");
            listener.getLogger().println(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Done pushing changes");
        } catch (IOException | InterruptedException ex) {
            LOGGER.log(Level.SEVERE, "Failed to push changes to integration branch. Exception:", ex);
            listener.getLogger().println(PretestedIntegrationBuildWrapper.LOG_PREFIX + String.format("Failed to push changes to integration branch. Exception %s", ex));
            throw new CommitFailedException(String.format("Failed to push changes to integration branch, message was:%n%s", output.toString()));
        }
    }

    /**
     * Retrieves the BuildData for the given build relevant to the Integration Repository.
     * <ul>
     *  <li>
     *      Extracts only BuildData belonging to the Integration repository
     *  </li>
     *  <li>
     *      Ensures that identical BuildData are narrowed down to different sets
     *      as MultiScm and the Git plugin may sometimes contribute with several
     *      identical sets
     *  </li>
     *  <li>
     *      Ensures that only one relevant set is supplied. It throws in case om ambiguity.
     *  </li>
     * </ul>
     * 
     * For a visualized example of several BuilData: See 'docs/More_than_1_gitBuild_data.png'
     * TODO:
     * We don't check that the branch complies with the branch specifier,
     * or that commits are heads.
     * See JENKINS-25542, JENKINS-25512, JENKINS-24909
     *
     * @param build The Build
     * @param listener The TaskListener
     * @return The relevant BuildData
     * @throws org.jenkinsci.plugins.pretestedintegration.exceptions.NothingToDoException
     * If no relevant BuildData was found.
     * @throws org.jenkinsci.plugins.pretestedintegration.exceptions.UnsupportedConfigurationException
     * If multiple, ambiguous BuildDatas were found.
     */
    public BuildData findRelevantBuildData(AbstractBuild<?, ?> build, TaskListener listener) throws NothingToDoException, UnsupportedConfigurationException {
        List<BuildData> buildDatas = build.getActions(BuildData.class);
        if (buildDatas.isEmpty()) {
            throw new NothingToDoException("No Git SCM change found.");
        }

        Set<BuildData> relevantBuildData = findRelevantBuildDataImpl(build, listener, buildDatas);

        if (relevantBuildData.isEmpty()) {
            String prettyBuildDatasString = toPrettyString(buildDatas);
            throw new NothingToDoException(String.format("No revision matches configuration in 'Integration repository'%n%s", prettyBuildDatasString));
        } else if (relevantBuildData.size() > 1) {
            String prettyBuildDatasString = toPrettyString(relevantBuildData);
            LOGGER.log(Level.SEVERE, String.format("Ambiguous build data found. Matching repository names and multiple changes to integrate.%n%s", prettyBuildDatasString));
            throw new UnsupportedConfigurationException(UnsupportedConfigurationException.AMBIGUITY_IN_BUILD_DATA);
        } else {
            return relevantBuildData.iterator().next();
        }
    }

    /***
     * Returns the relevant BuildDatas from the supplied list of BuildDatas.
     *
     * @param build The Build
     * @param listener The TaskListener
     * @param buildDatas The list of BuildDatas
     * @return The relevant BuildDatas
     */
    private Set<BuildData> findRelevantBuildDataImpl(AbstractBuild<?, ?> build, TaskListener listener, List<BuildData> buildDatas) {
        Set<BuildData> relevantBuildData = new HashSet<>();
        Set<String> revisions = new HashSet<>(); //Used to detect duplicates

        for (BuildData buildData : buildDatas) {
            try {
                Branch buildBranch = buildData.lastBuild.revision.getBranches().iterator().next();
                String expandedRepository = getExpandedRepository(build.getEnvironment(listener)) + "/"; // Assume no trailing slash in configuration
                if (buildBranch.getName().startsWith(expandedRepository)) { // Check branch matches integration repository
                    String revisionSha = buildData.lastBuild.revision.getSha1String();
                    boolean isDuplicateEntry = !revisions.add(revisionSha); // Check we haven't seen this changeset before
                    if (isDuplicateEntry) {
                        LOGGER.log(Level.INFO, String.format("Revision %s has a duplicate BuildData entry. Using first.", revisionSha));
                    } else {
                        relevantBuildData.add(buildData);
                    }
                }
            } catch (IOException | InterruptedException ex) {
                Logger.getLogger(GitBridge.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return relevantBuildData;
    }

    /***
     * Returns a pretty string listing all the passed in BuildData.
     *
     * @param buildDatas a Collection of BuildData to list
     * @return a string listing all the given BuildData
     */
    private String toPrettyString(Collection<BuildData> buildDatas) {
        StringBuilder builder = new StringBuilder();
        for (BuildData d : buildDatas) {
            builder.append(String.format(d.lastBuild.revision.getSha1String() + "%n"));
            for (Branch b : d.lastBuild.revision.getBranches()) {
                builder.append(String.format(b.getName() + "%n"));
            }
        }
        return builder.toString();
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public void isApplicable(AbstractBuild<?, ?> build, BuildListener listener) throws NothingToDoException, UnsupportedConfigurationException {
        findRelevantBuildData(build, listener);
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public void deleteIntegratedBranch(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws BranchDeletionFailedException, NothingToDoException, UnsupportedConfigurationException {
        BuildData gitBuildData = findRelevantBuildData(build, listener);

        //At this point in time the lastBuild is also the latest.
        Branch gitDataBranch = gitBuildData.lastBuild.revision.getBranches().iterator().next();

        if (build.getResult().isBetterOrEqualTo(getRequiredResult())) {
            try {
                LOGGER.log(Level.INFO, "Deleting development branch:");
                String expandedRepo = getExpandedRepository(build.getEnvironment(listener));
                listener.getLogger().println(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Deleting development branch:");
                GitClient client = findScm(build, listener).createClient(listener, build.getEnvironment(listener), build, build.getWorkspace());
                client.push(expandedRepo, ":" + removeRepository(gitDataBranch.getName()));
                listener.getLogger().println("push " + expandedRepo + " :" + removeRepository(gitDataBranch.getName()));
                LOGGER.log(Level.INFO, "Done deleting development branch");
                listener.getLogger().println(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Done deleting development branch");
            } catch (InterruptedException | IOException ex) {
                LOGGER.log(Level.SEVERE, "Failed to delete development branch. Exception:", ex);
                listener.getLogger().println(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Failed to delete development branch. Exception:" + ex.getMessage());
                throw new BranchDeletionFailedException(String.format("Failed to delete development branch %s with the following error:%n%s", gitDataBranch.getName(), ex.getMessage()));
            }
        }
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public void updateBuildDescription(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws NothingToDoException, UnsupportedConfigurationException {
        BuildData gitBuildData = findRelevantBuildData(build, listener);
        if (gitBuildData != null) {
            Branch gitDataBranch = gitBuildData.lastBuild.revision.getBranches().iterator().next();
            String text;
            if (!StringUtils.isBlank(build.getDescription())) {
                text = String.format("%s<br/>Branch: %s", build.getDescription(), gitDataBranch.getName());
            } else {
                text = String.format("Branch: %s", gitDataBranch.getName());
            }
            try {
                build.setDescription(text);
            } catch (Exception ex) {
                LOGGER.log(Level.FINE, "Failed to update description", ex); /* Dont care */ }
        }
    }

    /**
     * Removes the repository from the branch.
     * e.g. 'origin/branch' -> 'branch'
     * @param branch the branch to strip the repository from
     * @return the branch name
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
    private boolean validateMultiScm(List<SCM> scms) throws UnsupportedConfigurationException {
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
     * Counts the commits in the relevant BuildData
     * @param build The Build
     * @param listener The Listener
     * @return the amount of commits
     * @throws IOException
     * @throws InterruptedException
     */
    public int countCommits(AbstractBuild<?, ?> build, BuildListener listener) throws IOException, InterruptedException {
        ObjectId commitId = findRelevantBuildData(build, listener).lastBuild.revision.getSha1();
        GitClient client = Git.with(listener, build.getEnvironment(listener)).in(resolveWorkspace(build, listener)).getClient();
        GetCommitCountFromBranchCallback commitCountCallback = new GetCommitCountFromBranchCallback(listener, commitId, getExpandedBranch(build.getEnvironment(listener)));
        int commitCount = client.withRepository(commitCountCallback);
        return commitCount;
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public void handlePostBuild(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IOException {
        updateBuildDescription(build, launcher, listener);

        // TODO: Implement robustness in situations where this contains multiple revisons where two branches point to the same commit.
        // (JENKINS-24909). Check branch spec before doing anything
        BuildData gitBuildData = findRelevantBuildData(build, listener);
        Branch gitDataBranch = gitBuildData.lastBuild.revision.getBranches().iterator().next();

        String integrationBranch;
        EnvVars environment;
        try {
            environment = build.getEnvironment(listener);
            integrationBranch = getExpandedBranch(environment);
        } catch (InterruptedException ex) {
            integrationBranch = getBranch();
        }

        // The purpose of this section of code is to disallow usage of the master or integration branch as the polling branch.
        // TODO: This branch check should be moved to job configuration check method.
        String devBranchName = gitDataBranch.getName();
        if (devBranchName.equals("master") || devBranchName.equals(getRepoName() + "/master")
                || devBranchName.equals(integrationBranch)|| devBranchName.equals(getRepoName() + "/" + integrationBranch) ) {
            String msg = "Using the master or integration branch for polling and development is not "
                       + "allowed since it will attempt to merge it to other branches and delete it after. Failing build.";
            LOGGER.log(Level.SEVERE, msg);
            listener.getLogger().println(PretestedIntegrationBuildWrapper.LOG_PREFIX + msg);
            build.setResult(Result.FAILURE);
        }

        Result result = build.getResult();
        if (result != null && result.isBetterOrEqualTo(getRequiredResult())) {
            commit(build, launcher, listener);
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
    public String getBranch() {
        return StringUtils.isBlank(this.branch) ? "master" : this.branch;
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public String getExpandedBranch(EnvVars environment) {
        String expandedBranch = super.getExpandedBranch(environment);
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
    public void setRepoName(String repositoryName) {
        this.repoName = repositoryName;
    }

    /**
     * @return the repository name expanded using given environment variables.
     */
    private String getExpandedRepository(EnvVars environment) {
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
