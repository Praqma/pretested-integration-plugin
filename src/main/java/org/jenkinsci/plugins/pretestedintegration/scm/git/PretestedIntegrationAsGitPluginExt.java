package org.jenkinsci.plugins.pretestedintegration.scm.git;

import hudson.*;
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
import org.jenkinsci.plugins.gitclient.CheckoutCommand;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.MergeCommand;
import org.jenkinsci.plugins.pretestedintegration.IntegrationStrategy;
import org.jenkinsci.plugins.pretestedintegration.IntegrationStrategyDescriptor;
import org.jenkinsci.plugins.pretestedintegration.PretestedIntegrationBuildWrapper;
import org.jenkinsci.plugins.pretestedintegration.exceptions.IntegrationFailedException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.NothingToDoException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.UnsupportedConfigurationException;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

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
     */
    @DataBoundConstructor
    public PretestedIntegrationAsGitPluginExt(GitIntegrationStrategy gitIntegrationStrategy, final String integrationBranch, String repoName) {
        this.gitBridge = new GitBridge(gitIntegrationStrategy,integrationBranch,repoName);
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
    public String getIntegrationBranch(){
        if ( gitBridge == null ) {
            return "master";
        } else {
            return gitBridge.getIntegrationBranch();
        }
    }

    public void setGitBridge(GitBridge gitBridge ){
        this.gitBridge = gitBridge;
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
    public Revision decorateRevisionToBuild(GitSCM scm, Run<?, ?> run, GitClient git, TaskListener listener, Revision marked, Revision triggeredRevision)
            throws IOException, InterruptedException {
        EnvVars environment = run.getEnvironment(listener);

        Branch triggeredBranch = triggeredRevision.getBranches().iterator().next();
        run.addAction(new PretestTriggerCommitAction(triggeredBranch));

        String expandedIntegrationBranch = gitBridge.getExpandedIntegrationBranch(environment);
        String expandedRepo = gitBridge.getExpandedRepository(environment);

        try {
            // TODO: do Praqma Git Phlow stuff
            listener.getLogger().println(String.format("%s Pretested Integration Plugin v%s", LOG_PREFIX, getVersion()));

            try {
                String devBranchName = expandedIntegrationBranch;
                if ( expandedIntegrationBranch.equals(triggeredBranch.getName() ) ||
                        devBranchName.equals(expandedRepo + "/" + triggeredBranch.getName() ) ) {
                    String msg = "Using the integration integrationBranch for polling and development is not "
                               + "allowed since it will attempt to merge it to other branches and delete it after. Failing build.";
                    LOGGER.log(Level.SEVERE, msg);
                    listener.getLogger().println(PretestedIntegrationBuildWrapper.LOG_PREFIX + msg);
                    run.setResult(Result.FAILURE);
                    throw new AbortException(msg);
                }

                listener.getLogger().println(String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Checking out integration integrationBranch %s:", expandedIntegrationBranch));
                git.checkout().branch(expandedIntegrationBranch).ref(expandedRepo + "/" + expandedIntegrationBranch).deleteBranchIfExist(true).execute();
                git.merge().setRevisionToMerge(git.revParse(expandedRepo + "/" + expandedIntegrationBranch)).execute();

                ((GitIntegrationStrategy)gitBridge.integrationStrategy).integrateAsGitPluginExt(scm,run,git,listener,marked,triggeredRevision, gitBridge);

            } catch (NothingToDoException e) {
                run.setResult(Result.NOT_BUILT);
                String logMessage = String.format("%s - decorateRevisionToBuild() - NothingToDoException - %s", LOG_PREFIX, e.getMessage());
                listener.getLogger().println(logMessage);
                LOGGER.log(Level.SEVERE, logMessage, e);
                try {
                    LOGGER.fine("Setting build description 'Nothing to do':");
                    run.setDescription("Noting to do: " + triggeredRevision.getBranches().iterator().next().getName());
                } catch (IOException ex) {
                    LOGGER.log(Level.FINE, "Failed to update build description", ex);
                }
                gitBridge.setResultInfo("NothingToDo");
                gitBridge.updateBuildDescription(run);
                throw new GitException("Nothing to do..");
            } catch (IntegrationFailedException| UnsupportedConfigurationException e) {
                if (gitBridge.getIntegrationFailedStatusUnstable()) {
                    run.setResult(Result.UNSTABLE);
                } else {
                    run.setResult(Result.FAILURE);
                }
                gitBridge.setResultInfo("MergeFailed");
                String logMessage = String.format("%s - decorateRevisionToBuild() - %s - %s", LOG_PREFIX, e.getClass().getSimpleName(), e.getMessage());
                listener.getLogger().println(logMessage);
                LOGGER.log(Level.SEVERE, logMessage, e);

                // merge conflict. First, avoid leaving any conflict markers in the working tree
                // by checking out some known clean state. We don't really mind what commit this is,
                // since the next build is going to pick its own commit to build, but 'rev' is as good any.
                CheckoutCommand checkoutCommand = git.checkout().branch(this.gitBridge.getExpandedIntegrationBranch(environment)).ref(triggeredRevision.getSha1String()).deleteBranchIfExist(true);
                checkoutCommand.execute();
            } catch (IOException e) {
                run.setResult(Result.FAILURE);
                gitBridge.setResultInfo("UNKNOWN");
                gitBridge.updateBuildDescription(run);
                String logMessage = String.format("%s - Unexpected error. %n%s", LOG_PREFIX, e.getMessage());
                LOGGER.log(Level.SEVERE, logMessage, e);
                listener.getLogger().println(logMessage);
                e.printStackTrace(listener.getLogger());
                throw new AbortException();
            }

        } catch (GitException ex) {
            String logMessage = LOG_PREFIX + "Git operation failed";
            LOGGER.log(Level.SEVERE, logMessage, ex);
            listener.getLogger().println(logMessage);
            gitBridge.updateBuildDescription(run);
            throw new AbortException(ex.getMessage());
        }

        if ( run.getResult() == null || run.getResult() == Result.SUCCESS ) {
            Revision mergeRevision = new GitUtils(listener,git).getRevisionForSHA1(git.revParse(HEAD));
            gitBridge.setResultInfo("Build");
            mergeRevision.getBranches().add(
                    new Branch(gitBridge.getExpandedIntegrationBranch(environment), triggeredRevision.getSha1()));
            return mergeRevision;
        } else {
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
            return "Praqma Git Phlow - Verification before merge to integration integrationBranch";
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
