package org.jenkinsci.plugins.pretestedintegration;

import hudson.Extension;
import hudson.Launcher;
import hudson.Plugin;
import hudson.matrix.MatrixProject;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.pretestedintegration.exceptions.EstablishingWorkspaceFailedException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.IntegrationFailedException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.NothingToDoException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.UnsupportedConfigurationException;
import org.jenkinsci.plugins.pretestedintegration.scm.git.GitBridge;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * The build wrapper determines what will happen before the build will run.
 * Depending on the chosen SCM, a more specific function will be called.
 */
public class PretestedIntegrationBuildWrapper extends BuildWrapper {

    private static final Logger LOGGER = Logger.getLogger(PretestedIntegrationBuildWrapper.class.getName());

    /**
     * A prefix added to logging to facilitate debugging from logs.
     */
    public static final String LOG_PREFIX = "[PREINT] ";

    /**
     * The SCM Bridge used for this project.
     */
    public final AbstractSCMBridge scmBridge;

    /**
     * Constructor for the Build Wrapper.
     * DataBound to work with Jenkins UI.
     * @param scmBridge the SCM bridge
     */
    @DataBoundConstructor
    public PretestedIntegrationBuildWrapper(final GitBridge scmBridge) {
        this.scmBridge = scmBridge;
    }

    /**
     * Jenkins hook that fires after the workspace has been initialized.
     * Calls the SCM specific function according to the chosen SCM.
     *
     * @param build - The build in progress for which an BuildWrapper.Environment object is created. Never null.
     * @param launcher - This launcher can be used to launch processes for this build. If the build runs remotely, launcher will also run a job on that remote machine. Never null.
     * @param listener - Can be used to send any message.
     * @return non-null if the build can continue, null if there was an error and the build needs to be aborted.
     */
    @Override
    public BuildWrapper.Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) {
        listener.getLogger().println(String.format("%s Pretested Integration Plugin v%s", LOG_PREFIX, getVersion()));
        boolean proceedToBuildStep = true;
        try {
            scmBridge.validateConfiguration(build.getProject());
            scmBridge.isApplicable(build, listener);
            scmBridge.ensureBranch(build, launcher, listener, scmBridge.getExpandedIntegrationBranch(build.getEnvironment(listener)));
            scmBridge.prepareWorkspace(build, launcher, listener);
        } catch (NothingToDoException e) {
            build.setResult(Result.NOT_BUILT);
            String logMessage = LOG_PREFIX + String.format("%s - setUp() - NothingToDoException - %s", LOG_PREFIX, e.getMessage());
            listener.getLogger().println(logMessage);
            LOGGER.log(Level.SEVERE, logMessage, e);
            proceedToBuildStep = true;
        } catch (IntegrationFailedException | EstablishingWorkspaceFailedException | UnsupportedConfigurationException e) {
            build.setResult(Result.UNSTABLE);
            String logMessage = String.format("%s - setUp() - %s - %s", LOG_PREFIX, e.getClass().getSimpleName(), e.getMessage());
            listener.getLogger().println(logMessage);
            LOGGER.log(Level.SEVERE, logMessage, e);
            proceedToBuildStep = false;
        } catch (IOException | InterruptedException e) {
            build.setResult(Result.FAILURE);
            String logMessage = String.format("%s - Unexpected error. %n%s", LOG_PREFIX, e.getMessage());
            LOGGER.log(Level.SEVERE, logMessage, e);
            listener.getLogger().println(logMessage);
            e.printStackTrace(listener.getLogger());
            proceedToBuildStep = false;
        }

        BuildWrapper.Environment environment = new PretestEnvironment();
        return proceedToBuildStep ? environment : null;
    }

    /**
    * {@inheritDoc}
    */
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    /**
     * @return the plugin version
     */
    public String getVersion(){
        Plugin pretested = Jenkins.getInstance().getPlugin("pretested-integration");
        if (pretested != null) return pretested.getWrapper().getVersion();
        else return "unable to retrieve plugin version";
    }

    /**
     * A singleton Descriptor for every concrete BuildWrapper implementation.
     */
    @Extension
    public static class DescriptorImpl extends BuildWrapperDescriptor {

        /**
         * Constructor for the Descriptor
         */
        public DescriptorImpl() {
            load();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return "Use pretested integration";
        }

        /**
         * @return Descriptors for the SCM Bridge
         */
        public List<SCMBridgeDescriptor<?>> getSCMBridges() {
            return AbstractSCMBridge.getDescriptors();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isApplicable(AbstractProject<?, ?> arg0) {
            if (arg0 instanceof FreeStyleProject)
                return true;
            if (arg0 instanceof MatrixProject)
                return true;
            return false;
        }
    }

    /**
     * Custom Environment used with Pretested Integration.
     * Currently just a placeholder implementation.
     */
    class PretestEnvironment extends BuildWrapper.Environment {
    }
}
