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
import hudson.model.Node;
import hudson.model.Project;
import hudson.model.TaskListener;
import hudson.plugins.mercurial.HgExe;
import hudson.plugins.mercurial.MercurialSCM;
import hudson.plugins.mercurial.MercurialTagAction;
import hudson.plugins.mercurial.AbstractComparator;
import hudson.scm.PollingResult.Change;

@Extension
public class MercurialComparator extends AbstractComparator {

	private String LOG_PREFIX = "[PREINT-HG] ";
	
	public Change compare(MercurialSCM scm, Launcher launcher, TaskListener listener, MercurialTagAction baseline, PrintStream output, Node node, FilePath repository, AbstractProject<?,?> project)  
			throws IOException, InterruptedException {
		
		logger.finest("Entering MercurialComparator compare");
		listener.getLogger().println(LOG_PREFIX + "Entering comparator, this is going to be exiting!");

		HgExe hg = new HgExe((MercurialSCM) scm, launcher, node, listener, new EnvVars());
        
		try {
			Project<?, ?> p = (Project<?, ?>) project;
			
			//find the buildwrapper naively for now :(
			PretestedIntegrationBuildWrapper buildWrapper = null;
			for(Object b : p.getBuildWrappers().values().toArray()) {
				if(b instanceof PretestedIntegrationBuildWrapper){
					buildWrapper = (PretestedIntegrationBuildWrapper) b;
				}
			}
			
			if(buildWrapper != null && buildWrapper.getScmBridge() instanceof MercurialBridge) {
				MercurialBridge bridge = (MercurialBridge) buildWrapper.getScmBridge();
				
				//Refresh all branches, we're gonna need the data
				hg.run("pull").pwd(p.getSomeWorkspace()).join();
				
				String integrationBranch = bridge.getBranch();
				String branches = bridge.getBranches();
				String baserev = bridge.getRevId();

				String revset = "not(branch(" + integrationBranch + ")) and branch('re:" + branches + "') and " + baserev + ":tip";
				
				listener.getLogger().println(revset);
				//Get a list of changes after a certain point in time (not functional yet)
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				int exitCode = hg.run("log", "-r" ,revset, "--template","{node}\n")
						.stdout(out)
						.pwd(p.getSomeWorkspace())
						.join();
				String[] commits = out.toString().split("\\n");
				//If any changes are found, the change is significant enough to trigger
				if(exitCode == 0 && commits.length > 1) {
					listener.getLogger().println(LOG_PREFIX + "Changes found, triggering build");
					listener.getLogger().println(LOG_PREFIX + "Out: " + commits[1]);
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
		return	Change.NONE;
	}
	
	private static Logger logger = Logger.getLogger(MercurialComparator.class.getName());
}
