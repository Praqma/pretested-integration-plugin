package org.jenkinsci.plugins.pretestedintegration;

import com.tikal.jenkins.plugins.multijob.MultiJobProject;
import hudson.AbortException;
import hudson.Extension;
import hudson.Launcher;
import hudson.matrix.*;
import hudson.model.*;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.Revision;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import java.io.IOException;
import java.io.Serializable;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import hudson.util.DescribableList;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.pretestedintegration.exceptions.IntegrationFailedException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.NothingToDoException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.UnsupportedConfigurationException;
import org.jenkinsci.plugins.pretestedintegration.scm.git.GitBridge;
import org.jenkinsci.plugins.pretestedintegration.scm.git.GitIntegrationStrategy;
import org.jenkinsci.plugins.pretestedintegration.scm.git.PretestedIntegrationAsGitPluginExt;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * The publisher determines what will happen when the build has been run.
 * Depending on the chosen SCM, a more specific function will be called.
 */
public class PretestedIntegrationPostCheckout extends Recorder implements Serializable, MatrixAggregatable {

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

        GitSCM client = (GitSCM)build.getProject().getScm();
        if ( ! ( client instanceof GitSCM ) ) {
            throw new AbortException("Unsupported SCM type.");
        }

        PretestedIntegrationAsGitPluginExt pretestedGitPluginExt = client.getExtensions().get(PretestedIntegrationAsGitPluginExt.class) ;
        if ( pretestedGitPluginExt != null ) {
            if (proj instanceof FreeStyleProject ) {
                FreeStyleProject p = (FreeStyleProject) build.getProject();
                PretestedIntegrationBuildWrapper wrapper = p.getBuildWrappersList().get(PretestedIntegrationBuildWrapper.class);
                if ( wrapper != null ) {
                    listener.getLogger().println(PretestedIntegrationBuildWrapper.LOG_PREFIX + "BuildWrapper also configured,but skip as GitExtension prevails");
                }
            } else if (proj instanceof MultiJobProject ) {
                MultiJobProject p = (MultiJobProject) build.getProject();
                PretestedIntegrationBuildWrapper wrapper = p.getBuildWrappersList().get(PretestedIntegrationBuildWrapper.class);
                if ( wrapper != null ) {
                    listener.getLogger().println(PretestedIntegrationBuildWrapper.LOG_PREFIX + "BuildWrapper also configured, but skip as GitExtension prevails");
                }
            }
            return pretestedGitPluginExt.getGitBridge();
        }

        if (proj instanceof FreeStyleProject ) {
            FreeStyleProject p = (FreeStyleProject) build.getProject();
            PretestedIntegrationBuildWrapper wrapper = p.getBuildWrappersList().get(PretestedIntegrationBuildWrapper.class);
            return wrapper.scmBridge;
        } else if (proj instanceof MatrixProject) {
            MatrixProject p = (MatrixProject)proj.getParent();
            PretestedIntegrationBuildWrapper wrapper = p.getBuildWrappersList().get(PretestedIntegrationBuildWrapper.class);
            return wrapper.scmBridge;
        } else if (proj instanceof MultiJobProject ) {
            MultiJobProject p = (MultiJobProject)proj.getParent();
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
        GitSCM client = (GitSCM)build.getProject().getScm();
        if ( ! (client instanceof GitSCM) || client == null ) {
            listener.getLogger().println(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Unsupported SCM type: expects Git");
            return false;
        }

        GitBridge bridge = (GitBridge)getScmBridge(build, listener);

        if ( client.getExtensions().get(PretestedIntegrationAsGitPluginExt.class ) != null ) {
            if (proj instanceof MatrixConfiguration) {
                listener.getLogger().println(PretestedIntegrationBuildWrapper.LOG_PREFIX + "MatrixConfiguration/sub - skipping publisher - leaving it to root job");
            } else {
                listener.getLogger().println(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Performing pre-verified post build steps");
                try {
                    bridge.handlePostBuild(build, launcher, listener);
                } catch (NullPointerException | IllegalArgumentException e) {
                    listener.getLogger().println(String.format("Caught %s during post-checkout. Failing build.", e.getClass().getSimpleName()));
                    e.printStackTrace(listener.getLogger());
                    bridge.setResultInfo("Unknown");
                    bridge.updateBuildDescription((Run)build);
                    throw new AbortException("Unexpected error. Trace written to log.");
                } catch (IOException e) {
                    //All our known errors are IOExceptions. Just print the message, log the error.
                    listener.getLogger().println(e.getMessage());
                    LOGGER.log(Level.SEVERE, "IOException in post checkout", e);
                    bridge.setResultInfo("Unknown");
                    bridge.updateBuildDescription(build, launcher, listener);
                    throw new AbortException(e.getMessage());
                }
            }
            if ( build.getResult() == Result.SUCCESS ) {
                bridge.setResultInfo("Ok");
            }
            bridge.updateBuildDescription((Run)build);
            return true;
        } else { /* */
            if (proj instanceof MatrixConfiguration) {
                listener.getLogger().println(PretestedIntegrationBuildWrapper.LOG_PREFIX + "MatrixConfiguration/sub - skipping publisher - leaving it to root job");
                bridge.setResultInfo("Ok");
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
            bridge.setResultInfo("Ok");
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
            return "Pretested Integration Publisher";
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
