package org.jenkinsci.plugins.pretestedintegration.scm.git;

import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.plugins.git.GitException;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.MergeCommand;
import org.jenkinsci.plugins.pretestedintegration.IntegrationStrategy;
import org.jenkinsci.plugins.pretestedintegration.PretestedIntegrationBuildWrapper;
import org.jenkinsci.plugins.pretestedintegration.exceptions.IntegrationFailedException;

public abstract class GitIntegrationStrategy extends IntegrationStrategy {

    private static final Logger logger = Logger.getLogger(GitIntegrationStrategy.class.getName());

    /**
     * Creates a PersonIdent object from a full Git identity string.
     * @param identity The Git identity string to parse. ex.: john Doe <Joh@praqma.net> 1442321765 +0200
     * @return A PersonIdent object representing given Git author/committer
     */
    protected PersonIdent getPersonIdent(String identity) {
        int endOfName = identity.indexOf("<");
        String authorName = identity.substring(0, endOfName-1);
        int endOfMail = identity.indexOf(">");
        String authorMail = identity.substring(endOfName + 1, endOfMail);
        int endOfTime = identity.indexOf(" ", endOfMail+2);
        long time = Long.parseLong(identity.substring(endOfMail + 2, endOfTime));
        int zone = Integer.parseInt(identity.substring(identity.indexOf(" ", identity.indexOf(">")+2)+1));
        return new PersonIdent(authorName, authorMail, time, zone);
    }

    /**
     * Attempts to rebase the ready branch onto the integration branch.
     * Only when the ready branch consists of a single commit.
     *
     * @param build The Build
     * @param launcher The Launcher
     * @param listener The BuildListener
     * @param bridge The GitBridge
     * @return true if the rebase was a success, false if the branch isn't
     * suitable for a rebase
     * @throws IntegrationFailedException When commit counting or rebasing fails
     */
    protected boolean tryRebase(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, GitBridge bridge) throws IntegrationFailedException {
        logger.log(Level.INFO, String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Entering tryRebase"));

        //Get the commit count
        int commitCount;
        try {
            commitCount = bridge.countCommits(build, listener);
            logger.log(Level.INFO, String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Branch commit count: " + commitCount));
        } catch (IOException | InterruptedException ex) {
            throw new IntegrationFailedException("Failed to count commits.", ex);
        }

        //Only rebase if it's a single commit
        if (commitCount != 1) {
            logger.log(Level.INFO, String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Not attempting rebase. Exiting tryRebase."));
            return false;
        }

        //Rebase the commit
        try {
            logger.log(Level.INFO, String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Attempting rebase."));
            GitClient client = Git.with(listener, build.getEnvironment(listener)).in(bridge.resolveWorkspace(build, listener)).getClient();
            ObjectId commitId = bridge.findRelevantBuildData(build, listener).lastBuild.revision.getSha1();
            String expandedBranch = bridge.getExpandedBranch(build.getEnvironment(listener));

            //Rebase the commit, then checkout master for a fast-forward merge.
            client.checkout().ref(commitId.getName()).execute();
            client.rebase().setUpstream(expandedBranch).execute();
            ObjectId rebasedCommit = client.revParse("HEAD");
            logger.log(Level.INFO, String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Rebase successful. Attempting fast-forward merge."));
            client.checkout().ref(expandedBranch).execute();
            client.merge().setRevisionToMerge(rebasedCommit).setGitPluginFastForwardMode(MergeCommand.GitPluginFastForwardMode.FF_ONLY).execute();
            logger.log(Level.INFO, String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Fast-forward merge successful. Exiting tryRebase."));
            return true;
        } catch (GitException | IOException | InterruptedException ex) {
            throw new IntegrationFailedException("Failed to rebase commit.", ex);
        }
    }

    /**
     * Attempts to fast-forward merge the integration branch to the ready branch.
     * Only when the ready branch consists of a single commit.
     *
     * @param build The Build
     * @param launcher The Launcher
     * @param listener The BuildListener
     * @param bridge The GitBridge
     * @return true if the FF merge was a success, false if the branch isn't
     * suitable for a FF merge.
     * @throws IntegrationFailedException When commit counting fails
     */
    public boolean tryFastForward(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, GitBridge bridge) throws IntegrationFailedException{
        logger.log(Level.INFO, String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Entering tryFastForward"));

        //Get the commit count
        int commitCount;
        try {
            commitCount = bridge.countCommits(build, listener);
            logger.log(Level.INFO, String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Branch commit count: " + commitCount));
        } catch (IOException | InterruptedException ex) {
            throw new IntegrationFailedException("Failed to count commits.", ex);
        }

        //Only fast forward if it's a single commit
        if (commitCount != 1) {
            listener.getLogger().println(String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Not attempting fast forward. Exiting tryFastForward."));
            return false;
        }

        //FF merge the commit
        try {
            logger.log(Level.INFO, String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Attempting rebase."));
            GitClient client = Git.with(listener, build.getEnvironment(listener)).in(bridge.resolveWorkspace(build, listener)).getClient();
            ObjectId commitId = bridge.findRelevantBuildData(build, listener).lastBuild.revision.getSha1();
            client.merge().setGitPluginFastForwardMode(MergeCommand.GitPluginFastForwardMode.FF_ONLY).setRevisionToMerge(commitId).execute();
            listener.getLogger().println(String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "FF merge successful."));
            logger.log(Level.INFO, PretestedIntegrationBuildWrapper.LOG_PREFIX + " Exiting tryFastForward.");
            return true;
        } catch (GitException | IOException | InterruptedException ex) {
            listener.getLogger().println(String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "FF merge failed."));
            logger.log(Level.INFO, PretestedIntegrationBuildWrapper.LOG_PREFIX + " Exiting tryFastForward.");
            return false;
        }
    }
}
