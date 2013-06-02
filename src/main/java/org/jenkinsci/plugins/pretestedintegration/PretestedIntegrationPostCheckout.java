package org.jenkinsci.plugins.pretestedintegration;

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

import org.jenkinsci.plugins.pretestedintegration.CommitQueue;
import org.jenkinsci.plugins.pretestedintegration.scminterface
		.PretestedIntegrationSCMInterface;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * A collection of functions used post build.
 */
public class PretestedIntegrationPostCheckout extends Publisher {
	
	private static final String DISPLAY_NAME =
			"Run pretested integration post-build step";
	
	private boolean hasQueue;
	
	@DataBoundConstructor
	public PretestedIntegrationPostCheckout() {
	}

	@Override
	public boolean needsToRunAfterFinalized() {
		return true;
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
			BuildListener listener) throws IOException {
		try {
			PretestUtils.logMessage(listener, "Beginning post-build step");
			hasQueue = true;
			
			// Get the interface for the SCM according to the chosen SCM
			PretestedIntegrationSCMInterface scmInterface =
					PretestUtils.getScmInterface(build, launcher, listener);
			if(scmInterface == null) {
				return false;
			}
			
			scmInterface.handlePostBuild(build, launcher, listener);
			
			CommitQueue.getInstance().release();
			hasQueue = false;
			
			PretestUtils.logMessage(listener, "Finished post-build step");
		} catch(IOException e) {
			if(hasQueue) {
				CommitQueue.getInstance().release();
			}
			throw(e);
			//return false;
		} catch(Exception e) {
			if(hasQueue) {
				CommitQueue.getInstance().release();
			}
			e.printStackTrace();
			return false;
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
			return DISPLAY_NAME;
		}
	}
}
