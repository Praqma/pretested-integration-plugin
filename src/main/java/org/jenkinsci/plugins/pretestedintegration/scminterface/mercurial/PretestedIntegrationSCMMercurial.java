/**
 * 
 */
package org.jenkinsci.plugins.pretestedintegration.scminterface.mercurial;

import hudson.AbortException;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.AbstractBuild;

import java.io.IOException;
import java.util.Dictionary;

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
		// TODO Auto-generated method stub

	}

}
