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
import java.util.ArrayList;
import java.util.List;


import org.kohsuke.stapler.DataBoundConstructor;

public class PretestCommitPreCheckout extends BuildWrapper {

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
	/**
	 * @param build
	 * @param launcher
	 * @param listener
	 * @return noop Environment class
	 */
	@Override
	public Environment setUp(AbstractBuild build, Launcher launcher,
			BuildListener listener) throws IOException, InterruptedException {
		listener.getLogger().println("Setup!!!");
		listener.getLogger().println("Workspace is here: "
				+ build.getWorkspace());
		
		FilePath fp = build.getWorkspace().child("pretest_stuff_was_here");
		listener.getLogger().println("Writing file here: " + fp);
		OutputStream os = fp.write();
		
		//Setup variables to find our executable
		AbstractProject<?,?> project = build.getProject();
		//We need to check this cast..
		MercurialSCM scm = (MercurialSCM) project.getScm();
		Node node = Computer.currentComputer().getNode();
		
		EnvVars env = build.getEnvironment(listener);
		
		//Why not do it like the mercurial plugin? ;)
		ArgumentListBuilder cmd = findHgExe(scm, node, listener, false);
		
		//This is also a possibility
		//new HgExe(scm,launcher,build.getBuiltOn(),listener,env);
		
		String source = build.getBuildVariables().get("branch").toString();
		//Use args.add(String a) to add an argument/flag just as you would an
		// ordinary flag
		//e.g. to do "hg pull" you'd use args.add("pull")
		cmd.add("pull");
		cmd.add("--update");
		cmd.add(build.getBuildVariables().get("branch"));
		
		//Finally use hg.run(args).join() to run the command on the system
		int cloneExitCode;
		try {
			//cloneExitCode = hg.run(args).join();\
			cloneExitCode = launcher.launch().cmds(cmd)
					.pwd(build.getWorkspace()).join();
		} catch(IOException e) {
			String message = e.getMessage();
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
			throw new AbortException("Failed to clone repository");
		}
		if(cloneExitCode!=0) {
			listener.error("Failed to clone repository");
			throw new AbortException("Failed to clone repository");
		}
		
		//Use build.getBuildVariables().get("foo") to get a build variable configured in the jenkins job
		
		os.write(0);
		os.close();
		return new NoopEnv();
	}
	
	/**
	 * @param build
	 * @param launcher
	 * @param listener
	 */
	@Override
	public void preCheckout(AbstractBuild build, Launcher launcher,
			BuildListener listener) throws IOException, InterruptedException {
		//PrintStream log = listener.getLogger();
		
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
