/**
 * 
 */
package org.jenkinsci.plugins.pretestedintegration.scminterface.mercurial;

import hudson.AbortException;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Node;
import hudson.model.AbstractBuild;
import hudson.model.TaskListener;
import hudson.plugins.mercurial.MercurialInstallation;
import hudson.plugins.mercurial.MercurialSCM;
import hudson.scm.SCM;
import hudson.util.ArgumentListBuilder;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.util.Dictionary;

//import org.jenkinsci.plugins.pretestedintegration.scminterface.mercurial.HgUtils;
import org.jenkinsci.plugins.pretestedintegration.PretestUtils;
import org.jenkinsci.plugins.pretestedintegration.scminterface.PretestedIntegrationSCMCommit;
import org.jenkinsci.plugins.pretestedintegration.scminterface.PretestedIntegrationSCMInterface;

/**
 * Implementation of PretestedIntegrationSCMInterface.
 * This class adds integration support for mercurial SCM
 * @author rel
 *
 */
public class PretestedIntegrationSCMMercurial implements
		PretestedIntegrationSCMInterface {

	/**
	 * The directory in which to execute hg commands
	 */
	private FilePath workingDirectory = null;
	
	public void setWorkingDirectory(FilePath workingDirectory){
		this.workingDirectory = workingDirectory;
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

	/* (non-Javadoc)
	 * @see org.jenkinsci.plugins.pretestedintegration.scminterface.PretestedIntegrationSCMInterface#hasNextCommit(hudson.model.AbstractBuild, hudson.Launcher, hudson.model.BuildListener)
	 */
	public void prepareWorkspace(AbstractBuild build, Launcher launcher,
			BuildListener listener, PretestedIntegrationSCMCommit commit)
			throws AbortException, IOException, IllegalArgumentException {
		try {
			//Make sure that we are on the integration branch
			//TODO: Make it dynamic and not just "default"
			hg(build, launcher, listener, "update","default");
			
			//Merge the commit into the integration branch
			hg(build, launcher, listener, "merge", commit.getId(),"--tool","internal:merge");
		} catch(InterruptedException e){
			throw new AbortException("hg command exited unexpectedly");
		}	
	}

	/* (non-Javadoc)
	 * @see org.jenkinsci.plugins.pretestedintegration.scminterface.PretestedIntegrationSCMInterface#hasNextCommit(hudson.model.AbstractBuild, hudson.Launcher, hudson.model.BuildListener)
	 */
	public boolean hasNextCommit(AbstractBuild build, Launcher launcher,
			BuildListener listener) throws IOException,
			IllegalArgumentException {
			
		String revision = "0";
		try {
			ByteArrayOutputStream logStdout = new ByteArrayOutputStream();
			int exitCode = hg(build, launcher, listener,logStdout, "log", "-r", revision+":tip","--template","{node}\\n");
			String outString = logStdout.toString().trim();
			String [] commitArray = outString.split("\\n");
			System.out.println("The result: " + outString);
			if(commitArray.length > 1) {
				System.out.println("Returning true \\o/");
				return true;
			}
		} catch(InterruptedException e) {
			throw new IOException(e.getMessage());
		}
		System.out.println("Return false :(");
		return false;
	}

	/* (non-Javadoc)
	 * @see org.jenkinsci.plugins.pretestedintegration.scminterface.PretestedIntegrationSCMInterface#popCommit(hudson.model.AbstractBuild, hudson.Launcher, hudson.model.BuildListener)
	 */
	public PretestedIntegrationSCMCommit popCommit(AbstractBuild build,
			Launcher launcher, BuildListener listener) throws IOException,
			IllegalArgumentException {
			


				String revision = "0";
				
				try {
					ByteArrayOutputStream logStdout = new ByteArrayOutputStream();
					int exitCode = hg(build, launcher, listener,logStdout, new String[]
							{"log", "-r", revision+":tip","--template","\"{node}\\n\"" });
					
					String [] commitArray = logStdout.toString().split("\\n");
					if(commitArray.length<2 ){
						return null;
				
					}else{
						
						
						PretestedIntegrationSCMCommit commit = new PretestedIntegrationSCMCommit(commitArray[1]);
						return commit;
					}
				}
				catch(IOException e)
				{
					throw e;
				}
				catch(InterruptedException e)
				{
					throw new IOException(e.getMessage());
				}

		//return null;
	}

	/* (non-Javadoc)
	 * @see org.jenkinsci.plugins.pretestedintegration.scminterface.PretestedIntegrationSCMInterface#handlePostBuild(hudson.model.AbstractBuild, hudson.Launcher, hudson.model.BuildListener, hudson.model.Result)
	 */
	public void handlePostBuild(AbstractBuild build, Launcher launcher,
			BuildListener listener) throws IOException,
			IllegalArgumentException {
		try {
			ArgumentListBuilder cmd = HgUtils.createArgumentListBuilder(
					build, launcher, listener);
		} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		//get info regarding which branch that is going to be pushed to company truth	
		//Dictionary<String, String> newCommitInfo = HgUtils.getNewestCommitInfo(
		//		build, launcher, listener);
		//String sourceBranch = newCommitInfo.get("branch");
		//PretestUtils.logMessage(listener, "commit is on this branch: "
		//		+ sourceBranch);
		Dictionary<String, String> vars = null;
		try {
			vars =  HgUtils.getNewestCommitInfo(build, launcher, listener);
		} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		try{
		HgUtils.runScmCommand(build, launcher, listener,
				new String[]{"commit", "-m", vars.get("message")});

		HgUtils.runScmCommand(build, launcher, listener,
				new String[]{"push", "--new-branch"});
		}
		catch(AbortException e){
			throw e;
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
