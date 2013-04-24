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

public class HgUtils {
	
	private static ArgumentListBuilder findHgExe(MercurialSCM scm, Node node,
			TaskListener listener, boolean allowDebug) throws IOException,
			InterruptedException {
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
	
	public static ArgumentListBuilder createArgumentListBuilder(
			AbstractBuild build, Launcher launcher, BuildListener listener)
			throws IOException, InterruptedException {
		//Setup variables to find our executable
		AbstractProject<?,?> project = build.getProject();
		//We need to check this cast..
		MercurialSCM scm = null;
		try {
			scm = (MercurialSCM) project.getScm();
		} catch(ClassCastException e) {
			PretestUtils.logError(listener, "The chosen SCM is not Mercurial!");
			throw new AbortException(
					"The chosen SCM is not Mercurial!");
		}
		Node node = Computer.currentComputer().getNode();

		EnvVars env = build.getEnvironment(listener);

		//Why not do it like the mercurial plugin? ;)
		ArgumentListBuilder cmd = findHgExe(scm, node, listener, false);
		
		//This is also a possibility
		//new HgExe(scm,launcher,build.getBuiltOn(),listener,env);
		
		return cmd;
	}
	
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
	
	public static BufferedReader runScmCommand(AbstractBuild build,
			Launcher launcher, BuildListener listener, ArgumentListBuilder cmd)
			throws AbortException, IOException, InterruptedException {
		BufferedReader stdout;
		int exitCode;
		List<String> cmdList = cmd.toList();
		try {
			Launcher.ProcStarter starter = launcher.launch().cmds(cmd)
					.pwd(build.getWorkspace()).readStdout();
			Proc proc = starter.start();
			stdout = new BufferedReader(
					new InputStreamReader(proc.getStdout()));
			exitCode = proc.join();
		} catch(IOException e) {
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
			e.printStackTrace(listener.error(
					"Failed to execute hg command: Interrupted"));
			throw new AbortException(
					"Failed to execute hg command: Interrupted");
		}
		if(exitCode != 0) {
			List<String> hgList = new ArrayList<String>();
			String lastLine = "";
			try{
				String line;
				while((line = stdout.readLine()) != null) 
				{
					hgList.add(line);
					lastLine = line;
				}
			}catch(IOException e) 
			{
				PretestUtils.logError(listener, "An unexpected error occured when reading hg log");
			}

			if(lastLine.equals("no changes found")) 
			{
				PretestUtils.logError(listener, "No changes found when doing hg command: "+"\'"+cmdList.get(1)+"\'");
				
			}else
			{
				PretestUtils.logError(listener, "An unexpected hg log message occured, dumping log");
				Iterator itr = hgList.iterator();
				while(itr.hasNext()) 
				{
					Object element = itr.next();
					listener.error("[prteco]"+element.toString());
				}
			}	

			throw new AbortException("Failed to execute hg command");
		} else {
			PretestUtils.logMessage(listener, "Successfully executed hg command");
		}
		
		return stdout;
	}
	
	/**
	 * Returns a dictionary with the fields "changeset", "branch", "user",
	 * "date", "message". Each field will be null if the corresponding field is
	 * not defined in the log. Specifically, "branch" will be null if the commit
	 * is made on the default branch.
	 */
	public static Dictionary<String, String> getNewestCommitInfo(
			AbstractBuild build, Launcher launcher, BuildListener listener)
			throws IOException, InterruptedException, AbortException {
		
		runScmCommand(build, launcher, listener, new String[]{"pull"});
		
		BufferedReader logStdout = runScmCommand(
				build, launcher, listener, new String[]{"log", "-l", "1"});
		
		Dictionary<String, String> info = new Hashtable<String, String>();
		String line;
		while((line = logStdout.readLine()) != null) {
			String firstWord = line.split("\\s+")[0];
			String restOfLine = line.substring(firstWord.length()).trim();
			//PretestUtils.logMessage(listener,"log line: start = " + firstWord
			//		+ ", rest = " + restOfLine);
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
