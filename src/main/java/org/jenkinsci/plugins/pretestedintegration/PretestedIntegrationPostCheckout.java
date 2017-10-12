package org.jenkinsci.plugins.pretestedintegration;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.matrix.MatrixAggregatable;
import hudson.matrix.MatrixAggregator;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixConfiguration;
import hudson.model.*;
import hudson.plugins.git.GitSCM;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.pretestedintegration.scm.git.GitBridge;
import org.jenkinsci.plugins.pretestedintegration.scm.git.PretestTriggerCommitAction;
import org.jenkinsci.plugins.pretestedintegration.scm.git.PretestedIntegrationAsGitPluginExt;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The publisher determines what will happen when the build has been run.
 * Depending on the chosen SCM, a more specific function will be called.
 */
public class PretestedIntegrationPostCheckout extends Recorder implements Serializable, MatrixAggregatable, SimpleBuildStep {

    final static String LOG_PREFIX = "[PREINT] ";
    private static final Logger LOGGER = Logger.getLogger(PretestedIntegrationPostCheckout.class.getName());

    /**
     * Constructor for PretestedIntegrationPostCheckout
     */
    @DataBoundConstructor
    public PretestedIntegrationPostCheckout() {
    }

    /**
     * Gets the SCM Bridge of the BuildWrapper of this project.
     *
     * @param build the Build whose project to get the SCM Bridge of.
     * @return the SCM Bridge of the BuildWrapper of this project.
     * @throws AbortException When used outside of FreeStyle projects.
     */
    private AbstractSCMBridge getScmBridge(AbstractBuild<?, ?> build, BuildListener listener) throws AbortException {
        AbstractProject<?, ?> proj = build.getProject();

        if (!(build.getProject().getScm() instanceof GitSCM)) {
            throw new AbortException("Unsupported SCM type.");
        }
        GitSCM client = (GitSCM) build.getProject().getScm();

        PretestedIntegrationAsGitPluginExt pretestedGitPluginExt = client.getExtensions().get(PretestedIntegrationAsGitPluginExt.class);
        if (pretestedGitPluginExt != null) {
            GitBridge bridge = pretestedGitPluginExt.getGitBridge();
            if (bridge != null) {
                return bridge;
            } else {
                throw new AbortException("The GitBridge is not defined.. Something weird happend..");
            }
        }

        if (proj instanceof FreeStyleProject) {
            FreeStyleProject p = (FreeStyleProject) build.getProject();
            PretestedIntegrationBuildWrapper wrapper = p.getBuildWrappersList().get(PretestedIntegrationBuildWrapper.class);
            return wrapper.scmBridge;
        } else {
            throw new AbortException("Unsupported job type.");
        }
    }

    /**
     * Calls the SCM-specific function according to the chosen SCM.
     *
     * @param build    The Build
     * @param launcher The Launcher
     * @param listener The BuildListener
     * @return boolean True on success.
     * @throws InterruptedException An foreseen issue
     * @throws IOException          An foreseen IO issue
     */
    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        AbstractProject<?, ?> proj = build.getProject();
        if (!(build.getProject().getScm() instanceof GitSCM)) {
            listener.getLogger().println(LOG_PREFIX + "Unsupported SCM type: expects Git");
            return false;
        }
        GitSCM client = (GitSCM) build.getProject().getScm();

        GitBridge bridge = (GitBridge) getScmBridge(build, listener);

