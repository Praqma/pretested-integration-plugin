package org.jenkinsci.plugins.pretestedintegration.scm.git;

import hudson.plugins.git.Branch;
import hudson.plugins.git.GitException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.MergeCommand;
import org.jenkinsci.plugins.pretestedintegration.IntegrationStrategy;
import org.jenkinsci.plugins.pretestedintegration.exceptions.IntegrationFailedException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.IntegrationUnknownFailureException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.NothingToDoException;

/**
 * Abstract IntegrationStrategy containing common logic for Git integration strategies.
 */
public abstract class GitIntegrationStrategy extends IntegrationStrategy implements IntegrationStrategyAsGitPluginExt {

    private static final Logger LOGGER = Logger.getLogger(GitIntegrationStrategy.class.getName());

    /**
     * Creates a PersonIdent object from a full Git identity string.
     * @param identity The Git identity string to parse. ex.: 'john Doe Joh@praqma.net 1442321765 +0200'
     * @return A PersonIdent object representing given Git author/committer
     */
    public PersonIdent getPersonIdent(String identity) {
        Pattern regex = Pattern.compile("^([^<(]*?)[ \\t]?<([^<>]*?)>.*$");
        Matcher match = regex.matcher(identity);
        if(!match.matches()) return null;
        return new PersonIdent(match.group(1), match.group(2));
    }

    /**
     * Attempts to rebase the ready integrationBranch onto the integration integrationBranch.
     * Only when the ready integrationBranch consists of a single commit.
     *
     * @param commitId The sha1 from the polled integrationBranch
     * @param client The GitClient
     * @param integrationBranch The integrationBranch which the commitId need to be merged to
     * @param logger The Printstream
     * @return true if the rebase was a success, false if the integrationBranch isn't
     * suitable for a rebase
     * @throws IntegrationFailedException When commit counting or rebasing fails
     * @throws IntegrationUnknownFailureException An unforseen failure
     */
    protected boolean tryRebase(ObjectId commitId, GitClient client, PrintStream logger, String integrationBranch ) throws IntegrationFailedException, IntegrationUnknownFailureException {
        LOGGER.log(Level.INFO, GitMessages.LOG_PREFIX+ "Entering tryRebase");
        logger.println(GitMessages.LOG_PREFIX+ "Entering tryRebase");

        //Get the commit count
        int commitCount;
        try {
            commitCount = PretestedIntegrationGitUtils.countCommits(commitId, client, integrationBranch);
            LOGGER.log(Level.INFO, GitMessages.LOG_PREFIX+ "Branch commit count: " + commitCount);
            logger.println(GitMessages.LOG_PREFIX+ "Branch commit count: " + commitCount);
        } catch (IOException | InterruptedException ex) {
            throw new IntegrationUnknownFailureException("Failed to count commits.", ex);
        }

        //Only rebase if it's a single commit
        if (commitCount != 1) {
            LOGGER.log(Level.INFO, GitMessages.LOG_PREFIX+ "Not attempting rebase. Exiting tryRebase.");
            logger.println(GitMessages.LOG_PREFIX+ "Not attempting rebase. Exiting tryRebase.");
            return false;
        }

        //Rebase the commit
        try {
            LOGGER.log(Level.INFO, GitMessages.LOG_PREFIX+ "Attempting rebase.");
            logger.println(GitMessages.LOG_PREFIX+ "Attempting rebase.");
            //Rebase the commit, then checkout master for a fast-forward merge.
            client.checkout().ref(commitId.getName()).execute();
            client.rebase().setUpstream(integrationBranch).execute();
            ObjectId rebasedCommit = client.revParse("HEAD");
            LOGGER.log(Level.INFO, GitMessages.LOG_PREFIX+ "Rebase successful. Attempting fast-forward merge.");
            logger.println(GitMessages.LOG_PREFIX+ "Rebase successful. Attempting fast-forward merge.");

            client.checkout().ref(integrationBranch).execute();
            ObjectId integrationBranchCommitBefore = client.revParse("HEAD");
            client.merge().setRevisionToMerge(rebasedCommit).setGitPluginFastForwardMode(MergeCommand.GitPluginFastForwardMode.FF_ONLY).execute();
            if ( integrationBranchCommitBefore.equals(rebasedCommit) ){
                String logMessage = String.format("%sThe integration branch did not change during the rebase of development branch on top of it.%n" +
                        "There are two known reasons:%n" +
                        "A) You are trying to integrate a change that was already integrated.%n" +
                        "B) You have pushed an empty commit( presumably used --allow-empty ) that needed a rebase. If you REALLY want the empty commit to me accepted, you can rebase your single empty commit on top of the integration branch.%n", GitMessages.LOG_PREFIX);
                LOGGER.log(Level.SEVERE, logMessage);
                throw new IntegrationFailedException(logMessage);
            } else {
                LOGGER.log(Level.INFO, GitMessages.LOG_PREFIX+ "Rebasing successful.");
                logger.println(GitMessages.LOG_PREFIX+ "Rebasing successful.");
                return true;
            }
        } catch (GitException | InterruptedException ex) {
            String logMessage = String.format(GitMessages.LOG_PREFIX+ "Exception while rebasing commit. Logging exception msg: %s", ex.getMessage());
            LOGGER.log(Level.SEVERE, logMessage, ex);
            logger.println(logMessage);
            throw new IntegrationFailedException(ex);
        }
    }

    /**
     * Attempts to fast-forward merge the integration integrationBranch to the ready integrationBranch.
     * Only when the ready integrationBranch consists of a single commit.
     *
     * @param commitId The commit
     * @param commitCount The amount of commits
     * @param logger The logger for console logging
     * @param client The GitClient
     * @return true if the FF merge was a success, false if the integrationBranch isn't
     * suitable for a FF merge.
     * @throws IntegrationFailedException When commit counting fails
     * @throws NothingToDoException In case there is no commit to integrate/FF
     */
    protected boolean tryFastForward(ObjectId commitId, PrintStream logger, GitClient client, int commitCount ) throws IntegrationFailedException, NothingToDoException {
        LOGGER.log(Level.INFO, GitMessages.LOG_PREFIX+ "Entering tryFastForward");

        if ( commitCount == 1) {
            logger.println(GitMessages.LOG_PREFIX+ "Try FF as there is only one commit");
        } else {
            logger.println(GitMessages.LOG_PREFIX+ "Skip FF as there are several commits");
            return false;
        }

        //FF merge the commit
        try {
            LOGGER.log(Level.INFO, GitMessages.LOG_PREFIX+ "Attempting merge with FF.");
            client.merge().setGitPluginFastForwardMode(MergeCommand.GitPluginFastForwardMode.FF_ONLY).setRevisionToMerge(commitId).execute();
            logger.println(GitMessages.LOG_PREFIX+ "FF merge successful.");
            LOGGER.log(Level.INFO, GitMessages.LOG_PREFIX+ " Exiting tryFastForward.");
            return true;
        } catch (GitException | InterruptedException ex) {
            logger.println(GitMessages.LOG_PREFIX+ "FF merge failed.");
            LOGGER.log(Level.INFO, GitMessages.LOG_PREFIX+ " Exiting tryFastForward.");
            return false;
        }
    }

    /**
     * Checks whether or not we can find the given remote integrationBranch.
     * @param client the Git Client
     * @param branch the integrationBranch to look for
     * @return True if the integrationBranch was found, otherwise False.
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
