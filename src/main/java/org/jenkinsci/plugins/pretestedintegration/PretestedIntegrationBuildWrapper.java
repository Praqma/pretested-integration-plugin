package org.jenkinsci.plugins.pretestedintegration;

import hudson.Extension;
import hudson.Launcher;
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
import org.jenkinsci.plugins.pretestedintegration.exceptions.EstablishWorkspaceException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.IntegrationFailedException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.NothingToDoException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.UnsupportedConfigurationException;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * The build wrapper determines what will happen before the build will run.
 * Depending on the chosen SCM, a more specific function will be called.
 */
public class PretestedIntegrationBuildWrapper extends BuildWrapper {

    private static final Logger LOGGER = Logger.getLogger(PretestedIntegrationBuildWrapper.class.getName());
    public static final String LOG_PREFIX = "[PREINT] ";
    public final AbstractSCMBridge scmBridge;

    @DataBoundConstructor
    public PretestedIntegrationBuildWrapper(final AbstractSCMBridge scmBridge) {
        this.scmBridge = scmBridge;
    }

    /**
     * Jenkins hook that fires after the workspace is initialised. Calls the
     * SCM-specific function according to the chosen SCM.
     *
     * @param build
     * @param launcher
     * @param listener
     * @return
     */
    @Override
    public BuildWrapper.Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) {
        boolean proceedToBuildStep = true;
        try {
            scmBridge.validateConfiguration(build.getProject());
            scmBridge.isApplicable(build, listener);
            scmBridge.ensureBranch(build, launcher, listener, scmBridge.getExpandedBranch(build.getEnvironment(listener)));
            scmBridge.prepareWorkspace(build, launcher, listener);
        } catch (NothingToDoException e) {
            build.setResult(Result.NOT_BUILT);
            listener.getLogger().println(e.getMessage());
            LOGGER.log(Level.SEVERE, LOG_PREFIX + "- setUp()-NothingToDoException", e);
            proceedToBuildStep = false;
        } catch (IntegrationFailedException | EstablishWorkspaceException | UnsupportedConfigurationException e) {
            build.setResult(Result.FAILURE);
            listener.getLogger().println(e.getMessage());
            LOGGER.log(Level.SEVERE, String.format("%s - setUp()- %s", LOG_PREFIX, e.getClass().getSimpleName()), e);
            proceedToBuildStep = false;
        } catch (IOException | InterruptedException ex) {
            build.setResult(Result.FAILURE);
            listener.getLogger().println(String.format("%sUnexpected error. Check log for details", LOG_PREFIX));
            ex.printStackTrace(listener.getLogger());
            LOGGER.log(Level.SEVERE, LOG_PREFIX + "- setUp() - Unexpected error", ex);
            proceedToBuildStep = false;
        }

        BuildWrapper.Environment environment = new PretestEnvironment();
        return proceedToBuildStep ? environment : null;
    }

    /**
     * Prints out version information.
     *
     * @throws java.io.IOException
     * @throws java.lang.InterruptedException
     */
    public void preCheckout() throws IOException, InterruptedException {}

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static class DescriptorImpl extends BuildWrapperDescriptor {

        public DescriptorImpl() {
            load();
        }

        @Override
        public String getDisplayName() {
            return "Use pretested integration";
        }

        public List<SCMBridgeDescriptor<?>> getSCMBridges() {
            return AbstractSCMBridge.getDescriptors();
        }

        @Override
        public boolean isApplicable(AbstractProject<?, ?> arg0) {
            return arg0 instanceof FreeStyleProject;
        }
    }

    class PretestEnvironment extends BuildWrapper.Environment {
    }
}
