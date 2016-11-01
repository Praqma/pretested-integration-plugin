package org.jenkinsci.plugins.pretestedintegration.scm.git;

import hudson.*;
import hudson.model.*;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.Revision;
import hudson.plugins.git.Branch;
import hudson.plugins.git.UserMergeOptions;
import hudson.plugins.git.extensions.GitClientType;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import hudson.plugins.git.util.Build;
import hudson.plugins.git.util.GitUtils;
import hudson.plugins.git.util.MergeRecord;
import jenkins.model.Jenkins;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.RefSpec;
import org.jenkinsci.plugins.gitclient.CheckoutCommand;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.MergeCommand;
import org.jenkinsci.plugins.multiplescms.MultiSCM;
import org.jenkinsci.plugins.pretestedintegration.IntegrationStrategy;
import org.jenkinsci.plugins.pretestedintegration.IntegrationStrategyDescriptor;
import org.jenkinsci.plugins.pretestedintegration.PretestedIntegrationBuildWrapper;
import org.jenkinsci.plugins.pretestedintegration.exceptions.EstablishingWorkspaceFailedException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.IntegrationFailedException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.NothingToDoException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.UnsupportedConfigurationException;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static hudson.model.Result.FAILURE;

import org.kohsuke.stapler.DataBoundSetter;

import static org.eclipse.jgit.lib.Constants.HEAD;

/**
 * Speculatively merge the selected commit with another branch before the build to answer the "what happens
 * if I were to integrate this feature branch back to the master?" question.
 *
 * @author Nigel Magney
 * @author Nicolas Deloof
 * @author Andrew Bayer
 * @author Kohsuke Kawaguchi
 */
public class PretestedIntegrationAsGitPluginExt extends GitSCMExtension {
    private static final Logger LOGGER = Logger.getLogger(GitBridge.class.getName());

    /**
     * The name of the integration repository.
     */
    public final String repoName;
    public final String branch;

    private boolean integrationFailedStatusUnstable;

    public final GitIntegrationStrategy gitIntegrationStrategy;

    final static String LOG_PREFIX = "[PREINT] ";

    /**
     * FilePath of Git working directory
     */
    private FilePath workingDirectory;

    /**
     * Constructor for GitBridge.
     * DataBound for use in the UI.
     * @param gitIntegrationStrategy The selected IntegrationStrategy
     * @param branch The Integration Branch name
     * @param repoName The Integration Repository name
     */
    @DataBoundConstructor
    public PretestedIntegrationAsGitPluginExt(GitIntegrationStrategy gitIntegrationStrategy, final String branch, String repoName) {
        this.gitIntegrationStrategy = gitIntegrationStrategy;
        this.branch = branch;
        this.repoName = repoName;
    }

    @DataBoundSetter
    public void setIntegrationFailedStatusUnstable(boolean integrationFailedStatusUnstable){
        this.integrationFailedStatusUnstable = integrationFailedStatusUnstable;
    }

    public boolean isIntegrationFailedStatusUnstable(){
        return this.integrationFailedStatusUnstable;
    }

    /**
     * @return the plugin version
     */
    public String getVersion(){
        Plugin pretested = Jenkins.getInstance().getPlugin("pretested-integration");
        if (pretested != null) return pretested.getWrapper().getVersion();
        else return "unable to retrieve plugin version";
    }

