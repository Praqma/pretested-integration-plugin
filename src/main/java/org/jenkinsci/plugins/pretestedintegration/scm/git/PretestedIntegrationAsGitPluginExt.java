package org.jenkinsci.plugins.pretestedintegration.scm.git;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Plugin;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.*;
import hudson.plugins.git.extensions.GitClientType;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import hudson.plugins.git.util.Build;
import hudson.plugins.git.util.GitUtils;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.MergeCommand;
import org.jenkinsci.plugins.pretestedintegration.IntegrationStrategy;
import org.jenkinsci.plugins.pretestedintegration.IntegrationStrategyDescriptor;
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

import static org.eclipse.jgit.lib.Constants.HEAD;

/**
 * The Pretested Integration Plugin - automating The Phlow for git in Jenkins - as a Git Plugin Extension
 */
public class PretestedIntegrationAsGitPluginExt extends GitSCMExtension {
    final static String LOG_PREFIX = "[PREINT] ";
    private static final Logger LOGGER = Logger.getLogger(PretestedIntegrationAsGitPluginExt.class.getName());
    public String integrationBranch = "master";
    public String repoName;
    public IntegrationStrategy gitIntegrationStrategy;

    public PretestedIntegrationAsGitPluginExt() { }

    /**
     * Constructor for GitBridge.
     * DataBound for use in the UI.
     *
     * @param gitIntegrationStrategy The selected IntegrationStrategy
     * @param integrationBranch      The Integration Branch name
     * @param repoName               The Integration Repository name
     */
    @DataBoundConstructor
    public PretestedIntegrationAsGitPluginExt(IntegrationStrategy gitIntegrationStrategy, String integrationBranch, String repoName) {
        this.integrationBranch = integrationBranch;
        this.repoName = repoName;
        this.gitIntegrationStrategy = gitIntegrationStrategy;
    }

    public IntegrationStrategy getGitIntegrationStrategy() {
        return this.gitIntegrationStrategy;
    }


    public String getIntegrationBranch() {
        return this.integrationBranch;
    }


    public GitBridge getGitBridge() {
        return new GitBridge(gitIntegrationStrategy, integrationBranch, repoName);
    }

    /**
     * @return the plugin version
     */
    public String getVersion() {
        Plugin pretested = Jenkins.getActiveInstance().getPlugin("pretested-integration");
        if (pretested != null) return pretested.getWrapper().getVersion();
        else return "unable to retrieve plugin version";
    }

