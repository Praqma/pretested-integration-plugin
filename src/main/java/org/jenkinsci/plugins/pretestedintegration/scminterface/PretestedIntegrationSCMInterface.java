
package org.jenkinsci.plugins.pretestedintegration.scminterface;

import java.io.IOException;

import hudson.AbortException;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.AbstractBuild;


/**
 * Implements a class that specifies how an SCM can be used by the Pretested Integration Plugin.
 */
public interface PretestedIntegrationSCMInterface {
	
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
	void prepareWorkspace(
			AbstractBuild build, Launcher launcher, BuildListener listener,
			PretestedIntegrationSCMCommit commit)
			throws AbortException, IOException, IllegalArgumentException;
	
	/**
	 * Test if the list of pending commits is non-empty.
	 *
	 * @return true if there are more commits.
	 * 
	 * @throws IOException A repository could not be reached.
	 * @throws InvalidArgumentException The given repository is not in a valid
	 * condition.
	 */
	boolean hasNextCommit(
			AbstractBuild build, Launcher launcher, BuildListener listener)
			throws IOException, IllegalArgumentException;
	
	/**
	 * Return the next pending commit, and advance the commit log to the next
	 * element.
	 *
	 * @return The next pending commit.
	 *
	 * @throws AbortException There is no pending commit.
	 * @throws IOException A repository could not be reached.
	 * @throws InvalidArgumentException The given repository is not in a valid
	 * condition.
	 */
	PretestedIntegrationSCMCommit popCommit(
			AbstractBuild build, Launcher launcher, BuildListener listener)
			throws IOException, IllegalArgumentException;
	
	/**
	 * This is called after the build has run. If the build was successful, the
	 * commit should be pushed to the integration branch.
	 *
	 * @param build The status of the build.
	 *
	 * @throws IOException A repository could not be reached.
	 * @throws InvalidArgumentException The given repository is not in a valid
	 * condition.
	 */
	void handlePostBuild(
			AbstractBuild build, Launcher launcher, BuildListener listener,
			Result result)
			throws IOException, IllegalArgumentException;
}