    @Override
    public Revision decorateRevisionToBuild(GitSCM scm, Run<?, ?> run, GitClient git, TaskListener listener, Revision marked, Revision rev)
            throws IOException, InterruptedException {
        String remoteBranchRef = this.branch;

        // if the branch we are merging is already at the commit being built, the entire merge becomes no-op
        // so there's nothing to
// TODO: use this?
/*
        if (rev.containsBranchName(remoteBranchRef))
            return rev;

        // Only merge if there's a branch to merge that isn't us..
        listener.getLogger().println("Merging " + rev + " to " + remoteBranchRef );
*/
        // checkout origin/blah
//        ObjectId target = git.revParse(remoteBranchRef);
/*
        String paramLocalBranch = scm.getParamLocalBranch(build, listener);
        CheckoutCommand checkoutCommand = git.checkout().branch(paramLocalBranch).ref(remoteBranchRef).deleteBranchIfExist(true);
        for (GitSCMExtension ext : scm.getExtensions())
            ext.decorateCheckoutCommand(scm, build, git, listener, checkoutCommand);
        checkoutCommand.execute();
*/

        try {
            GitBridge scmBridge = new GitBridge(gitIntegrationStrategy, branch, repoName );
            // TODO: do Praqma Git Phlow stuff
            listener.getLogger().println(String.format("%s Pretested Integration Plugin v%s", LOG_PREFIX, getVersion()));
            boolean proceedToBuildStep = true;
            try {

// Extracted from               scmBridge.validateConfiguration(build.getProject());
/* TODO: Does it make sense to consider if Git / MultiSCM during integration as we are under GitPlugin which should work out-of-the-box?
                if ( ! (scm instanceof GitSCM)) {
                    throw new UnsupportedConfigurationException("We only support 'Git'");
                }
                if (Jenkins.getInstance().getPlugin("multiple-scms") != null && project.getScm() instanceof MultiSCM) {
                    MultiSCM multiscm = (MultiSCM)scm.get;
                    scmBridge.validateMultiScm(multiscm.getConfiguredSCMs());
                } else {
                    throw new UnsupportedConfigurationException("We only support 'Git' and 'Multiple SCMs' plugins");
                }
*/
//        if ( project instanceof FreeStyleProject == false )
//            throw new UnsupportedConfigurationException("We only support Freestyle projects, but feel free to try");

//
// TODO: just find buildData, but we already have it if needed:
// scmBridge.isApplicable(build, listener);



/* Extracted: Start from scmBridge.ensureBranch(build, listener, scmBridge.getExpandedBranch(build.getEnvironment(listener)));
scmBridge.update((AbstractBuild)run, (BuildListener)listener);
*/
                EnvVars environment = run.getEnvironment(listener);
                String expandedBranch = scmBridge.getExpandedBranch(environment);
                String expandedRepo = scmBridge.getExpandedRepository(environment);
//                    GitClient client = findScm(build, listener).createClient(listener, environment, build, build.getWorkspace());
                listener.getLogger().println(String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Checking out integration branch %s:", expandedBranch));
                git.checkout().branch(expandedBranch).ref(expandedRepo + "/" + expandedBranch).deleteBranchIfExist(true).execute();
                git.merge().setRevisionToMerge(git.revParse(expandedRepo + "/" + expandedBranch)).execute();
/* Extracted: End from scmBridge.ensureBranch(build, listener, scmBridge.getExpandedBranch(build.getEnvironment(listener)));
*/

// Integrate extracted from scmBridge.prepareWorkspace(build, listener);
                gitIntegrationStrategy.integrateAsGitPluginExt(scm,run,git,listener,marked,rev, scmBridge);

            } catch (NothingToDoException e) {
                run.setResult(Result.NOT_BUILT);
                String logMessage = LOG_PREFIX + String.format("%s - decorateRevisionToBuild() - NothingToDoException - %s", LOG_PREFIX, e.getMessage());
                listener.getLogger().println(logMessage);
                LOGGER.log(Level.SEVERE, logMessage, e);
            } catch (IntegrationFailedException | EstablishingWorkspaceFailedException | UnsupportedConfigurationException e) {
                run.setResult(Result.UNSTABLE);
                String logMessage = String.format("%s - setUp() - %s - %s", LOG_PREFIX, e.getClass().getSimpleName(), e.getMessage());
                listener.getLogger().println(logMessage);
                LOGGER.log(Level.SEVERE, logMessage, e);
            } catch (IOException e) {
                run.setResult(Result.FAILURE);
                String logMessage = String.format("%s - Unexpected error. %n%s", LOG_PREFIX, e.getMessage());
                LOGGER.log(Level.SEVERE, logMessage, e);
                listener.getLogger().println(logMessage);
                e.printStackTrace(listener.getLogger());
            }

        } catch (GitException ex) {
            // merge conflict. First, avoid leaving any conflict markers in the working tree
             scm.getBuildData(run).saveBuild(new Build(marked,rev, run.getNumber(), FAILURE));
            throw new AbortException("Branch not suitable for integration as it does not merge cleanly: " + ex.getMessage());
        }

//        build.addAction(new MergeRecord(remoteBranchRef,target.getName()));
//
//        Revision mergeRevision = new GitUtils(listener,git).getRevisionForSHA1(git.revParse(HEAD));
//        mergeRevision.getBranches().add(new Branch(remoteBranchRef, target));
        return rev;
    }

    @Override
    public void decorateMergeCommand(GitSCM scm, Run<?, ?> build, GitClient git, TaskListener listener, MergeCommand cmd) throws IOException, InterruptedException, GitException {
    }

    @Override
    public GitClientType getRequiredClient() {
        return GitClientType.GITCLI;
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        @Override
        public String getDisplayName() {
            return "Praqma Git Phlow - PreIntegration before build";
        }
        public List<IntegrationStrategyDescriptor<?>> getIntegrationStrategies() {
            List<IntegrationStrategyDescriptor<?>> list = new ArrayList<>();
            for (IntegrationStrategyDescriptor<?> descr : IntegrationStrategy.all()) {
                    list.add(descr);
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
