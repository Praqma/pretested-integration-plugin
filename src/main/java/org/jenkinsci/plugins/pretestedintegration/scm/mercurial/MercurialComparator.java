package org.jenkinsci.plugins.pretestedintegration.scm.mercurial;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.logging.Logger;

import org.jenkinsci.plugins.pretestedintegration.PretestedIntegrationBuildWrapper;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.model.Project;
import hudson.model.TaskListener;
import hudson.plugins.mercurial.HgExe;
import hudson.plugins.mercurial.MercurialSCM;
import hudson.plugins.mercurial.MercurialTagAction;
import hudson.plugins.mercurial.PollComparator;
import hudson.scm.PollingResult.Change;
import hudson.scm.SCM;
import hudson.tasks.BuildWrapper;

@Extension
public class MercurialComparator extends PollComparator {

	private String LOG_PREFIX = "[PREINT-HG] ";
	
	@Override
	public Change compare(SCM scm, Launcher launcher, TaskListener listener, MercurialTagAction baseline, PrintStream output, Node node, FilePath repository, AbstractProject<?,?> project) 
			throws IOException, InterruptedException {
		
		logger.finest("Entering MercurialComparator compare");
		listener.getLogger().println(LOG_PREFIX + "Entering comparator, this is going to be exiting!");
		
		try {
			//Don't know what to do about these warning :S
			Project p = (Project) project;
			
			//find the buildwrapper naively for now :(
			PretestedIntegrationBuildWrapper buildWrapper = null;
			for(Object b : p.getBuildWrappers().values().toArray()) {
				if(b instanceof PretestedIntegrationBuildWrapper){
					buildWrapper = (PretestedIntegrationBuildWrapper) b;
				}
			}
				
			if(buildWrapper != null && buildWrapper.scmBridge instanceof MercurialBridge) {
				
				HgExe hg = new HgExe((MercurialSCM) scm, launcher, node, listener, new EnvVars());
					
				//Refresh all branches, we're gonna need the data
				hg.run("pull").pwd(p.getSomeWorkspace()).join();
					
				//Get a list of changes after a certain point in time (not functional yet)
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				int exitCode = hg.run("log","-r","not(branch(default))","--template","{node}\n").stdout(out).pwd(p.getSomeWorkspace()).join();
					
				//If any changes are found, the change is significant enough to trigger
				if(exitCode == 0 && out.size() > 0) {
					listener.getLogger().println(LOG_PREFIX + "Changes found, triggering build");
					return Change.SIGNIFICANT;
				}
			}
		} catch (ClassCastException e) {
			//project was not an extension of Project. Default to Change.NONE and write a logger message
			//Should we return Change.INCOMPARABLE instead?
			logger.finest("Not compatible with project type");
		}
		logger.finest("Exiting MercurialComparator compare without result");
		//listener.getLogger().println(LOG_PREFIX + "No changes found. Move along, nothing to see here.");
		return Change.NONE;
	}
	
	private static final long serialVersionUID = 1L;
	private static Logger logger = Logger.getLogger(MercurialComparator.class.getName());
}
