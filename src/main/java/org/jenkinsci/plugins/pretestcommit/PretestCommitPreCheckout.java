package org.jenkinsci.plugins.pretestcommit;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.Proc;
import hudson.model.BuildListener;
import hudson.model.Environment;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.Cause.LegacyCodeCause;
import hudson.model.Computer;

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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jenkinsci.plugins.pretestcommit.CommitQueue;

import org.kohsuke.stapler.DataBoundConstructor;

public class PretestCommitPreCheckout extends BuildWrapper {

	private boolean hasQueue;
	
	@DataBoundConstructor
	public PretestCommitPreCheckout() {
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
		ArgumentListBuilder cmd = findHgExe(scm, node, listener, false);
		
		//This is also a possibility
		//new HgExe(scm,launcher,build.getBuiltOn(),listener,env);
		
		return cmd;
	}
	
	void mergeWithNewBranch(AbstractBuild build, Launcher launcher,
			BuildListener listener, String repositoryURL, String branch,
			String changeset, String user)
			throws IOException, InterruptedException {
		// listener.getLogger().println("URL: " + repositoryURL);
		// listener.getLogger().println("branch: " + branch);
		// listener.getLogger().println("changeset: " + changeset);
		// listener.getLogger().println("user: " + user);
		
		listener.getLogger().println("Merging branch \"" + branch + "\" by "
				+ user + "...");
		
		ArgumentListBuilder cmd = createArgumentListBuilder(
				build, launcher, listener);
		cmd.add("pull");
		cmd.add("--update");
		cmd.add("-r");
		cmd.add(changeset);
		cmd.add(repositoryURL);
		
		listener.getLogger().println("Merge command: " + cmd);
		
		int mergeExitCode;
		try {
			mergeExitCode = launcher.launch().cmds(cmd)
					.pwd(build.getWorkspace()).join();
		} catch(IOException e) {
			String message = e.getMessage();
			if (message != null
					&& message.startsWith("Cannot run program")
					&& message.endsWith("No such file or directory")) {
				listener.error("Failed to merge " + repositoryURL
						+ " because hg could not be found;"
						+ " check that you've properly configured your"
						+ " Mercurial installation");
			} else {
				e.printStackTrace(listener.error(
						"Failed to merge repository"));
			}
			throw new AbortException("Failed to merge repository");
		}
		if(mergeExitCode!=0) {
			listener.error("Failed to merge repository");
			throw new AbortException("Failed to merge repository");
		} else {
			listener.getLogger().println("Successfully merged "
					+ repositoryURL);
		}
	}
	
	/**
	 * @param build
	 * @param launcher
	 * @param listener
	 * @return noop Environment class
	 */
	@Override
	public Environment setUp(AbstractBuild build, Launcher launcher,
			BuildListener listener) throws IOException, InterruptedException {
		try {
			return performSetup(build, launcher, listener);
		} catch(IOException e) {
			if (hasQueue) {
				CommitQueue.getInstance().release();
			}	
			throw(e);
			//return new NoopEnv();
		} catch(InterruptedException e) {
			if (hasQueue) {
				CommitQueue.getInstance().release();
			}	
			throw(e);
			//return new NoopEnv();
		} catch(Exception e) {
			if (hasQueue) {
				CommitQueue.getInstance().release();
			}	
			e.printStackTrace();
			return new NoopEnv();
		}
	}
	
	public Environment performSetup(AbstractBuild build, Launcher launcher,
			BuildListener listener) throws IOException, InterruptedException {

		//Get unique access or go to queue
		
		CommitQueue.getInstance().enqueueAndWait();
		hasQueue = true;
		ArgumentListBuilder cmd = createArgumentListBuilder(
				build, launcher, listener);
		cmd.add("log");
		cmd.add("-l");
		cmd.add("1");
		
		BufferedReader logStdout;
		int logExitCode;
		try {
			Launcher.ProcStarter starter = launcher.launch().cmds(cmd)
					.pwd(build.getWorkspace()).readStdout();
			Proc proc = starter.start();
			logStdout = new BufferedReader(
					new InputStreamReader(proc.getStdout()));
			logExitCode = starter.join();
		} catch(IOException e) {
			String message = e.getMessage();
			if (message != null
					&& message.startsWith("Cannot run program")
					&& message.endsWith("No such file or directory")) {
				listener.error("Failed to get hg log"
						+ " because hg could not be found;"
						+ " check that you've properly configured your"
						+ " Mercurial installation");
			} else {
				e.printStackTrace(listener.error(
						"Failed to get hg log"));
			}
			throw new AbortException("Failed to get hg log");
		}
		if(logExitCode != 0) {
			listener.error("Failed to get hg log");
			throw new AbortException("Failed to get hg log");
		} else {
			listener.getLogger().println("Successfully got log");
		}
		
		String branch = null;
		String changeset = null;
		String user = null;
		String message = null;
		String date = null;
		
		String line;
		while((line = logStdout.readLine()) != null) {
			String firstWord = line.split("\\s+")[0];
			String restOfLine = line.substring(firstWord.length()).trim();
			listener.getLogger().println("log line: start = " + firstWord
					+ ", rest = " + restOfLine);
			if(firstWord.equals("changeset:")) {
				changeset = restOfLine;
			} else if(firstWord.equals("branch:")) {
				branch = restOfLine;
			} else if(firstWord.equals("user:")) {
				user = restOfLine;
			} else if(firstWord.equals("date:")) {
				date = restOfLine;
			} else if(firstWord.equals("summary:")) {
				message = restOfLine;
			}
		}
		
		listener.getLogger().println("changeset: " + changeset);
		listener.getLogger().println("branch: " + branch);
		listener.getLogger().println("user: " + user);
		listener.getLogger().println("date: " + date);
		listener.getLogger().println("message: " + message);
		
		// Map<String,String> vars = build.getBuildVariables();
		// String repositoryURL = vars.get("user_repository_url").toString();
		// String branch = vars.get("user_branch").toString();
		// String changeset = vars.get("user_changeset").toString();
		// String user = vars.get("user_name").toString();
		
		
//		test build URL: sben.dk:8081/job/Demo job/buildWithParameters?user_name=mig&user_changeset=1234&user_branch=b&user_repository_url=file:///home/nogen/hej
//		mergeWithNewBranch(build, launcher, listener, repositoryURL, branch,
//				changeset, user);//TODO find repositoryURL
		
		return new NoopEnv();
		
//		PRTECO
	}
	
	/**
	 * @param build
	 * @param launcher
	 * @param listener
	 */
	@Override
	public void preCheckout(AbstractBuild build, Launcher launcher,
			BuildListener listener) throws IOException, InterruptedException {
		listener.getLogger().println("Pre-checkout!!!");
	}
	@Extension
	public static final class DescriptorImpl extends Descriptor<BuildWrapper> {
		public String getDisplayName() {
			return "Run pretest-commit stuff before SCM runs";
		}
	}

	class NoopEnv extends Environment {
	}
}
