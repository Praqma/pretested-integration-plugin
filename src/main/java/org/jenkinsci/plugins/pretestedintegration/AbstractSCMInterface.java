package org.jenkinsci.plugins.pretestedintegration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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

public abstract class AbstractSCMInterface implements Describable<AbstractSCMInterface>, ExtensionPoint {
	
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
	public abstract void prepareWorkspace(
			AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, String branch, Commit<?> commit)
			throws AbortException, IOException, IllegalArgumentException;
	
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
	
	public abstract void commit(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException;
	public abstract void rollback(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException;
	
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
	void handlePostBuild(
			AbstractBuild<?,?> build, Launcher launcher, BuildListener listener)
			throws IOException, IllegalArgumentException {
		Result result = build.getResult();
		//TODO: make the success criteria configurable in post-build step
		if(result != null && result.isBetterOrEqualTo(Result.SUCCESS)){ //Commit the changes
			
			try {
				commit(build, launcher, listener);
				//hg(build, launcher, listener,"commit","-m", "Successfully integrated development branch");
			} catch (InterruptedException e) {
				throw new AbortException("Commiting changes on integration branch exited unexpectedly");
			}
		} else { //Rollback changes
			try {
				rollback(build, launcher, listener);
				//hg(build, launcher, listener, "update","-C");
			} catch (InterruptedException e) {
				throw new AbortException("Unable to revert changes in integration branch");
			}
		}
	}
}
