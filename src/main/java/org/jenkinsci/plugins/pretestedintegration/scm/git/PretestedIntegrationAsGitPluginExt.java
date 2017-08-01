package org.jenkinsci.plugins.pretestedintegration.scm.git;

import hudson.*;
import hudson.matrix.*;
import hudson.model.*;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.Revision;
import hudson.plugins.git.Branch;
import hudson.plugins.git.extensions.GitClientType;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import hudson.plugins.git.util.GitUtils;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.MergeCommand;
import org.jenkinsci.plugins.pretestedintegration.IntegrationStrategy;
import org.jenkinsci.plugins.pretestedintegration.IntegrationStrategyDescriptor;
import org.jenkinsci.plugins.pretestedintegration.PretestedIntegrationBuildWrapper;
import org.jenkinsci.plugins.pretestedintegration.exceptions.IntegrationAllowedNoCommitException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.IntegrationFailedException;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.kohsuke.stapler.DataBoundSetter;

import static org.eclipse.jgit.lib.Constants.HEAD;

/**
 * The Praqma Git Phlow - Automated Git branching model - as a Git Plugin Extension
 *
 */
public class PretestedIntegrationAsGitPluginExt extends GitSCMExtension {
    private static final Logger LOGGER = Logger.getLogger(PretestedIntegrationAsGitPluginExt.class.getName());
    private GitBridge gitBridge;
    final static String LOG_PREFIX = "[PREINT] ";

    /**
     * Constructor for GitBridge.
     * DataBound for use in the UI.
     * @param gitIntegrationStrategy The selected IntegrationStrategy
     * @param integrationBranch The Integration Branch name
     * @param repoName The Integration Repository name
     * @param allowedNoCommits The amount of commits allowed for integration
     */
    @DataBoundConstructor
    public PretestedIntegrationAsGitPluginExt(IntegrationStrategy gitIntegrationStrategy, final String integrationBranch, final String repoName, boolean integrationFailedStatusUnstable, String allowedNoCommits) {
        this.gitBridge = new GitBridge(
                gitIntegrationStrategy,
                integrationBranch,
                repoName,
                integrationFailedStatusUnstable,
                Integer.valueOf(allowedNoCommits));
    }

    @DataBoundSetter
    public void setIntegrationFailedStatusUnstable(boolean integrationFailedStatusUnstable){
        this.gitBridge.setIntegrationFailedStatusUnstable(integrationFailedStatusUnstable);
    }

    public boolean getIntegrationFailedStatusUnstable(){
        if ( gitBridge == null ) {
            return false;
        } else {
            return gitBridge.getIntegrationFailedStatusUnstable();
        }
    }
    public String getAllowedNoCommits(){
        if ( gitBridge == null ) {
            return "";
        } else {
            return Integer.toString(gitBridge.getAllowedNoCommits());
        }
    }
    public String getIntegrationBranch(){
        if ( gitBridge == null ) {
            return "master";
        } else {
            return gitBridge.getIntegrationBranch();
        }
    }

    public GitBridge getGitBridge(){
        return this.gitBridge;
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
    public Revision decorateRevisionToBuild(
                        GitSCM scm,
                        Run<?, ?> run,
                        GitClient git,
                        TaskListener listener,
                        Revision marked,
                        Revision triggeredRevision ) throws IOException, InterruptedException
    {
        listener.getLogger().println(String.format("%s Pretested Integration Plugin v%s", LOG_PREFIX, getVersion()));

        if ( run instanceof MatrixRun ) {
            Branch mergedBranch = ((MatrixRun) run).getParentBuild().getAction(PretestTriggerCommitAction.class).triggerBranch;
            listener.getLogger().println(String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Checking out merged branch %s from MatrixParent job ", mergedBranch.getName()));
            git.checkout().branch(mergedBranch.getName()).execute();
            Revision mergeRevision = new GitUtils(listener,git).getRevisionForSHA1(git.revParse(HEAD));
            return mergeRevision;
        }

        EnvVars environment = run.getEnvironment(listener);

        // TODO: Should this be last branch in stead of?
        Branch triggeredBranch = triggeredRevision.getBranches().iterator().next();
        String expandedIntegrationBranch = gitBridge.getExpandedIntegrationBranch(environment);
        String expandedRepo = gitBridge.getExpandedRepository(environment);
        run.addAction(new PretestTriggerCommitAction(triggeredBranch));

        try {
            gitBridge.evalBranchConfigurations(triggeredBranch, expandedIntegrationBranch, expandedRepo);
            listener.getLogger().println(String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Checking out integration branch %s:", expandedIntegrationBranch));
            git.checkout().branch(expandedIntegrationBranch).ref(expandedRepo + "/" + expandedIntegrationBranch).deleteBranchIfExist(true).execute();
            ((GitIntegrationStrategy) gitBridge.integrationStrategy).integrateAsGitPluginExt(scm, run, git, listener, marked, triggeredRevision, gitBridge);
        } catch ( IntegrationAllowedNoCommitException | IntegrationFailedException e ) {
                gitBridge.handleIntegrationExceptionsGit(run, listener, e, git);
        } catch (Exception e) {
            // Get back to the triggered state, before handling the exceptions
            gitBridge.handleIntegrationExceptionsGit(run, listener, e, git);
        }

        if ( run.getResult() == null || run.getResult() == Result.SUCCESS ) {
            Revision mergeRevision = new GitUtils(listener,git).getRevisionForSHA1(git.revParse(HEAD));
            run.addAction(new PretestTriggerCommitAction(new Branch(triggeredBranch.getName(),triggeredBranch.getSHA1())));
            return mergeRevision;
        } else {
            // We could not integrate, but we want to update the branch name accordingly. Checkout the triggered branch
            // branch again before pushing
            git.checkout().ref(triggeredBranch.getName()).execute();

            run.addAction(new PretestTriggerCommitAction(new Branch(triggeredBranch.getName(),triggeredBranch.getSHA1())));

            return triggeredRevision;
        }
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
            return "Praqma Git Phlow - Verification before merge to integration branch";
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
