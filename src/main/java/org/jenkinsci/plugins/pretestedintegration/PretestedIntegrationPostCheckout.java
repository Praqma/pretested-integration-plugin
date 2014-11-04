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

    @Override
    public boolean needsToRunAfterFinalized() {
        logger.entering("PretestedIntegrationPostCheckout",
				"needsToRunAfterFinalized");// Generated code DONT TOUCH! Bookmark: eb726c44daeb157789f42c450477042e
		logger.exiting("PretestedIntegrationPostCheckout",
				"needsToRunAfterFinalized");// Generated code DONT TOUCH! Bookmark: 4afd6d3a46055d0c0e5bb180a6ecb84a
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
        logger.entering("PretestedIntegrationPostCheckout", "perform",
				new Object[] { build, listener, launcher });// Generated code DONT TOUCH! Bookmark: f0df32c5903984db7bd92f79a94b81ee
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
        logger.exiting("PretestedIntegrationPostCheckout", "perform");// Generated code DONT TOUCH! Bookmark: 7a7a9c1108c955514dcf240fb6bf7b4d
		return result;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        logger.entering("PretestedIntegrationPostCheckout",
				"getRequiredMonitorService");// Generated code DONT TOUCH! Bookmark: 65600bc5fd3617002e9482b8a182671f
		logger.exiting("PretestedIntegrationPostCheckout",
				"getRequiredMonitorService");// Generated code DONT TOUCH! Bookmark: ae100df987b40982a3d2efcc0e2b2578
		return BuildStepMonitor.BUILD;
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<Publisher> {

        private final static Logger logger = Logger
				.getLogger(DescriptorImpl.class.getName());// Generated code DONT TOUCH! Bookmark: 3ca61d8e671737b5ead8aaccd31875c4

		public String getDisplayName() {
            return "Pretested Integration post-build";
        }
    }

    private static final Logger logger = Logger.getLogger(PretestedIntegrationPostCheckout.class.getName());
}