        if (client.getExtensions().get(PretestedIntegrationAsGitPluginExt.class) != null) {
            if (proj instanceof MatrixConfiguration) {
                listener.getLogger().println(LOG_PREFIX + "MatrixConfiguration/sub - skipping publisher - leaving it to root job");
            } else {
                listener.getLogger().println(LOG_PREFIX + "Performing pre-verified post build steps");
                try {
                    bridge.handlePostBuild(build, launcher, listener);
                } catch (NullPointerException | IllegalArgumentException e) {
                    listener.getLogger().println(String.format("Caught %s during post-checkout. Failing build.", e.getClass().getSimpleName()));
                    e.printStackTrace(listener.getLogger());
                    bridge.updateBuildDescription(build, launcher, listener);
                    throw new AbortException("Unexpected error. Trace written to log.");
                } catch (IOException e) {
                    //All our known errors are IOExceptions. Just print the message, log the error.
                    listener.getLogger().println(e.getMessage());
                    LOGGER.log(Level.SEVERE, "IOException in post checkout", e);
                    bridge.updateBuildDescription(build, launcher, listener);
                    throw new AbortException(e.getMessage());
                }
            }
            bridge.updateBuildDescription(build, launcher, listener);
            return true;
        } else { /* */
            if (proj instanceof MatrixConfiguration) {
                listener.getLogger().println(LOG_PREFIX + "MatrixConfiguration/sub - skipping publisher - leaving it to root job");
                bridge.updateBuildDescription(build, launcher, listener);
            } else {
                listener.getLogger().println(LOG_PREFIX + "Performing pre-verified post build steps");
                try {
                    bridge.handlePostBuild(build, launcher, listener);
                } catch (NullPointerException | IllegalArgumentException e) {
                    listener.getLogger().println(String.format("Caught %s during post-checkout. Failing build.", e.getClass().getSimpleName()));
                    e.printStackTrace(listener.getLogger());
                    throw new AbortException("Unexpected error. Trace written to log.");
                } catch (IOException e) {
                    //All our known errors are IOExceptions. Just print the message, log the error.
                    listener.getLogger().println(e.getMessage());
                    LOGGER.log(Level.SEVERE, "IOException in post checkout", e);
                    throw new AbortException(e.getMessage());
                }
            }

            bridge.updateBuildDescription(build, launcher, listener);
            return true;
        }

    }

    /**
     * For a matrix project, push should only happen once.
     */
    public MatrixAggregator createAggregator(MatrixBuild build, Launcher launcher, BuildListener listener) {
        return new MatrixAggregator(build, launcher, listener) {

            @Override
            public boolean endBuild() throws InterruptedException, IOException {
                return PretestedIntegrationPostCheckout.this.perform(build, launcher, listener);
            }
        };
    }


    @Override
    public void perform(Run<?, ?> run, FilePath ws, Launcher launcher, TaskListener listener) throws InterruptedException {
        Result result = run.getResult();

        String triggeredBranch = run.getAction(PretestTriggerCommitAction.class).triggerBranch.getName();
        String integrationBranch = run.getAction(PretestTriggerCommitAction.class).integrationBranch;
        String integrationRepo = run.getAction(PretestTriggerCommitAction.class).integrationRepo;
        String ucCredentialsId = run.getAction(PretestTriggerCommitAction.class).ucCredentialsId;

        //  assert triggeredBranch != null : "triggered branch must not be null";
        //  assert integrationBranch != null : "integration branch must not be null";
        //  assert integrationRepo != null : "integrationRepo must not be null";
        //

        listener.getLogger().println("Triggered branch: " + triggeredBranch);
        listener.getLogger().println("integration branhc: " + integrationBranch);
        listener.getLogger().println("repo: " + integrationRepo);
        listener.getLogger().println("uccredentials: " + ucCredentialsId);

        
        try {
            // The choice of 'jgit' or 'git'. It must be set though..
            GitClient client = Git.with(listener, run.getEnvironment(listener)).in(ws).using("git").getClient();

            if (ucCredentialsId != null) {
/* TODO: What is this for? Copied from GitSCM..
                String url = "origin"; // getParameterString(uc.getUrl(), environment);

                List<StandardUsernameCredentials> urlCredentials = CredentialsProvider.lookupCredentials(StandardUsernameCredentials.class,project,
                        ACL.SYSTEM, URIRequirementBuilder.fromUri(url).build());
                CredentialsMatcher ucMatcher = CredentialsMatchers.withId(ucCredentialsId);
                CredentialsMatcher idMatcher = CredentialsMatchers.allOf(ucMatcher, GitClient.CREDENTIALS_MATCHER);
                StandardUsernameCredentials credentials = CredentialsMatchers.firstOrNull(urlCredentials, idMatcher);
                */
                StandardUsernameCredentials credentials = CredentialsProvider.findCredentialById(ucCredentialsId, StandardUsernameCredentials.class, run, Collections.EMPTY_LIST);
                 
                if (credentials != null) {
                    listener.getLogger().println("[PREINT] found credentials");
                    client.setCredentials(credentials);
                }
            }

            if (result == null || result.isBetterOrEqualTo(GitBridge.getRequiredResult())) {
                GitBridge.pushToIntegrationBranchGit(run, listener, client, integrationRepo, integrationBranch);
                GitBridge.deleteBranch(run, listener, client, triggeredBranch, integrationRepo);
            } else {
                LOGGER.log(Level.WARNING, "Build result not satisfied - skipped post-build step.");
                listener.getLogger().println(LOG_PREFIX + "Build result not satisfied - skipped post-build step.");
            }
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Cannot launch the Git Client.." + ex);
            listener.getLogger().println(LOG_PREFIX + "Cannot launch the Git Client.." + ex);
        }
        GitBridge.updateBuildDescription(run, listener, integrationBranch, triggeredBranch.replace(integrationRepo + "/", ""));

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }

    /**
     * Descriptor Implementation for PretestedIntegrationPostCheckout
     */
    @Symbol("pretestedIntegration")
    @Extension(ordinal = Integer.MIN_VALUE)
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return "Pretested Integration publisher to push to integration branch";
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }
    }
}
