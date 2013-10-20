package org.jenkinsci.plugins.pretestedintegration.scm.mercurial;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.logging.Logger;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.plugins.mercurial.HgExe;
import hudson.plugins.mercurial.MercurialSCM;
import hudson.plugins.mercurial.MercurialTagAction;
import hudson.plugins.mercurial.PollComparator;
import hudson.scm.PollingResult.Change;
import hudson.scm.SCM;

@Extension
public class MercurialComparator extends PollComparator {

	private String LOG_PREFIX = "[PREINT-HG] ";
	
	@Override
	public Change compare(SCM scm, Launcher launcher, TaskListener listener, MercurialTagAction baseline, PrintStream output, Node node, FilePath repository, AbstractProject<?,?> project) 
			throws IOException, InterruptedException {
		
		logger.finest("Entering MercurialComparator compare");
		listener.getLogger().println(LOG_PREFIX + "Entering comparator, this is going to be exiting!");
		HgExe hg = new HgExe((MercurialSCM) scm, launcher, node, listener, new EnvVars());
		
		hg.run("pull").join();
		
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		int exitCode = hg.run("log","-r","not(branch(default))","--template","{node}\n").stdout(out).join();
		
		
		if(exitCode == 0 && out.size() > 0) {
			listener.getLogger().println(LOG_PREFIX + "Changes found, triggering build");
			return Change.SIGNIFICANT;
		}
		
		logger.finest("Exiting MercurialComparator compare without result");
		listener.getLogger().println(LOG_PREFIX + "No changes found. Move along, nothing to see here.");
		return Change.NONE;
	}
	
	private static final long serialVersionUID = 1L;
	private static Logger logger = Logger.getLogger(MercurialComparator.class.getName());
}
