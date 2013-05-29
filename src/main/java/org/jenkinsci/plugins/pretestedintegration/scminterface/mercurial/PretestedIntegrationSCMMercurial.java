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

	/* (non-Javadoc)
	 * @see org.jenkinsci.plugins.pretestedintegration.scminterface.PretestedIntegrationSCMInterface#prepareWorkspace(hudson.model.AbstractBuild, hudson.Launcher, hudson.model.BuildListener, org.jenkinsci.plugins.pretestedintegration.scminterface.PretestedIntegrationSCMCommit)
	 */
	public void prepareWorkspace(AbstractBuild build, Launcher launcher,
			BuildListener listener, PretestedIntegrationSCMCommit commit)
			throws AbortException, IOException, IllegalArgumentException {
		// TODO Auto-generated method stub

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