    @Override
    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    public Revision decorateRevisionToBuild(
            GitSCM scm,
            Run<?, ?> run,
            GitClient git,
            TaskListener listener,
            Revision marked,
            Revision triggeredRevision) throws IOException, InterruptedException {
        listener.getLogger().println(String.format("%s Pretested Integration Plugin v%s", LOG_PREFIX, getVersion()));

        GitBridge gitBridge = getGitBridge();

        EnvVars environment = run.getEnvironment(listener);

        String expandedIntegrationBranch = gitBridge.getExpandedIntegrationBranch(environment);
        String expandedRepo = gitBridge.getExpandedRepository(environment);
        String ucCredentialsId = "";

        for (UserRemoteConfig uc : scm.getUserRemoteConfigs()) {
            String credentialsRepoName = StringUtils.isBlank(uc.getName()) ? "origin" : uc.getName();
            if (credentialsRepoName != null && credentialsRepoName.equals(expandedRepo)) {
                String ucCred = uc.getCredentialsId();
                if (ucCred != null) {
                    ucCredentialsId = uc.getCredentialsId();
                }
            }
        }

        Branch triggeredBranch = null;
        if ( triggeredRevision.getBranches().isEmpty() ) {
            run.setResult(Result.NOT_BUILT);
            String logMessage = String.format("%s - No branch on revision which we cannot handle - leaving workspace: %s  and set result to NOT_BUILT", LOG_PREFIX, expandedIntegrationBranch);
            listener.getLogger().println(logMessage);
        } else {
            // TODO: Should this be last branch in stead of?
            Branch triggeredBranchDraft = triggeredRevision.getBranches().iterator().next();
            triggeredBranch = new Branch(triggeredBranchDraft.getName().replaceFirst("refs/remotes/", ""), triggeredBranchDraft.getSHA1());
        }

        if(!run.getActions(PretestTriggerCommitAction.class).isEmpty() ) {
            run.setResult(Result.FAILURE);
            String logMessage = String.format("%s ERROR Likely misconfigered. Currently it is not supported to integrate twice in a build. It is likely because of Pipeline preSCM step or multiSCM. Please see https://github.com/Praqma/pretested-integration-plugin/issues/133 for details about Pipeline preSCM support. If it is neither scenarios, please report it", LOG_PREFIX );
            listener.getLogger().println(logMessage);
        }

        if (run.getResult() == null || run.getResult() == Result.SUCCESS ) {
            try {
                gitBridge.evalBranchConfigurations(triggeredBranch, expandedIntegrationBranch, expandedRepo);
                listener.getLogger().println(String.format(LOG_PREFIX + "Checking out integration branch %s:", expandedIntegrationBranch));
                git.checkout().branch(expandedIntegrationBranch).ref(expandedRepo + "/" + expandedIntegrationBranch).deleteBranchIfExist(true).execute();
                ((GitIntegrationStrategy) gitBridge.integrationStrategy).integrate(scm, run, git, listener, marked, triggeredBranch, gitBridge);
            } catch (NothingToDoException e) {
                run.setResult(Result.NOT_BUILT);
                String logMessage = String.format("%s - setUp() - NothingToDoException - %s", LOG_PREFIX, e.getMessage());
                listener.getLogger().println(logMessage);
                LOGGER.log(Level.SEVERE, logMessage, e);
                //Only do this when polling and we have an object
                if(scm.getBuildData(run) != null) {
                    scm.getBuildData(run).saveBuild(new Build(marked, triggeredRevision, run.getNumber(), run.getResult()));
                }
                // Leave the workspace as we were triggered, so postbuild step can report the correct branch
                git.checkout().ref(triggeredBranch.getName()).execute();
            } catch (IntegrationFailedException | EstablishingWorkspaceFailedException | UnsupportedConfigurationException e) {
                run.setResult(Result.FAILURE);
                String logMessage = String.format("%s - setUp() - %s - %s", LOG_PREFIX, e.getClass().getSimpleName(), e.getMessage());
                listener.getLogger().println(logMessage);
                LOGGER.log(Level.SEVERE, logMessage, e);
                git.checkout().branch(expandedIntegrationBranch).ref(expandedRepo + "/" + expandedIntegrationBranch).deleteBranchIfExist(true).execute();
            } catch (IOException | InterruptedException e) {
                run.setResult(Result.FAILURE);
                String logMessage = String.format("%s - Unexpected error. %n%s", LOG_PREFIX, e.getMessage());
                LOGGER.log(Level.SEVERE, logMessage, e);
                listener.getLogger().println(logMessage);
                e.printStackTrace(listener.getLogger());
                git.checkout().branch(expandedIntegrationBranch).ref(expandedRepo + "/" + expandedIntegrationBranch).deleteBranchIfExist(true).execute();
            }
        }

        run.addAction(new PretestTriggerCommitAction(triggeredBranch, expandedIntegrationBranch, expandedRepo, ucCredentialsId));
        if (run.getResult() == null || run.getResult() == Result.SUCCESS || run.getResult() == Result.NOT_BUILT) {
            Revision mergeRevision = new GitUtils(listener, git).getRevisionForSHA1(git.revParse(HEAD));
            if ( triggeredBranch != null ) {
                mergeRevision.getBranches().add(triggeredBranch);
            }
            return mergeRevision;
        } else {
            // reset the workspace to the triggered revision
            git.checkout().ref(triggeredBranch.getName()).execute();
            // We could not integrate, but we must return a revision for recording it so it does not retrigger
            scm.getBuildData(run).saveBuild(new Build(marked, triggeredRevision, run.getNumber(), Result.FAILURE));
            // throwing the AbortException will result in a status=FAILURE
            throw new AbortException(String.format("%s Unable to integrate revision: %s", LOG_PREFIX, triggeredRevision.getSha1String()));
        }
    }

    @Override
    public void decorateMergeCommand(GitSCM scm, Run<?, ?> build, GitClient git, TaskListener listener, MergeCommand cmd) throws IOException, InterruptedException, GitException {
    }

    @Override
    public GitClientType getRequiredClient() {
        return GitClientType.GITCLI;
    }

    @Symbol("pretestedIntegration")
    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {

        @Override
        public String getDisplayName() {
            return "Use pretested integration";
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
            return new SquashCommitStrategy();
        }
    }
}
