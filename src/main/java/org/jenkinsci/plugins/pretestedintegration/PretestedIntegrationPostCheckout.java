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
import java.util.logging.Level;
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

    /**
     * This should ensure that we only run, when the build result can no longer be changes (is final). 
     * @return 
     */
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
    public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) {
        PretestedIntegrationAction action = build.getAction(PretestedIntegrationAction.class);
        if (action == null)
            return true;

        listener.getLogger().println("Performing pre-verified post build steps");
        Boolean result = false;

        try {
            result = action.finalise(launcher, listener);
        } catch (NullPointerException e) {
            e.printStackTrace(listener.getLogger());
            build.setResult(Result.FAILURE);
            logger.log(Level.SEVERE, "IO Exception caught", e);
        } catch (IllegalArgumentException e) {
            e.printStackTrace(listener.getLogger());
            build.setResult(Result.FAILURE);
            logger.log(Level.SEVERE, "IllegalArgumentException in post checkout", e);
        } catch (IOException e) {
            //All our know errors are IOExceptions. Just print the message...but log the error
            listener.getLogger().println(e.getMessage());
            logger.log(Level.SEVERE, "IO Exception in post checkout", e);
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
