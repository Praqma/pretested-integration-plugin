package org.jenkinsci.plugins.pretestedintegration.scm.git;

import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.plugins.git.Branch;
import hudson.plugins.git.GitException;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.MergeCommand;
import org.jenkinsci.plugins.pretestedintegration.IntegrationStrategy;
import org.jenkinsci.plugins.pretestedintegration.PretestedIntegrationBuildWrapper;
import org.jenkinsci.plugins.pretestedintegration.exceptions.IntegrationFailedException;

/**
 * Abstract IntegrationStrategy containing common logic for Git integration strategies.
 */
public abstract class GitIntegrationStrategy extends IntegrationStrategy {

    private static final Logger LOGGER = Logger.getLogger(GitIntegrationStrategy.class.getName());

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
        LOGGER.log(Level.INFO, String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Entering tryRebase"));

        //Get the commit count
        int commitCount;
        try {
            commitCount = bridge.countCommits(build, listener);
            LOGGER.log(Level.INFO, String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Branch commit count: " + commitCount));
        } catch (IOException | InterruptedException ex) {
            throw new IntegrationFailedException("Failed to count commits.", ex);
        }

        //Only rebase if it's a single commit
        if (commitCount != 1) {
            LOGGER.log(Level.INFO, String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Not attempting rebase. Exiting tryRebase."));
            return false;
        }

        //Rebase the commit
        try {
            LOGGER.log(Level.INFO, String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Attempting rebase."));
            GitClient client = bridge.findScm(build, listener).createClient(listener, build.getEnvironment(listener), build, build.getWorkspace());
            ObjectId commitId = bridge.findRelevantBuildData(build, listener).lastBuild.revision.getSha1();
            String expandedBranch = bridge.getExpandedBranch(build.getEnvironment(listener));

            //Rebase the commit, then checkout master for a fast-forward merge.
            client.checkout().ref(commitId.getName()).execute();
            client.rebase().setUpstream(expandedBranch).execute();
            ObjectId rebasedCommit = client.revParse("HEAD");
            LOGGER.log(Level.INFO, String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Rebase successful. Attempting fast-forward merge."));
            client.checkout().ref(expandedBranch).execute();
            client.merge().setRevisionToMerge(rebasedCommit).setGitPluginFastForwardMode(MergeCommand.GitPluginFastForwardMode.FF_ONLY).execute();
            LOGGER.log(Level.INFO, String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Fast-forward merge successful. Exiting tryRebase."));
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
        LOGGER.log(Level.INFO, String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Entering tryFastForward"));

        //Get the commit count
        int commitCount;
        try {
            commitCount = bridge.countCommits(build, listener);
            LOGGER.log(Level.INFO, String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Branch commit count: " + commitCount));
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
            LOGGER.log(Level.INFO, String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "Attempting rebase."));
            GitClient client = bridge.findScm(build, listener).createClient(listener, build.getEnvironment(listener), build, build.getWorkspace());
            ObjectId commitId = bridge.findRelevantBuildData(build, listener).lastBuild.revision.getSha1();
            client.merge().setGitPluginFastForwardMode(MergeCommand.GitPluginFastForwardMode.FF_ONLY).setRevisionToMerge(commitId).execute();
            listener.getLogger().println(String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "FF merge successful."));
            LOGGER.log(Level.INFO, PretestedIntegrationBuildWrapper.LOG_PREFIX + " Exiting tryFastForward.");
            return true;
        } catch (GitException | IOException | InterruptedException ex) {
            listener.getLogger().println(String.format(PretestedIntegrationBuildWrapper.LOG_PREFIX + "FF merge failed."));
            LOGGER.log(Level.INFO, PretestedIntegrationBuildWrapper.LOG_PREFIX + " Exiting tryFastForward.");
            return false;
        }
    }

    /**
     * Checks whether or not we can find the given remote branch.
     * @param client the Git Client
     * @param branch the branch to look for
     * @return True if the branch was found, otherwise False.
     * @throws IntegrationFailedException when the Git call failed unexpectedly
     */
    protected boolean containsRemoteBranch(GitClient client, Branch branch) throws IntegrationFailedException {
        try {
            LOGGER.fine("Resolving and getting Git client from workspace:");
            LOGGER.fine("Remote branches:");
            for (Branch remoteBranch : client.getRemoteBranches()) {
                LOGGER.fine(String.format("Found remote branch %s", remoteBranch.getName()));
                if (remoteBranch.getName().equals(branch.getName())) {
                    return true;
                }
            }
        } catch (GitException | InterruptedException ex) {
            LOGGER.log(Level.SEVERE, "GitClient error", ex);
            throw new IntegrationFailedException("GitClient error, unspecified", ex);
        }
        return false;
    }
}
