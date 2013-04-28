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
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Iterator;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Collection of static methods for interacting with Mercurial.
 * TODO: All functionality in this class must be moved once we finish our design
 * for interacting with different SCMs through a single interface.
 */	
public class HgUtils {

	/**
	 * Get an interface to the Mercurial executable.
	 * 
	 * @param scm The SCM plugin used in the job
	 * @param node 
	 * @param listener
	 * @param allowDebug If debug information should be printed
	 *
	 * @return The resulting ArgumentListBuilder that can make calls to the
	 * Mercurial executable
	 */	
	private static ArgumentListBuilder findHgExe(MercurialSCM scm, Node node,
			TaskListener listener, boolean allowDebug) throws IOException,
			InterruptedException {
		// Run through Mercurial installations and check if they correspond to
		// the one used in this job
		for (MercurialInstallation inst
				: MercurialInstallation.allInstallations()) {
			if (inst.getName().equals(scm.getInstallation())) {
				// TODO: what about forEnvironment?
				String home = inst.getExecutable().replace("INSTALLATION",
						inst.forNode(node, listener).getHome());
				ArgumentListBuilder b = new ArgumentListBuilder(home);
				if (allowDebug && inst.getDebug()) {
					b.add("--debug");
				}
				return b;
			}
		}
		return new ArgumentListBuilder(scm.getDescriptor().getHgExe());
	}
	
	/**
	 * Get an interface to the Mercurial executable corresponding to the current
	 * build.
	 * 
	 * @param AbstractBuild
	 * @param Launcher
	 * @param BuildListener
	 */	
	public static ArgumentListBuilder createArgumentListBuilder(
			AbstractBuild build, Launcher launcher, BuildListener listener)
			throws IOException, InterruptedException {
		// Setup variables to find our executable
		AbstractProject<?,?> project = build.getProject();
		// Get the SCM and try to cast it to MercurialSCM
		MercurialSCM scm = null;
		try {
			scm = (MercurialSCM) project.getScm();
		} catch(ClassCastException e) {
			// This job is not using Mercurial. Nothing we can do now...
			PretestUtils.logError(listener, "The chosen SCM is not Mercurial!");
			throw new AbortException(
					"The chosen SCM is not Mercurial!");
		}
		
		// Create and return ArgumentListBuilder for the current
		// node/environment
		Node node = Computer.currentComputer().getNode();
		EnvVars env = build.getEnvironment(listener);
		return findHgExe(scm, node, listener, false);
		
		// TODO: This is also a possibility
		//new HgExe(scm,launcher,build.getBuiltOn(),listener,env);
	}
	
	/**
	 * Run an Mercurial command given a list of arguments.
	 * 
	 * @param AbstractBuild
	 * @param Launcher
	 * @param BuildListener
	 * @param String[] List of arguments for the Mercurial executable.
	 *
	 * @return A reader through which one can get the output of the command.
	 */	
	public static BufferedReader runScmCommand(AbstractBuild build,
			Launcher launcher, BuildListener listener, String command[])
			throws AbortException, IOException, InterruptedException {
		ArgumentListBuilder cmd = createArgumentListBuilder(
				build, launcher, listener);
		for(String s : command) {
			cmd.add(s);
		}
		return runScmCommand(build, launcher, listener, cmd);
	}
	
