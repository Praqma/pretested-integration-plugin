package org.jenkinsci.plugins.pretestedintegration;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.Descriptor;
import hudson.tasks.Publisher;
import hudson.tasks.BuildStepMonitor;
import java.io.IOException;
import org.jenkinsci.plugins.pretestedintegration.CommitQueue;
import org.jenkinsci.plugins.pretestedintegration.scminterface
		.PretestedIntegrationSCMInterface;

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
		try {
			PretestUtils.logMessage(listener, "Beginning post-build step");
			//hasQueue = true;
			
			// Get the interface for the SCM according to the chosen SCM
			PretestedIntegrationSCMInterface scmInterface =
					PretestUtils.getScmInterface(build, launcher, listener);
			if(scmInterface == null) {
				return false;
			}
			
			// Do it!
			listener.getLogger().println("Invoking postbuild step");
			scmInterface.handlePostBuild(build, launcher, listener);
			
			//CommitQueue.getInstance().release();
			//hasQueue = false;
			
			PretestUtils.logMessage(listener, "Finished post-build step");
		} catch(IOException e) {
			if(hasQueue) {
				CommitQueue.getInstance().release();
			}
			throw(e);
			//return false;
		} catch(Exception e) {
			if(hasQueue) {
				CommitQueue.getInstance().release();
			}
			e.printStackTrace();
			return false;
		}
		return true;
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
