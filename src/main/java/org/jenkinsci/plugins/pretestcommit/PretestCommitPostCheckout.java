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
	 * Finds the hg executable on the system. This method is  taken from MercurialSCM where it is private
	 * @param node
	 * @param listener
	 * @param allowDebug
	 * @return
	 * @throws IOException
	 * @throws InterruptedException
	 */
	ArgumentListBuilder findHgExe(MercurialSCM scm, Node node, TaskListener listener, boolean allowDebug) throws IOException, InterruptedException {
		for (MercurialInstallation inst : MercurialInstallation.allInstallations()) {
			if (inst.getName().equals(scm.getInstallation())) {
				// XXX what about forEnvironment?
				String home = inst.getExecutable().replace("INSTALLATION", inst.forNode(node, listener).getHome());
				ArgumentListBuilder b = new ArgumentListBuilder(home);
				if (allowDebug && inst.getDebug()) {
					b.add("--debug");
				}
				return b;
			}
		}
		return new ArgumentListBuilder(scm.getDescriptor().getHgExe());
	}

	private ArgumentListBuilder createArgumentListBuilder(
			AbstractBuild build, Launcher launcher, BuildListener listener)
			throws IOException, InterruptedException {
		//Setup variables to find our executable
		AbstractProject<?,?> project = build.getProject();
		//We need to check this cast..
		MercurialSCM scm = null;
		try {
			scm = (MercurialSCM) project.getScm();
		} catch(ClassCastException e) {
			listener.error("The chosen SCM is not Mercurial!");
			throw new AbortException("The chosen SCM is not Mercurial!");
		}
		Node node = Computer.currentComputer().getNode();

		EnvVars env = build.getEnvironment(listener);

		//Why not do it like the mercurial plugin? ;)
		ArgumentListBuilder cmd = findHgExe(scm, node, listener, true);
		
		//This is also a possibility
		//new HgExe(scm,launcher,build.getBuiltOn(),listener,env);
		
		return cmd;
	}

	/**
	 * Pushing to Company Truth
	 * 
	 * @param build
	 * @param launcher
	 * @param listener
	 */
	private void pushToCT(AbstractBuild build, Launcher launcher,
			BuildListener listener) throws IOException, InterruptedException {
		ArgumentListBuilder cmd = createArgumentListBuilder(
				build, launcher, listener);
		
		Dictionary<String, String> newCommitInfo = HgLog.getNewestCommitInfo(
				build, launcher, listener);
		String sourceBranch = newCommitInfo.get("branch");
		listener.getLogger().println("[prteco] commit is on this branch: "
				+ sourceBranch);
		HgLog.runScmCommand(build, launcher, listener,
				new String[]{"push", "--branch", sourceBranch});
	}
	
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
			listener.getLogger().println(
					"[prteco] Could not read log. Assuming build failure.");
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
	 * @return noop Environment class
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
		
	public boolean work(AbstractBuild build, Launcher launcher,
			BuildListener listener) throws IOException, InterruptedException {
		hasQueue = true;
		BufferedReader br = new BufferedReader(build.getLogReader());
		boolean status = getBuildSuccessStatus(build, launcher, listener);
		listener.getLogger().println("[prteco] Post build status: " + status);
		
		if(status) {
			listener.getLogger().println(
					"[prteco] Pushing resulting workspace to CT...");
			pushToCT(build, launcher, listener);
			listener.getLogger().println("[prteco] ...done!");
		} else {
			listener.getLogger().println(
					"[prteco] Build error. Not pushing to CT");
		}
		
		listener.getLogger().println(
				"Queue available pre release: " +
				CommitQueue.getInstance().available());
		CommitQueue.getInstance().release();
		hasQueue = false;
		listener.getLogger().println(
				"Queue available post release: " +
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
