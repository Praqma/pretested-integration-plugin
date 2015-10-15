package org.jenkinsci.plugins.pretestedintegration.scm.git;

import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.plugins.git.GitException;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.jgit.lib.ObjectId;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.MergeCommand;
import org.jenkinsci.plugins.pretestedintegration.AbstractSCMBridge;
import org.jenkinsci.plugins.pretestedintegration.IntegrationStrategy;
import org.jenkinsci.plugins.pretestedintegration.exceptions.IntegationFailedExeception;
import org.jenkinsci.plugins.pretestedintegration.exceptions.NothingToDoException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.UnsupportedConfigurationException;

public class GitIntegrationStrategy extends IntegrationStrategy {

    private static final Logger logger = Logger.getLogger(GitIntegrationStrategy.class.getName());
    private static final String LOG_PREFIX = "[PREINT] ";

    @Override
    public void integrate(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, AbstractSCMBridge bridge) throws IntegationFailedExeception, NothingToDoException, UnsupportedConfigurationException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    public boolean tryFastForward(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, GitBridge bridge) throws IntegationFailedExeception{
        logger.log(Level.INFO, String.format(LOG_PREFIX + "Entering tryFastForward"));

        //Get the commit count
        int commitCount;
        try {
            commitCount = bridge.countCommits(build, listener);
            logger.log(Level.INFO, String.format(LOG_PREFIX + "Branch commit count: " + commitCount));
        } catch (IOException | InterruptedException ex) {
            throw new IntegationFailedExeception("Failed to count commits.", ex);
        }

        //Only fast forward if it's a single commit
        if (commitCount != 1) {
            listener.getLogger().println(String.format(LOG_PREFIX + "Not attempting fast forward. Exiting tryFastForward."));
            return false;
        }

        //FF merge the commit
        try {
            logger.log(Level.INFO, String.format(LOG_PREFIX + "Attempting rebase."));
            GitClient client = Git.with(listener, build.getEnvironment(listener)).in(bridge.resolveWorkspace(build, listener)).getClient();
            ObjectId commitId = bridge.findRelevantBuildData(build, listener).lastBuild.revision.getSha1();
            client.merge().setGitPluginFastForwardMode(MergeCommand.GitPluginFastForwardMode.FF_ONLY).setRevisionToMerge(commitId).execute();
            listener.getLogger().println(String.format(LOG_PREFIX + "FF merge successful."));
            logger.log(Level.INFO, LOG_PREFIX + " Exiting tryFastForward.");
            return true;
        } catch (GitException | IOException | InterruptedException ex) {
            listener.getLogger().println(String.format(LOG_PREFIX + "FF merge failed."));
            logger.log(Level.INFO, LOG_PREFIX + " Exiting tryFastForward.");
            return false;
        }
    }
}
