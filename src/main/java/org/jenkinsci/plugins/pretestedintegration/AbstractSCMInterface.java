package org.jenkinsci.plugins.pretestedintegration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import jenkins.model.Jenkins;

import hudson.AbortException;
import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Describable;
import hudson.model.AbstractBuild;
import hudson.model.Descriptor;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.model.Descriptor.FormException;

public abstract class AbstractSCMInterface implements Describable<AbstractSCMInterface>, ExtensionPoint {
	
	protected String branch;
	final static String LOG_PREFIX = "[PREINT-SCM] ";
	
	@DataBoundConstructor
	public AbstractSCMInterface(){
	}
	
	public String getBranch() {
		return branch;
	}
	
    public Descriptor<AbstractSCMInterface> getDescriptor() {
        return (SCMInterfaceDescriptor<?>)Jenkins.getInstance().getDescriptorOrDie(getClass());
    }

    public static DescriptorExtensionList<AbstractSCMInterface, SCMInterfaceDescriptor<AbstractSCMInterface>> all() {
        return Jenkins.getInstance().<AbstractSCMInterface, SCMInterfaceDescriptor<AbstractSCMInterface>>getDescriptorList(AbstractSCMInterface.class);
    }
    
    public static List<SCMInterfaceDescriptor<?>> getDescriptors() {
    	List<SCMInterfaceDescriptor<?>> list = new ArrayList<SCMInterfaceDescriptor<?>>();
    	for(SCMInterfaceDescriptor<?> d : all()) {
    		list.add(d);
    	}
    	return list;
    }
    
    /**
	 * This function is called after the SCM plugin has updated the workspace
	 * with remote changes. When this function has been run, the workspace must
	 * be ready to perform a build and tests. The integration branch must be
	 * checked out, and the given commit must be merged into it.
	 * 
	 * @param commit This commit represents the code that must be checked out.
	 * 
	 * @throws AbortException It is not possible to leave the workspace in a
	 * state as described above.
	 * @throws IOException A repository could not be reached.
	 * @throws InvalidArgumentException The given repository is not in a valid
	 * condition.
	 */
	public void prepareWorkspace(
			AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, Commit<?> commit)
			throws AbortException, IOException, IllegalArgumentException {
		logger.finest(LOG_PREFIX + "Entering prepareWorkspace");
		try {
			logger.finest(LOG_PREFIX + "Invoking ensureBranch with branch: " + branch);
			ensureBranch(build, launcher, listener, branch);
			logger.finest(LOG_PREFIX + "Invoking mergeChanges with commit: " + commit.getId().toString());
			mergeChanges(build, launcher, listener, commit);
		} catch (InterruptedException e){
			throw new AbortException(LOG_PREFIX + "Could not prepare workspace. Error message: "  + e.getMessage());
		}
		logger.finest(LOG_PREFIX + "Exiting prepareWorkspace");
	}
	
	protected void mergeChanges(AbstractBuild<?,?> build, Launcher launcher,TaskListener listener, Commit<?> commit) throws IOException, InterruptedException {
		//nop
	}

	protected void ensureBranch(AbstractBuild<?,?> build, Launcher launcher,TaskListener listener, String branch) throws IOException, InterruptedException {
		//nop
	}

	/**
	 * Calculate and return the next commit from the argument 
	 *
	 * @return The next pending commit. If no commit is pending null is returned.
	 *
	 * @throws IOException A repository could not be reached.
	 * @throws InvalidArgumentException The given repository is not in a valid
	 * condition.
	 */
	public Commit<?> nextCommit(
			AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, Commit<?> commit)
			throws IOException, IllegalArgumentException {
		return null;
	}
	
	public void commit(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) 
			throws IOException, InterruptedException {
		//nop
	}
	public void rollback(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) 
			throws IOException, InterruptedException {
		//nop
	}
	
	public Result getRequiredResult(){
		return Result.SUCCESS; 
	}
	
	/**
	 * This is called after the build has run. If the build was successful, the
	 * changes should be committed, otherwise the workspace is cleared as before 
	 * the changes
	 *
	 * @param build The status of the build.
	 *
	 * @throws IOException A repository could not be reached.
	 * @throws InvalidArgumentException The given repository is not in a valid
	 * condition.
	 */
	public void handlePostBuild(
			AbstractBuild<?,?> build, Launcher launcher, BuildListener listener)
			throws IOException, IllegalArgumentException {
		logger.finest(LOG_PREFIX + "Entering handlePostBuild");
		Result result = build.getResult();
		//TODO: make the success criteria configurable in post-build step
		if(result != null && result.isBetterOrEqualTo(getRequiredResult())){ //Commit the changes
			try {
				listener.getLogger().println(LOG_PREFIX + "Commiting changes");
				commit(build, launcher, listener);
			} catch (InterruptedException e) {
				throw new AbortException(LOG_PREFIX + "Commiting changes on integration branch exited unexpectedly");
			}
		} else { //Rollback changes
			try {
				listener.getLogger().println(LOG_PREFIX + "Rolling back changes");
				rollback(build, launcher, listener);
			} catch (InterruptedException e) {
				throw new AbortException(LOG_PREFIX + "Unable to revert changes in integration branch");
			}
		}
		logger.finest(LOG_PREFIX + "Exiting handlePostBuild");
	}

	private static Logger logger = Logger.getLogger(AbstractSCMInterface.class.getName());
}
