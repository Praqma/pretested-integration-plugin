package org.jenkinsci.plugins.pretestcommit;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Environment;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.Cause.LegacyCodeCause;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildTrigger;
import hudson.tasks.BuildStep;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.tasks.BuildStepMonitor;
import hudson.model.*;
import hudson.plugins.mercurial.HgExe;
import hudson.plugins.mercurial.MercurialInstallation;
import hudson.plugins.mercurial.MercurialSCM;
import hudson.plugins.mercurial.*;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildTrigger;
import hudson.tasks.BuildStep;
import hudson.util.ArgumentListBuilder;
import hudson.FilePath;

import java.io.IOException;
import java.io.PrintStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Dictionary;
import java.io.BufferedReader;

import org.jenkinsci.plugins.pretestcommit.CommitQueue;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 *
 */
public class PretestCommitPostCheckout extends Publisher {
	
	private static final String DISPLAY_NAME = "Run pretest post-build step";
	
	private boolean hasQueue;
	
	@DataBoundConstructor
	public PretestCommitPostCheckout() {
	}

	@Override
	public boolean needsToRunAfterFinalized() {
		return true;
	}
	
	/**
	 * Pushing to Company Truth
	 * 
	 * @param build
	 * @param launcher
	 * @param listener
	 *
	 * @return void	 
	 */
	private void pushToCT(AbstractBuild build, Launcher launcher,
			BuildListener listener) throws IOException, InterruptedException {
		ArgumentListBuilder cmd = HgUtils.createArgumentListBuilder(
				build, launcher, listener);
		
		Dictionary<String, String> newCommitInfo = HgUtils.getNewestCommitInfo(
				build, launcher, listener);
		String sourceBranch = newCommitInfo.get("branch");
		PretestUtils.logMessage(listener, "commit is on this branch: "
				+ sourceBranch);
		HgUtils.runScmCommand(build, launcher, listener,
				new String[]{"push", "--branch", sourceBranch});
	}
	
	/**
	 * 
	 * 
	 * @param build
	 * @param launcher
	 * @param listener
	 *
	 * @return boolean	 
	 */
	private boolean getBuildSuccessStatus(AbstractBuild build,
			Launcher launcher, BuildListener listener) {
		boolean success = true;
		try {
			BufferedReader br = new BufferedReader(build.getLogReader());
			while(success) {
				String line = br.readLine();
				if(line == null) {
					break;
				}
				if(line.startsWith("Build step '")
						&& line.endsWith("' marked build as failure")) {
							success = false;
					break;
				}
			}
		} catch(IOException e) {
			PretestUtils.logMessage(listener,
					"Could not read log. Assuming build failure.");
			success = false;
		}
		return success;
	}
	
	/**
	 * Overridden setup returns a noop class as we don't want to add anything
	 * here.
	 *
	 * @param build
	 * @param launcher
	 * @param listener
	 *
	 * @return boolean
	 */
	@Override
	public boolean perform(AbstractBuild build, Launcher launcher,
			BuildListener listener) throws IOException, InterruptedException {
		try {
			return work(build, launcher, listener);
		} catch(IOException e) {
			if (hasQueue) {
				CommitQueue.getInstance().release();
			}
			throw(e);
			//return false;
		} catch(InterruptedException e) {
			if (hasQueue) {
				CommitQueue.getInstance().release();
			}
			throw(e);
			//return false;
		} catch(Exception e) {
			if (hasQueue) {
				CommitQueue.getInstance().release();
			}
			e.printStackTrace();
			return false;
		}
	}
		
	/**
	 * 
	 * 
	 * @param build
	 * @param launcher
	 * @param listener
	 *
	 * @return boolean	 
	 */
	public boolean work(AbstractBuild build, Launcher launcher,
			BuildListener listener) throws IOException, InterruptedException {
		hasQueue = true;
		BufferedReader br = new BufferedReader(build.getLogReader());
		boolean status = getBuildSuccessStatus(build, launcher, listener);
		PretestUtils.logMessage(listener, "Post build status: " + status);
		
		if(status) {
			PretestUtils.logMessage(listener,
					"Pushing resulting workspace to CT...");
			pushToCT(build, launcher, listener);
			PretestUtils.logMessage(listener, "...done!");
		} else {
			PretestUtils.logMessage(listener, "Build error. Not pushing to CT");
		}
		
		PretestUtils.logMessage(listener, "Queue available pre release: " +
				CommitQueue.getInstance().available());
		CommitQueue.getInstance().release();
		hasQueue = false;
		PretestUtils.logMessage(listener, "Queue available post release: " +
				CommitQueue.getInstance().available());

		return true;
	}
	
	@Override
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
