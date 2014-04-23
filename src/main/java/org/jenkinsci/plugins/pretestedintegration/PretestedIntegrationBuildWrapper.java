package org.jenkinsci.plugins.pretestedintegration;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Describable;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * The build wrapper determines what will happen before the build will run.
 * Depending on the chosen SCM, a more specific function will be called.
 */
public class PretestedIntegrationBuildWrapper extends BuildWrapper {

    private static final String LOG_PREFIX = "[PREINT] ";
    public final AbstractSCMBridge scmBridge;

    @DataBoundConstructor
    public PretestedIntegrationBuildWrapper(final AbstractSCMBridge scmBridge) {
        this.scmBridge = scmBridge;
    }

    /**
     * Jenkins hook that fires after the workspace is initialized. Calls the
     * SCM-specific function according to the chosen SCM.
     *
     * @param build
     * @param launcher
     * @param listener
     * @return
     */
    @Override
    public BuildWrapper.Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) {
        logger.finest("Entering setUp");

        //There can be only one... at a time
        BuildQueue.getInstance().enqueueAndWait();

        PretestedIntegrationAction action;
        try {
            action = new PretestedIntegrationAction(build, launcher, listener, scmBridge);            
            build.addAction(action);
            boolean result = action.initialise(launcher, listener);

            if (!result) {
                logger.finest("Set result to NOT_BUILT");
                listener.getLogger().println(LOG_PREFIX + "Nothing to do, setting result to NOT_BUILT");
                build.setResult(Result.NOT_BUILT);
            }

            try {
                ensurePublisher(build);
            } catch (IOException e) {
                e.printStackTrace(listener.getLogger());
                BuildQueue.getInstance().release();
            }

            logger.finest("Exiting setUp");
            listener.getLogger().println(LOG_PREFIX + "Building commit: " + action.getClass());

        } catch (IllegalArgumentException e) {
            e.printStackTrace(listener.getLogger());
            build.setResult(Result.FAILURE);
            BuildQueue.getInstance().release();
        } catch (IOException e) {
            e.printStackTrace(listener.getLogger());
            build.setResult(Result.FAILURE);
            BuildQueue.getInstance().release();
        }

        BuildWrapper.Environment environment = new PretestEnvironment();
        return environment;
    }

    public void ensurePublisher(AbstractBuild<?, ?> build) throws IOException {
        Describable<?> describable = build.getProject().getPublishersList().get(PretestedIntegrationPostCheckout.class);
        if (describable == null) {
            logger.info("Adding publisher to project");
            build.getProject().getPublishersList().add(new PretestedIntegrationPostCheckout());
        }
    }

    /**
     * Prints out version information.
     *
     * @param build
     * @param launcher
     * @param listener
     */
    //@Override
    public void preCheckout() throws IOException, InterruptedException {
        //nop
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static class DescriptorImpl extends BuildWrapperDescriptor {

        public DescriptorImpl() {
            load();
        }

        public String getDisplayName() {
            return "Use pretested integration";
        }

        public List<SCMBridgeDescriptor<?>> getSCMBridges() {
            return AbstractSCMBridge.getDescriptors();
        }

        @Override
        public boolean isApplicable(AbstractProject<?, ?> arg0) {
            return true;
        }
    }

    class PretestEnvironment extends BuildWrapper.Environment {
    }

    private static final Logger logger = Logger.getLogger(PretestedIntegrationBuildWrapper.class.getName());
}
