package org.jenkinsci.plugins.pretestedintegration;

import hudson.AbortException;
import hudson.Extension;
import hudson.Launcher;
import hudson.matrix.MatrixConfiguration;
import hudson.matrix.MatrixProject;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * The publisher determines what will happen when the build has been run.
 * Depending on the chosen SCM, a more specific function will be called.
 */
public class PretestedIntegrationPostCheckout extends Recorder {

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
    private AbstractSCMBridge getScmBridge(AbstractBuild<?, ?> build) throws AbortException {
        AbstractProject<?, ?> proj = build.getProject();
        if (proj instanceof FreeStyleProject) {
            FreeStyleProject p = (FreeStyleProject) build.getProject();
            PretestedIntegrationBuildWrapper wrapper = p.getBuildWrappersList().get(PretestedIntegrationBuildWrapper.class);
            return wrapper.scmBridge;
        } else if (proj instanceof MatrixConfiguration) {
            MatrixProject p = ((MatrixConfiguration) proj).getParent();
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
        listener.getLogger().println(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Performing pre-verified post build steps");
        try {
            getScmBridge(build).handlePostBuild(build, launcher, listener);
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

        return true;
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
