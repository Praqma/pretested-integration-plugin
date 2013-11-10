package org.jenkinsci.plugins.pretestedintegration.scm.mercurial;

import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.model.BuildListener;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;
import hudson.plugins.mercurial.HgExe;
import hudson.plugins.mercurial.MercurialSCM;
import hudson.scm.SCM;
import hudson.util.ArgumentListBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Logger;

import net.sf.json.JSONObject;

import org.jenkinsci.plugins.pretestedintegration.AbstractSCMBridge;
import org.jenkinsci.plugins.pretestedintegration.Commit;
import org.jenkinsci.plugins.pretestedintegration.SCMBridgeDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

public class MercurialBridge extends AbstractSCMBridge {

	private boolean reset;
	private String revId;
	//private String result;
	
	@DataBoundConstructor
	//public Mercurial(boolean reset, String branch, String result){
	public MercurialBridge(boolean reset, String branch){
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
	
	/*
	public String getResult() {
		return this.result;
	}*/
	
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

    private MercurialSCM findScm(AbstractBuild<?,?> build) throws InterruptedException {
        try{
            SCM scm = build.getProject().getScm();
            MercurialSCM hg = (MercurialSCM) scm;
            return hg;
        } catch (ClassCastException e) {
            throw new InterruptedException("Configured scm is not mercurial");
        }
    }
    
    private ProcStarter buildCommand(AbstractBuild<?,?> build, Launcher launcher, TaskListener listener, String... cmds) throws IOException, InterruptedException {
    	MercurialSCM scm = findScm(build);
        HgExe hg = new HgExe(scm, launcher, build, listener);
        ArgumentListBuilder b = new ArgumentListBuilder();

        b.add(cmds);
		//if the working directory has not been manually set use the build workspace
		if(workingDirectory == null){
			setWorkingDirectory(build.getWorkspace());
		}
		return hg.run(b).pwd(workingDirectory);
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
	public int hg(AbstractBuild<?, ?> build, Launcher launcher, TaskListener listener, String... cmds) throws IOException, InterruptedException{
		ProcStarter hg = buildCommand(build, launcher, listener,cmds);
		int exitCode = hg.join();
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
	public int hg(AbstractBuild<?, ?> build, Launcher launcher, TaskListener listener,OutputStream out, String... cmds) throws IOException, InterruptedException{
		ProcStarter hg = buildCommand(build, launcher, listener,cmds);
		int exitCode = hg.stdout(out).join();
		return exitCode;
	}
	
	@Override
	protected void ensureBranch(AbstractBuild<?,?> build, Launcher launcher, TaskListener listener, String branch) 
		throws IOException, InterruptedException {

		logger.finest("Updating the position to the integration branch");
		//Make sure that we are on the integration branch
		hg(build, launcher, listener, "update","-C", getBranch());
	}
	
	@Override
	protected void mergeChanges(AbstractBuild<?,?> build, Launcher launcher, TaskListener listener, Commit<?> commit) throws IOException, InterruptedException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		int exitCode = hg(build, launcher, listener, out, "merge",(String) commit.getId(),"--tool","internal:merge");
		if(exitCode > 0){
			logger.finest("hg command failed with exitcode: " + exitCode);
			throw new AbortException("Could not merge. Mercurial output: " + out.toString());
		}
	}
	
	@Override
	public Commit<String> nextCommit(
			AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, Commit<?> commit)
			throws IOException, IllegalArgumentException{
		logger.finest("Mercurial plugin, nextCommit invoked");
		Commit<String> next = null;
		String revision = "0";
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		try {
			if(commit == null || reset) {
				logger.finest("Resetting revision to last successful build");
				//Get the last build on the integration branch
				hg(build,launcher, listener, out, "heads", branch, "--template", "{node}");
				revision = out.toString();
			} else {
				logger.finest("Setting revision to previous build");
				revision = (String) commit.getId();
			}
			
			listener.getLogger().println(LOG_PREFIX + "Calculating next revision from " + revision);
			
			//Make sure we have updated version of the integration branch
			hg(build, launcher, listener, "pull", branch);
			//TODO: Failsafe for when the branch does not exist
			
			//We need the instance of the installed scm here
			SCM scm = build.getProject().getScm();
			MercurialSCM hg = (MercurialSCM) scm;
			
			//Get the staging branch from mercurial
			String stage = hg.getBranch();
			out.reset();
			int exitCode = hg(build, launcher, listener,out,"log", "-r", "branch("+stage+") and "+revision+":tip","--template","{node}\n");
			
			String commits = out.toString();
			logger.finest("hg exitcode: " + exitCode);
			logger.finest("hg log result: " + commits);
			listener.getLogger().println("Resulting string" + commits);
			
			String [] commitArray = commits.split("\\n");
		
			if(!(exitCode > 0) && commits.length() > 0){
				logger.finest("New revisions found");
				//default to the first found revision
				revId = commitArray[0];
				if(revId.equals(revision)){
					listener.getLogger().println(LOG_PREFIX + "Already seen commit: " + revision);
					logger.finest("This is not the commit we're looking for");
					if(commitArray.length > 1) {
						revId = commitArray[1];
						listener.getLogger().println(LOG_PREFIX + "Next commit is: " + revId);
						logger.finest("Getting the next commit in line");

						out.reset();
						hg(build, launcher, listener, out, "log", "-r", revId,"--template","{desc}");
						
						next = new Commit<String>(revId);
					}
				} else {
					logger.finest("Grabbing the next commit naively");
					
					out.reset();
					hg(build, launcher, listener, out, "log", "-r", revId,"--template","{desc}");
					
					listener.getLogger().println("Next commit is: " + revId);
					
					next = new Commit<String>(revId);
				}
			}
		} catch (InterruptedException e){
			throw new IOException(e.getMessage());
		} catch (ClassCastException e) {
			logger.finest("Configured scm is not mercurial. Aborting...");
		}
		this.reset = false;
		logger.finest("Mercurial plugin, nextCommit returning");
		return next;
	}

	@Override
	public void commit(AbstractBuild<?, ?> build, Launcher launcher,
			BuildListener listener) throws IOException, InterruptedException {
		logger.finest("Mercurial plugin commiting");
		hg(build, launcher, listener,"commit","-m", "Merge of revision " + revId + " succesfull.");
		
		//push the changes back to the repo
		hg(build, launcher, listener,"push");
	}

	@Override
	public void rollback(AbstractBuild<?, ?> build, Launcher launcher,
			BuildListener listener) throws IOException, InterruptedException {
		logger.finest("Mercurial plugin rolling back");
		hg(build, launcher, listener, "update","-C", getBranch());
	}

	/*@Override
	public Result getRequiredResult(){
		return Result.fromString(result);
	}*/
	
	@Extension
	public static final class DescriptorImpl extends SCMBridgeDescriptor<MercurialBridge> {
		
		public String getDisplayName(){
			return "Mercurial";
		}
		
		@Override
		public MercurialBridge newInstance(StaplerRequest req, JSONObject formData) throws FormException {
			MercurialBridge i = (MercurialBridge) super.newInstance(req, formData);
			
			boolean reset = formData.getJSONObject("scmBridge").getBoolean("reset");
			String branch = formData.getJSONObject("scmBridge").getString("branch");
			
			i.reset = reset;
			i.branch = branch;
			//i.result = result;
			
			save();
			return i;
		}
	}
	
	private static Logger logger = Logger.getLogger(MercurialBridge.class.getName());
}
