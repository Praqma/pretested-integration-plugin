/**
 * 
 */
package org.jenkinsci.plugins.pretestedintegration.scminterface.mercurial;

import hudson.AbortException;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.util.ArgumentListBuilder;

import java.io.IOException;
import java.util.Dictionary;

import org.jenkinsci.plugins.pretestedintegration.HgUtils;
import org.jenkinsci.plugins.pretestedintegration.PretestUtils;
import org.jenkinsci.plugins.pretestedintegration.scminterface.PretestedIntegrationSCMCommit;
import org.jenkinsci.plugins.pretestedintegration.scminterface.PretestedIntegrationSCMInterface;

/**
 * Implementation of PretestedIntegrationSCMInterface.
 * This class adds integration support for mercurial SCM
 * @author rel
 *
 */
public class PretestedIntegrationSCMMercurial implements
		PretestedIntegrationSCMInterface {

	/**
	 * Checkout 
	 */
	public void prepareWorkspace(AbstractBuild build, Launcher launcher,
			BuildListener listener, PretestedIntegrationSCMCommit commit)
			throws AbortException, IOException, IllegalArgumentException {
		try {
			//Make sure that we are on the integration branch
			//TODO: Make it dynamic and not just "default"
			HgUtils.runScmCommand(build, launcher, listener, 
					new String[]{"update","default"});
			
			//Merge the commit into the integration branch
			HgUtils.runScmCommand(build, launcher, listener,
				new String[]{"merge", commit.getId(),"--tool","internal:merge"});
		} catch(AbortException e) {
			PretestUtils.logError(listener, "Could not merge commit: " + commit.getId());
			throw e;
		} catch(InterruptedException e){
			//wth?
		}
		
	}

	/* (non-Javadoc)
	 * @see org.jenkinsci.plugins.pretestedintegration.scminterface.PretestedIntegrationSCMInterface#hasNextCommit(hudson.model.AbstractBuild, hudson.Launcher, hudson.model.BuildListener)
	 */
	public boolean hasNextCommit(AbstractBuild build, Launcher launcher,
			BuildListener listener) throws IOException,
			IllegalArgumentException {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see org.jenkinsci.plugins.pretestedintegration.scminterface.PretestedIntegrationSCMInterface#popCommit(hudson.model.AbstractBuild, hudson.Launcher, hudson.model.BuildListener)
	 */
	public PretestedIntegrationSCMCommit popCommit(AbstractBuild build,
			Launcher launcher, BuildListener listener) throws IOException,
			IllegalArgumentException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.jenkinsci.plugins.pretestedintegration.scminterface.PretestedIntegrationSCMInterface#handlePostBuild(hudson.model.AbstractBuild, hudson.Launcher, hudson.model.BuildListener, hudson.model.Result)
	 */
	public void handlePostBuild(AbstractBuild build, Launcher launcher,
			BuildListener listener, Result result) throws IOException,
			IllegalArgumentException {
		try {
			ArgumentListBuilder cmd = HgUtils.createArgumentListBuilder(
					build, launcher, listener);
		} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		//get info regarding which branch that is going to be pushed to company truth	
		//Dictionary<String, String> newCommitInfo = HgUtils.getNewestCommitInfo(
		//		build, launcher, listener);
		//String sourceBranch = newCommitInfo.get("branch");
		//PretestUtils.logMessage(listener, "commit is on this branch: "
		//		+ sourceBranch);
		Dictionary<String, String> vars = null;
		try {
			vars =  HgUtils.getNewestCommitInfo(build, launcher, listener);
		} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		try{
		HgUtils.runScmCommand(build, launcher, listener,
				new String[]{"commit", "-m", vars.get("message")});

		HgUtils.runScmCommand(build, launcher, listener,
				new String[]{"push", "--new-branch"});
		}
		catch(AbortException e){
			throw e;
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
