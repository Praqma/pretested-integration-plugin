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
import java.io.BufferedReader;

import org.kohsuke.stapler.DataBoundConstructor;

public class PretestCommitPostCheckout extends Recorder {

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
		
		String source = build.getBuildVariables().get("branch").toString();
		//Use args.add(String a) to add an argument/flag just as you would an
		// ordinary flag
		//e.g. to do "hg pull" you'd use args.add("pull")
		cmd.add("push");
		//cmd.add("--update");
		//cmd.add(build.getBuildVariables().get("branch"));

		//Finally use hg.run(args).join() to run the command on the system
		int pushExitCode;
		try {
			//cloneExitCode = hg.run(args).join();\
			pushExitCode = launcher.launch().cmds(cmd)
					.pwd(build.getWorkspace()).join();
		} catch(IOException e) {
			String message = e.getMessage();
			listener.getLogger().println("Failed to push workspace to company truth -- IOException");
			/*
			if (message != null
					&& message.startsWith("Cannot run program")
					&& message.endsWith("No such file or directory")) {
				listener.error("Failed to clone " + source
						+ " because hg could not be found;"
						+ " check that you've properly configured your"
						+ " Mercurial installation");
			} else {
				e.printStackTrace(listener.error(
						"Failed to clone repository"));
			}
			*/
			throw new AbortException("Failed to push workspace to company truth");//"Failed to clone repository");
		}
		if(pushExitCode!=0) {
			listener.error("Failed to push workspace to company truth, "+pushExitCode);
			listener.getLogger().println("Failed to push workspace to company truth");
			throw new AbortException("Failed to push workspace to company truth");
		} else {
			listener.getLogger().println("Successfully pushed workspace to company truth");
		}
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
		BufferedReader br = new BufferedReader(build.getLogReader());
		boolean success = true;
		while (success) {
			String line = br.readLine();
			if (line == null) {
				break;
			}
			if (line.startsWith("Build step '")
					&& line.endsWith("' marked build as failure")) {
				success = false;
				break;
			}
		}
		listener.getLogger().println("Post build status HESTEHEST: "+success);
		if (success) {
			listener.getLogger().println("Pushing resulting workspace to CT...");
			pushToCT(build, launcher, listener);
			listener.getLogger().println("...done!");
		} else {
			listener.getLogger().println("LULWHATPONY");
		}
		return true;
	}
	
	@Override
	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.BUILD;
	}
	
	@Extension
	public static final class DescriptorImpl extends Descriptor<Publisher> {
		public String getDisplayName() {
			return "Run pretest-commit stuff after everything and whatnut";
		}
	}
}
