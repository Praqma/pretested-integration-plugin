package org.jenkinsci.plugins.pretestedintegration;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.Descriptor;
import hudson.model.Result;
import hudson.tasks.Publisher;
import hudson.tasks.BuildStepMonitor;
import java.io.IOException;
import java.util.logging.Logger;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * The publisher determines what will happen when the build has been run.
 * Depending on the chosen SCM, a more specific function will be called.
 */
public class PretestedIntegrationPostCheckout extends Publisher {

    @DataBoundConstructor
    public PretestedIntegrationPostCheckout() {
    }

    @Override
    public boolean needsToRunAfterFinalized() {
        return true;
    }

    /**
     * Calls the SCM-specific function according to the chosen SCM.
     *
     * @param build
     * @param launcher
     * @param listener
     *
     * @return boolean
     */
    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
        listener.getLogger().println("Performing pre-verified post build steps");
        PretestedIntegrationAction action = build.getAction(PretestedIntegrationAction.class);
        Boolean result = false;
        
        try {
            result = action.finalise(launcher, listener);
        } catch (NullPointerException e) {
            e.printStackTrace(listener.getLogger());
            build.setResult(Result.FAILURE);
        } catch (IllegalArgumentException e) {
            e.printStackTrace(listener.getLogger());
            build.setResult(Result.FAILURE);
        } catch (IOException e) {
            e.printStackTrace(listener.getLogger());
            build.setResult(Result.FAILURE);
        }
        BuildQueue.getInstance().release();
        return result;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<Publisher> {

        public String getDisplayName() {
            return "Pretested Integration post-build";
        }
    }

    private static final Logger logger = Logger.getLogger(PretestedIntegrationPostCheckout.class.getName());
}
