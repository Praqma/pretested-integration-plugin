package org.jenkinsci.plugins.pretest_commit;

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
