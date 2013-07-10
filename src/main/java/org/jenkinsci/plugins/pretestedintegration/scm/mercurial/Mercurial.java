package org.jenkinsci.plugins.pretestedintegration.scm.mercurial;

import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.plugins.mercurial.MercurialInstallation;
import hudson.plugins.mercurial.MercurialSCM;
import hudson.scm.SCM;
import hudson.util.ArgumentListBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Logger;

import net.sf.json.JSONObject;

import org.jenkinsci.plugins.pretestedintegration.Commit;
import org.jenkinsci.plugins.pretestedintegration.AbstractSCMInterface;
import org.jenkinsci.plugins.pretestedintegration.PretestedIntegrationAction;
import org.jenkinsci.plugins.pretestedintegration.PretestedIntegrationPostCheckout;
import org.jenkinsci.plugins.pretestedintegration.SCMInterfaceDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

public class Mercurial extends AbstractSCMInterface {

	private boolean reset;
	private String branch;
	
	@DataBoundConstructor
	public Mercurial(boolean reset, String branch){
		this.reset = reset;
		if(branch != null && !branch.equals(""))
			this.branch = branch;
	}
	
	public boolean getReset(){
		return this.reset;
	}
	
	public String getBranch() {
		return this.branch == null ? "default" : this.branch;
	}
	
	/**
	 * The directory in which to execute hg commands
	 */
	private FilePath workingDirectory = null;
	final static String LOG_PREFIX = "[PREINT-HG] ";

	public void setWorkingDirectory(FilePath workingDirectory){
		this.workingDirectory = workingDirectory;
	}
	
	public FilePath getWorkingDirectory(){
		return this.workingDirectory;
	}
	
