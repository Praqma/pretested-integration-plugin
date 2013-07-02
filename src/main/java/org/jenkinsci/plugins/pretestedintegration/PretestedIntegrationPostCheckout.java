package org.jenkinsci.plugins.pretestedintegration;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.Descriptor;
import hudson.tasks.Publisher;
import hudson.tasks.BuildStepMonitor;
import java.io.IOException;

import org.kohsuke.stapler.DataBoundConstructor;


/**
 * The publisher determines what will happen when the build has been run.
 * Depending on the chosen SCM, a more specific function will be called.
 */
public class PretestedIntegrationPostCheckout extends Publisher {
	
	private static final String DISPLAY_NAME =
			"Run pretested integration post-build step";
	
	private boolean hasQueue;
	
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
	public boolean perform(AbstractBuild build, Launcher launcher,
			BuildListener listener) throws IOException {
		listener.getLogger().println("Perform invoked");
		
		PretestedIntegrationAction action = build.getAction(PretestedIntegrationAction.class);
		return action.finalise();
	}
	
	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.BUILD;
	}
	
	@Extension
	public static final class DescriptorImpl extends Descriptor<Publisher> {
		public String getDisplayName() {
			return DISPLAY_NAME;
		}
	}
}
