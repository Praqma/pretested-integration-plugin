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

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 * The build wrapper determines what will happen before the build will run.
 * Depending on the chosen SCM, a more specific function will be called.
 */
public class PretestedIntegrationBuildWrapper extends BuildWrapper {
	
	private static String LOG_PREFIX = "[PREINT] ";
	private AbstractSCMBridge scmBridge;
	
	@DataBoundConstructor
	public PretestedIntegrationBuildWrapper(AbstractSCMBridge scmBridge){
		this.scmBridge = scmBridge;
	}
	
	public AbstractSCMBridge getScmInterface(){
		return this.scmBridge;
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
			BuildListener listener) {
		logger.finest("Entering setUp");
		
		//There can be only one... at a time
		BuildQueue.getInstance().enqueueAndWait();
		
		PretestedIntegrationAction action;
		try {
			action = new PretestedIntegrationAction(build, launcher, listener, scmBridge);

			build.addAction(action);
			boolean result = action.initialise(launcher, listener);
			if(!result) {
				logger.finest("Set result to NOT_BUILT");
				listener.getLogger().println(LOG_PREFIX + "Nothing to do, setting result to NOT_BUILT");
				build.setResult(Result.NOT_BUILT);
			}

			try {
				ensurePublisher(build);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				logger.finest("Could not add publisher" + e.getMessage());
				BuildQueue.getInstance().release();
			}
			
			logger.finest("Exiting setUp");
			listener.getLogger().println(LOG_PREFIX + "Building commit: " + action.getClass());
			
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			logger.finest("An exeption occured while settings up the build." + e.getMessage());
			build.setResult(Result.FAILURE);
			BuildQueue.getInstance().release();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			logger.finest("An exeption occured while settings up the build." + e.getMessage());
			build.setResult(Result.FAILURE);
			BuildQueue.getInstance().release();
		}
		
		Environment environment = new PretestEnvironment();
		return environment;
	}
	
	public void ensurePublisher(AbstractBuild<?,?> build) throws IOException {
		Describable<?> describable = build.getProject().getPublishersList().get(PretestedIntegrationPostCheckout.class);
		if(describable == null) {
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
		return (DescriptorImpl)super.getDescriptor();
	}
	
	@Extension
	public static class DescriptorImpl extends BuildWrapperDescriptor {
		
		public String getDisplayName() {
			return "Use pretested integration";
		}
		
		public BuildWrapper newInstance(StaplerRequest req, JSONObject formData) throws FormException {
			PretestedIntegrationBuildWrapper b = (PretestedIntegrationBuildWrapper) super.newInstance(req,formData);
			
			SCMBridgeDescriptor<AbstractSCMBridge> d = (SCMBridgeDescriptor<AbstractSCMBridge>) b.getScmInterface().getDescriptor();
			b.scmBridge = d.newInstance(req, formData);

			save();
			return b;
		}

		public List<SCMBridgeDescriptor<?>>getSCMBridges(){
			return AbstractSCMBridge.getDescriptors();
		}
		
		@Override
		public boolean isApplicable(AbstractProject<?, ?> arg0) {
			return true;
		}
	}
	
	class PretestEnvironment extends Environment {
	}
	
	private static Logger logger = Logger.getLogger(PretestedIntegrationBuildWrapper.class.getName());
}
