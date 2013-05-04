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
import hudson.model.Descriptor.FormException;
import hudson.model.Computer;

import hudson.model.*;
import hudson.plugins.mercurial.*;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.tasks.BuildTrigger;
import hudson.tasks.BuildStep;
import hudson.util.ArgumentListBuilder;
import hudson.util.FormValidation;
import hudson.FilePath;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;

import org.ini4j.InvalidFileFormatException;
import org.ini4j.Ini;

import net.sf.json.JSONObject;
import org.jenkinsci.plugins.pretestcommit.CommitQueue;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 *
 */
public class PretestCommitPreCheckout extends BuildWrapper {
	
	private static final String DISPLAY_NAME = "Use pretested commits";
	private static final String PLUGIN_NAME = "pretest-commit";
	
	private final String stageRepositoryUrl;

	private boolean hasQueue;
	
	@DataBoundConstructor
	public PretestCommitPreCheckout(String stageRepositoryUrl) {
		this.stageRepositoryUrl = stageRepositoryUrl;
	}

	public String getStageRepositoryUrl() {
		return stageRepositoryUrl;
	}
	
	private String getScmRepositoryURL(AbstractBuild build, Launcher launcher,
			BuildListener listener) throws AbortException {
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
		return scm.getSource();
	}
	
	void mergeWithNewBranch(AbstractBuild build, Launcher launcher,
			BuildListener listener, String repositoryURL, String branch,
			String changeset, String user)
			throws IOException, InterruptedException {
		
		HgUtils.runScmCommand(build, launcher, listener,
				new String[]{"pull", "--update", repositoryURL});
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
		if(Hudson.getInstance().getPlugin(PLUGIN_NAME) != null) {
			PretestUtils.logMessage(listener,"Pre-Checkout plugin version: "
					+ Hudson.getInstance().getPlugin(PLUGIN_NAME)
					.getWrapper().getVersion());
		}
		PretestUtils.logMessage(listener, "No plugin found with name "
				+ PLUGIN_NAME);
	}
	
	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl)super.getDescriptor();
	}
	
	@Extension
	public static final class DescriptorImpl extends BuildWrapperDescriptor {
		
		public String getDisplayName() {
			return DISPLAY_NAME;
		}
		
		/**
		 * Gets the configuration directory for a repository
		 * @param repositoryUrl
		 * @return
		 */
		public File configurationDirectory(final String repositoryUrl){
			return new File(repositoryUrl, ".hg");
		}
		
		/**
		 * Tests whether a repository exists at the specified location
		 * @assert That repositoryUrl is a local repository the user has access
		 * to
		 */
		public boolean validateConfiguration(final String repositoryUrl) {
			File repoDir = configurationDirectory(repositoryUrl);
			//We should check whether the hgrc file is correctly configured 
			//and that the python hook file is properly set
			//That the repository exists is good enough for now
			return repoDir.exists();
		}
		
		public boolean setupRepositoryDirectory(File repoDir) {
			return (repoDir.exists() || repoDir.mkdirs());
		}
		
		/**
		 * Updates the configuration file in the specified repository
		 * @param repositoryUrl
		 */
		public boolean updateConfiguration(final String repositoryUrl) {
			File repoDir = configurationDirectory(repositoryUrl);
			if(setupRepositoryDirectory(repoDir)){
				//the hgDir now is a valid path
				//Write the hgrc file
				try {
					File hgrc = new File(repoDir,"hgrc");
					if(hgrc.canWrite() || hgrc.createNewFile()){
						Ini ini = new Ini(hgrc);
						ini.put("hooks","changegroup", 
							"python:.hg/hg_changegroup_hook.py:run");
						ini.store();
						return true;
					} else {
						return false;
					}
				} catch(InvalidFileFormatException e) {
					System.out.println("Invalid file format");
				} catch(IOException e) {	
					System.out.println("Some ioxception occured");
				}
			}
			return false;
		}
		
		/**
		 * Updates the hook file in the specified repository
		 * @param repositoryUrl
		 * @return 
		 */
		public boolean updateHook(final String repositoryUrl, final String job){
			File repoDir = configurationDirectory(repositoryUrl);
			if(setupRepositoryDirectory(repoDir)){
				try {
					File hook = new File(repoDir,"hg_changegroup_hook.py");
					if(hook.canWrite() || hook.createNewFile()){
						FileWriter fw = new FileWriter(hook);
						fw.write("from mercurial import ui, hg\r\n");
						fw.write("from mercurial.node import hex\r\n");
						fw.write("from httplib import HTTPConnection\r\n");
						fw.write("from urllib import urlencode\r\n");
						fw.write("import os\r\n\r\n");
						
						String jenkinsRoot = Hudson.getInstance()
								.getRootUrl()
								.replaceAll("^http://|/$", "");
						
						fw.write("def run(ui, repo, **kwargs):\r\n");
						fw.write("\thttp = HTTPConnection(\"" 
								+ jenkinsRoot + "\")\r\n");
						fw.write("\thttp.request(\"GET\",\"http://" 
								+ jenkinsRoot + "/job/" 
								+ job + "/build\")\r\n");
						fw.write("\tui.warn(\"http://" 
								+ jenkinsRoot + "/job/" 
								+ job + "/build\\n\")\r\n");
						fw.write("\tui.warn(str(http.getresponse().read())"
								+ "+\"\\n\")\r\n");
						fw.write("\treturn False\r\n");
						
						fw.close();
						return true;
					}
				} catch (IOException e) {
					System.out.println("Could not write to hook file");
				}
			}
			return false;
		}
		
		/**
		 * Performs a validation test whether the repository url is correctly 
		 * initialised
		 */
		public FormValidation doTestRepository(
				@QueryParameter("stageRepositoryUrl") final String repositoryUrl) 
						throws IOException, ServletException {
			boolean isValid = validateConfiguration(repositoryUrl);
			if(isValid) {
				return FormValidation.ok("The path contains a valid mercurial repository");
			} else {
				return FormValidation.error("The repository is not a mercurial repository");
			}
		}
		
		/**
		 * Updates or creates the repository url to be functional
		 * @param repositoryUrl
		 * @return
		 */
		public FormValidation doUpdateRepository(@QueryParameter("stageRepositoryUrl") final String repositoryUrl, 
				@QueryParameter("name") final String name) {
			System.out.println("Name of the task is: "+name);
			boolean updateConfiguration = updateConfiguration(repositoryUrl);
			if(updateConfiguration){
				boolean updateHook = updateHook(repositoryUrl, name);
				if(updateHook) {
					return FormValidation.ok("Configuration updated");
				}
			}
			return FormValidation.error("Configuration could not be updated");
		}
		
		/**
		 * Invoked when "save" is hit. 
		 */
		
		@Override
		public boolean configure(StaplerRequest req, JSONObject formData) 
				throws FormException {
			
			save();
			return super.configure(req,formData);
		}

		@Override
		public boolean isApplicable(AbstractProject<?, ?> arg0) {
			// TODO Auto-generated method stub
			return true;
		}
	}
	
	class NoopEnv extends Environment {
	}
}
