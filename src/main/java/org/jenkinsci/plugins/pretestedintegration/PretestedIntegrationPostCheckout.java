package org.jenkinsci.plugins.pretestedintegration;

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import hudson.*;
import hudson.matrix.*;
import hudson.model.*;
import hudson.plugins.git.*;
import hudson.scm.SCM;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.Writer;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import jenkins.tasks.SimpleBuildStep;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.plugins.gitclient.*;
import org.jenkinsci.plugins.pretestedintegration.scm.git.AccumulatedCommitStrategy;
import org.jenkinsci.plugins.pretestedintegration.scm.git.GitBridge;
import org.jenkinsci.plugins.pretestedintegration.scm.git.PretestTriggerCommitAction;
import org.jenkinsci.plugins.pretestedintegration.scm.git.PretestedIntegrationAsGitPluginExt;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.CheckForNull;

/**
 * The publisher determines what will happen when the build has been run.
 * Depending on the chosen SCM, a more specific function will be called.
 */
public class PretestedIntegrationPostCheckout extends Recorder implements Serializable, MatrixAggregatable, SimpleBuildStep {

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

        if ( ! ( build.getProject().getScm() instanceof GitSCM ) ) {
            throw new AbortException("Unsupported SCM type.");
        }
        GitSCM client = (GitSCM)build.getProject().getScm();

        PretestedIntegrationAsGitPluginExt pretestedGitPluginExt = client.getExtensions().get(PretestedIntegrationAsGitPluginExt.class) ;
        if ( pretestedGitPluginExt != null ) {
            GitBridge bridge = pretestedGitPluginExt.getGitBridge();
            if ( bridge != null ) {
                return bridge;
            } else {
                throw new AbortException("The GitBridge is not defined.. Something weird happend..");
            }
        }

        if (proj instanceof FreeStyleProject ) {
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
     * @param build The Build
     * @param launcher The Launcher
     * @param listener The BuildListener
     * @return boolean True on success.
     * @throws InterruptedException
     * @throws IOException
     */
    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        AbstractProject<?, ?> proj = build.getProject();
        if ( ! ( build.getProject().getScm() instanceof GitSCM) ) {
            listener.getLogger().println(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Unsupported SCM type: expects Git");
            return false;
        }
        GitSCM client = (GitSCM)build.getProject().getScm();

        GitBridge bridge = (GitBridge)getScmBridge(build, listener);

        if ( client.getExtensions().get(PretestedIntegrationAsGitPluginExt.class ) != null ) {
            if (proj instanceof MatrixConfiguration) {
                listener.getLogger().println(PretestedIntegrationBuildWrapper.LOG_PREFIX + "MatrixConfiguration/sub - skipping publisher - leaving it to root job");
            } else {
                listener.getLogger().println(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Performing pre-verified post build steps");
                try {
                    bridge.handlePostBuild(build, launcher, listener );
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
                listener.getLogger().println(PretestedIntegrationBuildWrapper.LOG_PREFIX + "MatrixConfiguration/sub - skipping publisher - leaving it to root job");
                bridge.updateBuildDescription(build, launcher, listener);
            } else {
                listener.getLogger().println(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Performing pre-verified post build steps");
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
        return new MatrixAggregator(build,launcher,listener) {

            @Override
            public boolean endBuild() throws InterruptedException, IOException {
                return PretestedIntegrationPostCheckout.this.perform(build,launcher,listener);
            }
        };
    }

    @Override
    public void perform(Run<?,?> run, FilePath ws, Launcher launcher, TaskListener listener) throws InterruptedException {
        Result result = run.getResult();

        String triggeredBranch = run.getAction(PretestTriggerCommitAction.class).triggerBranch.getName();

        GitBridge bridge = new GitBridge(new AccumulatedCommitStrategy(),"masterPipe","origin");

        try {
            // TODO: Credentials
            GitClient client = new GitSCM("origin").createClient(listener,run.getEnvironment(listener),run,ws);

             if (result == null || result.isBetterOrEqualTo(result.SUCCESS)) {
                bridge.pushToIntegrationBranchGit(run,listener,client);
                bridge.deleteBranch(run,listener,client,triggeredBranch,"origin");
            } else {
                LOGGER.log(Level.WARNING, "Build result not satisfied - skipped post-build step.");
                listener.getLogger().println(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Build result not satisfied - skipped post-build step.");
            }
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Cannot launch the Git Client.." + ex);
            listener.getLogger().println(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Cannot launch the Git Client.." + ex);
        }
        bridge.updateBuildDescription(run, listener);

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
    @Extension(ordinal = Integer.MIN_VALUE)
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return "Praqma Git Phlow - Update remote repository";
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