	/**
	 * Run an Mercurial command given an ArgumentListBuilder describing the
	 * arguments.
	 * 
	 * @param AbstractBuild
	 * @param Launcher
	 * @param BuildListener
	 * @param ArgumentListBuilder Argument list describing what to pass to the
	 * Mercurial executable.
	 *
	 * @return BufferedReader	 
	 */	
	public static BufferedReader runScmCommand(AbstractBuild build,
			Launcher launcher, BuildListener listener, ArgumentListBuilder cmd)
			throws AbortException, IOException, InterruptedException {
		BufferedReader stdout;
		int exitCode;
		List<String> cmdList = cmd.toList();
		// try to run the command
		try {
			Launcher.ProcStarter starter = launcher.launch().cmds(cmd)
					.pwd(build.getWorkspace()).readStdout();
			Proc proc = starter.start();
			stdout = new BufferedReader(
					new InputStreamReader(proc.getStdout()));
			exitCode = proc.join();
		} catch(IOException e) {
			// We can't save this, just inform the user
			String message = e.getMessage();
			if(message != null
					&& message.startsWith("Cannot run program")
					&& message.endsWith("No such file or directory")) {
				PretestUtils.logError(listener, "Failed to get hg command "
						+ " because hg could not be found;"
						+ " check that you've properly configured your"
						+ " Mercurial installation");
			} else {
				e.printStackTrace(listener.error(
						"Failed to execute hg command"));
			}
			throw new AbortException("Failed to execute hg command");
		} catch(InterruptedException e) {
			// This should not happen...
			e.printStackTrace(listener.error(
					"Failed to execute hg command: Interrupted"));
			throw new AbortException(
					"Failed to execute hg command: Interrupted");
		}
		if(exitCode != 0) {
			// Program ran but failed. Read output to find out what happened.
			List<String> hgList = new ArrayList<String>();
			String lastLine = "";
			try {
				String line;
				while((line = stdout.readLine()) != null) 
				{
					hgList.add(line);
					lastLine = line;
				}
			} catch(IOException e) {
				PretestUtils.logError(listener, "An unexpected error occured"
						+ " when reading hg log");
			}
			
			if(lastLine.equals("no changes found")) {
				// This means that no error actually happened, but the command
				// did nothing
				PretestUtils.logError(listener,
						"No changes found when doing hg command: \'"
								+ cmdList.get(1) + "\'");
			} else {
				// Unknown error, just show it to the user
				PretestUtils.logError(listener,
						"An unexpected hg log message occured, dumping log:");
				Iterator itr = hgList.iterator();
				while(itr.hasNext()) {
					Object element = itr.next();
					listener.error("[prteco]" + element.toString());
				}
			}	

			throw new AbortException("Failed to execute hg command");
		} else {
			PretestUtils.logMessage(listener,
					"Successfully executed hg command");
		}
		
		return stdout;
	}
	
	/**
	 * Returns a dictionary with the fields "changeset", "branch", "user",
	 * "date", "message". Each field will be null if the corresponding field is
	 * not defined in the log. Specifically, "branch" will be null if the commit
	 * is made on the default branch.
	 * 
	 * @param AbstractBuild
	 * @param Launcher
	 * @param BuildListener
	 *
	 * @return
	 */	
	public static Hashtable<String, String> getNewestCommitInfo(
			AbstractBuild build, Launcher launcher, BuildListener listener)
			throws IOException, InterruptedException, AbortException {
		// Make sire we have the latest changes
		runScmCommand(build, launcher, listener, new String[]{"pull"});
		// Get the first item in the log
		BufferedReader logStdout = runScmCommand(
				build, launcher, listener, new String[]{"log", "-l", "1"});
		
		// Read one line at a time and put the values into a dictionary
		Hashtable<String, String> info = new Hashtable<String, String>();
		String line;
		while((line = logStdout.readLine()) != null) {
			String firstWord = line.split("\\s+")[0];
			String restOfLine = line.substring(firstWord.length()).trim();
			if(firstWord.equals("changeset:")) {
				info.put("changeset", restOfLine);
			} else if(firstWord.equals("branch:")) {
				info.put("branch", restOfLine);
			} else if(firstWord.equals("user:")) {
				info.put("user", restOfLine);
			} else if(firstWord.equals("date:")) {
				info.put("date", restOfLine);
			} else if(firstWord.equals("summary:")) {
				info.put("message", restOfLine);
			}
		}
		
		// Dump it all to the log
		PretestUtils.logMessage(listener,
				"SCM log data:");
		PretestUtils.logMessage(listener,
				"\tchangeset: " + info.get("changeset"));
		PretestUtils.logMessage(listener,
				"\tbranch: " + info.get("branch"));
		PretestUtils.logMessage(listener,
				"\tuser: " + info.get("user"));
		PretestUtils.logMessage(listener,
				"\tdate: " + info.get("date"));
		PretestUtils.logMessage(listener,
				"\tmessage: " + info.get("message"));
		
		return info;
	}
	
}
