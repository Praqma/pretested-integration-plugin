package org.jenkinsci.plugins.pretestedintegration.scm.git;

import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.plugins.git.GitException;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    public PersonIdent getPersonIdent(String identity) {
        Pattern regex = Pattern.compile("^([^<(]*?)[ \\t]?<([^<>]*?)>.*$");
        Matcher match = regex.matcher(identity);
        if(!match.matches()) return null;
        return new PersonIdent(match.group(1), match.group(2));
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
    protected boolean tryFastForward(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, GitBridge bridge) throws IntegrationFailedException{
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