	/**
	 * Locate the correct mercurial binary to use for commands
	 * @param build
	 * @param listener
	 * @param allowDebug
	 * @return An ArgumentListBuilder containing the correct hg binary
	 * @throws IOException
	 * @throws InterruptedException
	 */
	private static ArgumentListBuilder findHgExe(AbstractBuild build, TaskListener listener, boolean allowDebug) throws IOException,
			InterruptedException {
		//Cast the current SCM to get the methods we want. 
		//Throw exception on failure
		try{
			SCM scm = build.getProject().getScm();
			MercurialSCM hg = (MercurialSCM) scm;
			
			Node node = build.getBuiltOn();
			// Run through Mercurial installations and check if they correspond to
			// the one used in this job
			for (MercurialInstallation inst
					: MercurialInstallation.allInstallations()) {
				if (inst.getName().equals(hg.getInstallation())) {
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
			//Just use the default hg
			return new ArgumentListBuilder(hg.getDescriptor().getHgExe());
		} catch(ClassCastException e) {
			throw new InterruptedException("Configured scm is not mercurial");
		}
	}
	
	/**
	 * Invoke a command with mercurial
	 * @param build
	 * @param launcher
	 * @param listener
	 * @param cmds
	 * @return The exitcode of command
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public int hg(AbstractBuild build, Launcher launcher, TaskListener listener, String... cmds) throws IOException, InterruptedException{
		ArgumentListBuilder hg = findHgExe(build, listener, false);
		hg.add(cmds);
		//if the working directory has not been manually set use the build workspace
		if(workingDirectory == null){
			setWorkingDirectory(build.getWorkspace());
		}
		int exitCode = launcher.launch().cmds(hg).pwd(workingDirectory).join();
		return exitCode;
	}

	/**
	 * Invoke a command with mercurial
	 * @param build
	 * @param launcher
	 * @param listener
	 * @param cmds
	 * @return The exitcode of command
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public int hg(AbstractBuild build, Launcher launcher, TaskListener listener,OutputStream out, String... cmds) throws IOException, InterruptedException{
		ArgumentListBuilder hg = findHgExe(build, listener, false);
		hg.add(cmds);
		//if the working directory has not been manually set use the build workspace
		if(workingDirectory == null){
			setWorkingDirectory(build.getWorkspace());
		}
		int exitCode = launcher.launch().cmds(hg).stdout(out).pwd(workingDirectory).join();
		return exitCode;
	}
	
	/**
	 * Given a date, search through the revision history and find the first changeset committed on or after the specified date.
	 * @param build
	 * @param launcher
	 * @param listener
	 * @param date
	 * @return A commit representation of the next commit made at the specified date, or null
	 * @throws IOException
	 * @throws InterruptedException
	 */

	/*public PretestedIntegrationSCMCommit commitFromDate(AbstractBuild build, Launcher launcher, TaskListener listener, Date date) throws IOException, InterruptedException{
		PretestedIntegrationSCMCommit commit = null;
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		hg(build, launcher, listener, out, "log","-r","0:tip","-l1","--date",">" + dateFormat.format(date), "--template","{node}");
		String revision = out.toString();
		if(revision.length() > 0)
			commit = new PretestedIntegrationSCMCommit(revision);
		return commit;
	}*/

	@Extension
	public static final class DescriptorImpl extends SCMInterfaceDescriptor<Mercurial> {
		
		public String getDisplayName(){
			return "Mercurial";
		}
		
		@Override
		public Mercurial newInstance(StaplerRequest req, JSONObject formData) throws FormException {
			Mercurial i = (Mercurial) super.newInstance(req, formData);
			
			boolean reset = formData.getJSONObject("scmInterface").getBoolean("reset");
			String branch = formData.getJSONObject("scmInterface").getString("branch");
			i.reset = reset;
			i.branch = branch;
			
			save();
			return i;
		}
	}

	@Override
	public void prepareWorkspace(AbstractBuild<?, ?> build, Launcher launcher,
			BuildListener listener, Commit<?> commit)
			throws AbortException, IOException, IllegalArgumentException {
		logger.finest("Mercurial plugin, prepareWorkspace invoked");
		try {
			logger.finest("Updating the position to the integration branch");
			//Make sure that we are on the integration branch
			hg(build, launcher, listener, "update","-C", getBranch());
			
			logger.finest("Merging the commit into the integration branch");
			//Merge the commit into the integration branch
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			int exitCode = hg(build, launcher, listener, out, "merge",(String) commit.getId(),"--tool","internal:merge");
			if(exitCode > 0){
				logger.finest("hg command failed with exitcode: " + exitCode);
				throw new AbortException("Could not merge. Mercurial output: " + out.toString());
			}
		} catch(InterruptedException e){
			throw new AbortException("Merge into integration branch exited unexpectedly");
		}	
		logger.finest("Mercurial plugin, prepareWorkspace returning");
	}
	
	@Override
	public Commit<String> nextCommit(
			AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, Commit<?> commit)
			throws IOException, IllegalArgumentException{
		logger.finest("Mercurial plugin, nextCommit invoked");
		Commit<String> next = null;
		String revision;
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		try {
			if(commit == null || reset) {
				logger.finest("Resetting revision to last successful build");
				//Get the last build on the integration branch
				hg(build,launcher, listener, out,"heads",getBranch(),"--template","{node}");
				revision = out.toString();
			} else {
				logger.finest("Setting revision to previous build");
				revision = (String) commit.getId();
			}
			
			hg(build, launcher, listener,"pull"); //wow this is bad
			String branch = getBranch();
			out.reset();
			int exitCode = hg(build, launcher, listener,out,"log", "-r", "not branch("+branch+") and "+revision+":tip","--template","{node}\\n");
			//System.out.println("exitCode: " + exitCode);
		
			String commits = out.toString();
			//System.out.println("Resulting string" + output);
			
			String [] commitArray = commits.split("\\n");
		
			if(!(exitCode > 0) && commitArray.length > 0){
				logger.finest("New revisions found");
				if(commitArray[0].equals(revision)){
					logger.finest("This is not the commit we're looking for");
					if(commitArray.length > 1) {
						logger.finest("Getting the next commit in line");
						next = new Commit<String>(commitArray[1]);
					}
				} else {
					logger.finest("Grabbing the next commit naively");
					next = new Commit<String>(commitArray[0]);
				}
			}
		} catch (InterruptedException e){
			throw new IOException(e.getMessage());
		}
		this.reset = false;
		logger.finest("Mercurial plugin, nextCommit returning");
		return next;
	}

	@Override
	public void commit(AbstractBuild<?, ?> build, Launcher launcher,
			BuildListener listener) throws IOException, InterruptedException {
		logger.finest("Mercurial plugin commiting");
		hg(build, launcher, listener,"commit","-m", "Successfully integrated development branch");
		
	}

	@Override
	public void rollback(AbstractBuild<?, ?> build, Launcher launcher,
			BuildListener listener) throws IOException, InterruptedException {
		logger.finest("Mercurial plugin rolling back");
		hg(build, launcher, listener, "update","-C", getBranch());
	}
	
	private static Logger logger = Logger.getLogger(PretestedIntegrationPostCheckout.class.getName());
}
