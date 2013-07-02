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

import net.sf.json.JSONObject;

import org.jenkinsci.plugins.pretestedintegration.Commit;
import org.jenkinsci.plugins.pretestedintegration.AbstractSCMInterface;
import org.jenkinsci.plugins.pretestedintegration.SCMInterfaceDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

public class Mercurial extends AbstractSCMInterface {

	private String latest;
	private String pattern;
	
	@DataBoundConstructor
	public Mercurial(String latest, String pattern){
		this.latest = latest;
		this.pattern = pattern;
	}
	
	public String getLatest(){
		return this.latest;
	}
	
	public String getPattern() {
		return this.pattern;
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
			
			String latest = formData.getJSONObject("scmInterface").getString("latest");
			String pattern = formData.getJSONObject("scmInterface").getString("pattern");
			i.latest = latest;
			i.pattern = pattern;
			
			save();
			return i;
		}
	}

	@Override
	public void prepareWorkspace(AbstractBuild<?, ?> build, Launcher launcher,
			BuildListener listener, Commit<?> commit)
			throws AbortException, IOException, IllegalArgumentException {
		try {
			//Make sure that we are on the integration branch
			//TODO: Make it dynamic and not just "default"

			hg(build, launcher, listener, "update","-C","default");
			
			//Merge the commit into the integration branch
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			int exitCode = hg(build, launcher, listener, out, "merge",(String) commit.getId(),"--tool","internal:merge");
			if(exitCode > 0)
				throw new AbortException("Merging branches caused conflict: " + out.toString());
		} catch(InterruptedException e){
			throw new AbortException("Merge into integration branch exited unexpectedly");
		}	
	}
	
	@Override
	public Commit<String> nextCommit(
			AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, Commit<?> commit)
			throws IOException, IllegalArgumentException{
		Commit<String> next = null;
		String revision;
		if(commit == null || commit.getId().equals("0")) {
			revision = "0";
		} else {
			revision = (String) commit.getId();
		}
		ByteArrayOutputStream logStdout = new ByteArrayOutputStream();
		try {
			hg(build, launcher, listener,"pull"); //wow this is bad
			int exitCode = hg(build, launcher, listener,logStdout,"log", "-r", "not branch(default) and "+revision+":tip","--template","{node}\\n");
			//System.out.println("exitCode: " + exitCode);
		
			String output = logStdout.toString();
			//System.out.println("Resulting string" + output);
			
			String [] commitArray = logStdout.toString().split("\\n");
		
			if(!(exitCode > 0) && commitArray.length > 0){
				if(commitArray[0].equals(revision)){
					//System.out.println("Already seen this commit, move along");
					if(commitArray.length > 1) {
						//System.out.println("Wow there was another commit");
						next = new Commit<String>(commitArray[1]);
					}
				} else {
					//System.out.println("Getting a commit!");
					next = new Commit<String>(commitArray[0]);
				}
				if(next != null)
					this.latest = next.getId();
			}
		} catch (InterruptedException e){
			throw new IOException(e.getMessage());
		}
		return next;
	}

	@Override
	public void commit(AbstractBuild<?, ?> build, Launcher launcher,
			BuildListener listener) throws IOException, InterruptedException {
		hg(build, launcher, listener,"commit","-m", "Successfully integrated development branch");
		
	}

	@Override
	public void rollback(AbstractBuild<?, ?> build, Launcher launcher,
			BuildListener listener) throws IOException, InterruptedException {
		hg(build, launcher, listener, "update","-C");
	}
}
