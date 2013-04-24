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
import org.jenkinsci.plugins.pretestcommit.HelloWorldBuilder.DescriptorImpl;

import net.sf.json.JSONObject;
import org.jenkinsci.plugins.pretestcommit.CommitQueue;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

public class PretestCommitPreCheckout extends BuildWrapper {
	
	private final String stageRepositoryUrl;

	private boolean hasQueue;
	
	@DataBoundConstructor
	public PretestCommitPreCheckout(String stageRepositoryUrl) {
		this.stageRepositoryUrl = stageRepositoryUrl;
	}

	public String getStageRepositoryUrl() {
		return stageRepositoryUrl;
	}
	
	/**
	 * Finds the hg executable on the system. This method is  
	 * taken from MercurialSCM where it is private
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
		// listener.getLogger().println("URL: " + repositoryURL);
		// listener.getLogger().println("branch: " + branch);
		// listener.getLogger().println("changeset: " + changeset);
		// listener.getLogger().println("user: " + user);
		
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
		
		/*ArgumentListBuilder cmd = createArgumentListBuilder(
				build, launcher, listener);
		cmd.add("pull");
		int updateExitCode;
		try {
			updateExitCode = launcher.launch().cmds(cmd)
					.pwd(build.getWorkspace()).join();
		} catch(IOException e) {
			throw new AbortException("Failed to update");
		}
		if(updateExitCode!=0) {
			listener.error("Failed to update repository");
			throw new AbortException("Failed to update repository");
		} else {
			listener.getLogger().println("Successfully updated");
		}
		
		
		/*ArgumentListBuilder* cmd = createArgumentListBuilder(
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
		
		String new_branch = null;
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
				new_branch = restOfLine;
			} else if(firstWord.equals("user:")) {
				user = restOfLine;
			} else if(firstWord.equals("date:")) {
				date = restOfLine;
			} else if(firstWord.equals("summary:")) {
				message = restOfLine;
			}
		}
		
		listener.getLogger().println("changeset: " + changeset);
		listener.getLogger().println("branch: " + new_branch);
		listener.getLogger().println("user: " + user);
		listener.getLogger().println("date: " + date);
		listener.getLogger().println("message: " + message);
		
		// Map<String,String> vars = build.getBuildVariables();
		// String repositoryURL = vars.get("user_repository_url").toString();
		// String branch = vars.get("user_branch").toString();
		// String changeset = vars.get("user_changeset").toString();
		// String user = vars.get("user_name").toString();
		
		
//		test build URL: sben.dk:8081/job/Demo job/buildWithParameters?user_name=mig&user_changeset=1234&user_branch=b&user_repository_url=file:///home/nogen/hej
		//mergeWithNewBranch(build, launcher, listener, repositoryURL, branch,
		//		changeset, user);//TODO
		*/
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
	
	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl)super.getDescriptor();
	}
	
	@Extension
	public static final class DescriptorImpl extends BuildWrapperDescriptor {
		
		public String getDisplayName() {
			return "Use pretested commits";
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
		 * @assert That repositoryUrl is a local repository the user has access to
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
						fw.write("\tui.warn(str(http.getresponse().read())+\"\\n\")\r\n");
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
