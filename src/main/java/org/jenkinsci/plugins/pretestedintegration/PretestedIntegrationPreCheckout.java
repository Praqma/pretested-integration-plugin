package org.jenkinsci.plugins.pretestedintegration;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import java.io.IOException;
import java.util.List;

import net.sf.json.JSONObject;

import org.jenkinsci.plugins.pretestedintegration.scminterface.PretestedIntegrationSCMInterface;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

//import org.jenkinsci.plugins.pretestedintegration.scminterface.mercurial.HgUtils;

/**
 * The build wrapper determines what will happen before the build will run.
 * Depending on the chosen SCM, a more specific function will be called.
 */
public class PretestedIntegrationPreCheckout extends BuildWrapper {
	
	private static final String DISPLAY_NAME = "Use pretested integration";
	private static final String PLUGIN_NAME = "pretested-integration";
	
	private boolean hasQueue;
	
	private AbstractSCMInterface asi;
	private BuildListener listener;
	
	@DataBoundConstructor
	public PretestedIntegrationPreCheckout(AbstractSCMInterface asi) {
		this.asi = asi;
	}
	
	public AbstractSCMInterface getAsi(){
		return this.asi;
	}
	
	/**
	 * Jenkins hook that fires after the workspace is initialized.
	 * Calls the SCM-specific function according to the chosen SCM.
	 * 
	 * @param build
	 * @param launcher
	 * @param listener
	 * @return 
	 */
	@Override
	public Environment setUp(AbstractBuild build, Launcher launcher,
			BuildListener listener) throws IOException, InterruptedException {
		
		this.listener = listener;

		PretestUtils.logMessage(listener, "Beginning pre-build step");
		
		// Wait in line until no other jobs are running.
		hasQueue = true;
		
		// Get the interface for the SCM according to the chosen SCM
		PretestedIntegrationSCMInterface scmInterface =
				PretestUtils.getScmInterface(build, launcher, listener);
		if(scmInterface == null) {
			return null;
		}
		
		PretestUtils.logMessage(listener, "begin has next");
		// Verify that there is anything to do
		boolean hasNextCommit = scmInterface.hasNextCommit(build, launcher, listener);
		listener.getLogger().println("Has next commit? " + hasNextCommit);
		if(!hasNextCommit) {
			PretestUtils.logMessage(listener, "Nothing to build. Aborting.");
			return null;
		}
		PretestUtils.logMessage(listener, "end has next");
		
		// Prepare for build
		scmInterface.prepareWorkspace(build, launcher, listener,
				scmInterface.popCommit(build, launcher, listener));
		
		PretestUtils.logMessage(listener, "Finished pre-build step");
		
		Environment environment = new PretestEnvironment();
		return environment;
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
		if(Hudson.getInstance().getPlugin(PLUGIN_NAME) != null) {
			PretestUtils.logMessage(listener, PLUGIN_NAME + " plugin version: "
					+ Hudson.getInstance().getPlugin(PLUGIN_NAME)
					.getWrapper().getVersion());
		} else {
			PretestUtils.logMessage(listener, "No plugin found with name "
					+ PLUGIN_NAME);
		}
	}
	
	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl)super.getDescriptor();
	}
	
	@Extension
	public static class DescriptorImpl extends BuildWrapperDescriptor {
		
		public String getDisplayName() {
			return "Use pretested integration";
		}
		
		public BuildWrapper newInstance(StaplerRequest req, JSONObject formData) throws FormException {
			PretestedIntegrationPreCheckout b = (PretestedIntegrationPreCheckout) super.newInstance(req,formData);
			SCMInterfaceDescriptor<AbstractSCMInterface> d = (SCMInterfaceDescriptor<AbstractSCMInterface>) b.getAsi().getDescriptor();
			b.asi = d.newInstance(req, formData);
			save();
			return b;
		}
		
		public List<SCMInterfaceDescriptor<?>>getSCMs(){
			return AbstractSCMInterface.getDescriptors();
		}
		
		@Override
		public boolean isApplicable(AbstractProject<?, ?> arg0) {
			return true;
		}
	}
	
	class PretestEnvironment extends Environment {
	}
}
