package org.jenkinsci.plugins.pretestedintegration.scm.git;

import hudson.*;
import hudson.matrix.*;
import hudson.model.*;
import hudson.plugins.git.*;
import hudson.plugins.git.extensions.GitClientType;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import hudson.plugins.git.util.GitUtils;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.gitclient.ChangelogCommand;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.MergeCommand;
import org.jenkinsci.plugins.pretestedintegration.IntegrationStrategy;
import org.jenkinsci.plugins.pretestedintegration.IntegrationStrategyDescriptor;
import org.jenkinsci.plugins.pretestedintegration.exceptions.*;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.framework.io.WriterOutputStream;

import static org.eclipse.jgit.lib.Constants.HEAD;

/**
 * The Pretested Integration Plugin - automating The Phlow for git in Jenkins - as a Git Plugin Extension
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
     */
    @DataBoundConstructor
    public PretestedIntegrationAsGitPluginExt(IntegrationStrategy gitIntegrationStrategy, final String integrationBranch, final String repoName) {
        this.gitBridge = new GitBridge(
                gitIntegrationStrategy,
                integrationBranch,
                repoName
        );
    }

    public IntegrationStrategy getGitIntegrationStrategy(){
        return gitBridge.integrationStrategy;
    }

    public String getRepoName(){
        return gitBridge.getRepoName();
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

        EnvVars environment = run.getEnvironment(listener);

        // TODO: Should this be last branch in stead of?
        Branch triggeredBranch = triggeredRevision.getBranches().iterator().next();
        String expandedIntegrationBranch = gitBridge.getExpandedIntegrationBranch(environment);
        String expandedRepo = gitBridge.getExpandedRepository(environment);
        String ucCredentialsId = "";

        for (UserRemoteConfig uc : scm.getUserRemoteConfigs()) {
            String credentialsRepoName = uc.getName();
            if ( credentialsRepoName != null && credentialsRepoName.equals(expandedRepo) ){
                String ucCred = uc.getCredentialsId();
                if ( ucCred != null ){
                    ucCredentialsId = uc.getCredentialsId();
                }
            }
        }

        try {
            gitBridge.evalBranchConfigurations(triggeredBranch, expandedIntegrationBranch, expandedRepo);

            ChangelogCommand changelog = git.changelog();
            changelog.includes(triggeredRevision.getSha1());
            Writer out = new StringWriter();
            listener.getLogger().println("Using 'Changelog to branch' strategy.");
            changelog.excludes(expandedRepo + "/" + expandedIntegrationBranch);
            changelog.to(out).execute();
            if (out.toString().contains("Jenkinsfile")){
                listener.getLogger().println(String.format("%s You have changed Jenkinsfile", LOG_PREFIX));
//                throw new IntegrationFailedException("You have changed Jenkinsfile");
            }

            listener.getLogger().println(String.format(LOG_PREFIX + "Checking out integration branch %s:", expandedIntegrationBranch));
            git.checkout().branch(expandedIntegrationBranch).ref(expandedRepo + "/" + expandedIntegrationBranch).deleteBranchIfExist(true).execute();
            ((GitIntegrationStrategy) gitBridge.integrationStrategy).integrateAsGitPluginExt(scm, run, git, listener, marked, triggeredRevision, gitBridge);

        } catch (NothingToDoException e) {
            run.setResult(Result.NOT_BUILT);
            String logMessage = LOG_PREFIX + String.format("%s - setUp() - NothingToDoException - %s", LOG_PREFIX, e.getMessage());
            listener.getLogger().println(logMessage);
            LOGGER.log(Level.SEVERE, logMessage, e);
            git.checkout().branch(expandedIntegrationBranch).ref(expandedRepo + "/" + expandedIntegrationBranch).deleteBranchIfExist(true).execute();
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

        run.addAction(new PretestTriggerCommitAction( triggeredBranch, expandedIntegrationBranch, expandedRepo, ucCredentialsId));
        if ( run.getResult() == null || run.getResult() == Result.SUCCESS ) {
            Revision mergeRevision = new GitUtils(listener,git).getRevisionForSHA1(git.revParse(HEAD));

            return mergeRevision;
        } else {
            // We could not integrate, but we must return a revision for recording it so it does not retrigger
            git.checkout().ref(triggeredBranch.getName()).execute();
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

    @Symbol("PretestedIntegration")
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
