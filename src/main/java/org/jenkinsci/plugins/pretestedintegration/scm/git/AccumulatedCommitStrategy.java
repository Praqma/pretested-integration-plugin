/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.jenkinsci.plugins.pretestedintegration.scm.git;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.plugins.git.Branch;
import hudson.plugins.git.util.BuildData;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.pretestedintegration.AbstractSCMBridge;
import org.jenkinsci.plugins.pretestedintegration.exceptions.IntegationFailedExeception;
import org.jenkinsci.plugins.pretestedintegration.IntegrationStrategy;
import org.jenkinsci.plugins.pretestedintegration.IntegrationStrategyDescriptor;
import org.jenkinsci.plugins.pretestedintegration.exceptions.NothingToDoException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.UnsupportedConfigurationException;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 *
 * @author Mads
 */
public class AccumulatedCommitStrategy extends IntegrationStrategy {
    
    private static final String B_NAME = "Accumulated commit";
    private static final Logger logger = Logger.getLogger(AccumulatedCommitStrategy.class.getName());
    private static final String LOG_PREFIX = "[PREINT] ";
    private static final int unLikelyExitCode = -999; // An very unlikely exit code, that we use as default

    @DataBoundConstructor
    public AccumulatedCommitStrategy() { }

    @Override
    public void integrate(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener, AbstractSCMBridge bridge) throws IntegationFailedExeception, NothingToDoException, UnsupportedConfigurationException {
        logger.entering("AccumulatedCommitStrategy", "integrate", new Object[] { build, listener, bridge, launcher });// Generated code DONT TOUCH! Bookmark: ee74dbf7df6fa51582ccc15f5fee72da
        int exitCodeMerge = unLikelyExitCode;
        int exitCodeCommit = unLikelyExitCode;

        GitClient client;

        GitBridge gitbridge = (GitBridge)bridge;
        
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BuildData gitBuildData = gitbridge.checkAndDetermineRelevantBuildData(build, listener);
        String commit = gitBuildData.lastBuild.revision.getSha1String();

        //TODO: Implement robustness, in which situations does this one contain 
        // multiple revisons, when two branches point to the same commit? 
        // (JENKINS-24909). Check branch spec before doing anything     
        Branch gitDataBranch = gitBuildData.lastBuild.revision.getBranches().iterator().next();
        boolean found = false;


        try {
            logger.log(Level.INFO, String.format("Preparing to merge changes in commit %s on development branch %s to integration branch %s", commit, gitDataBranch.getName(), gitbridge.getExpandedBranch(build.getEnvironment(listener))));
            listener.getLogger().println(String.format(LOG_PREFIX + "Preparing to merge changes in commit %s on development branch %s to integration branch %s", commit, gitDataBranch.getName(), gitbridge.getExpandedBranch(build.getEnvironment(listener))));
            logger.fine("Resolving and getting git client from workspace:");
            client = Git.with(listener, build.getEnvironment(listener)).in(gitbridge.resolveWorkspace(build, listener)).getClient();
            logger.fine("Finding remote branches:");
            for (Branch b : client.getRemoteBranches()) {
                logger.fine(String.format("Found remote branch %s", b.getName()));
                if (b.getName().equals(gitDataBranch.getName())) {
                    found = true;
                    break;
                }
            }
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "GitClient error", ex);
            logger.exiting("AccumulatedCommitStrategy", "integrate");// Generated code DONT TOUCH! Bookmark: 26b6ce59c6edbad7afa29f96febc6fd7
            throw new IntegationFailedExeception("GitClient error, unspecified", ex);
        }

        if (!found) {
            logger.fine("Found no remote branches.");
            try {
                logger.fine("Setting build description 'Nothing to do':");
                build.setDescription(String.format("Noting to do"));
                logger.fine("Done setting build description.");
            } catch (IOException ex) {
                logger.log(Level.FINE, "Failed to update build description", ex);
            }
            String msg = GitMessages.NoRelevantSCMchange(gitDataBranch != null ? gitDataBranch.getName() : "null");
            logger.log(Level.WARNING, msg);
            logger.exiting("AccumulatedCommitStrategy", "integrate");// Generated code DONT TOUCH! Bookmark: 26b6ce59c6edbad7afa29f96febc6fd7
            throw new NothingToDoException(msg);
        }

        String commitAuthor; //leaving un-assigned, want to fail later if not assigned
        try {
            // FIXME I don't like this call back design, where we collect data 
            // for commit message based on a series of commits which aren't 
            // really ensure to match what the 'git merge' command ends up 
            // merging.
            // The method that get all commits from a branch walk the git tree 
            // using JGit, so it is complete independent from the following
            // merge operation. Worst case is that the merge commit message is
            // based on other commits than the actual merge commit consist of.
            logger.log(Level.INFO, String.format(LOG_PREFIX + "Collecting commit messages on development branch %s", gitDataBranch.getName()));
            listener.getLogger().println(String.format(LOG_PREFIX + "Collecting commit messages on development branch %s", gitDataBranch.getName()));
            String commits = client.withRepository(new GetAllCommitsFromBranchCallback(listener, gitDataBranch.getSHA1(), gitbridge.getExpandedBranch(build.getEnvironment(listener))));
            String headerLine = String.format("Accumulated commit of the following from branch '%s':%n", gitDataBranch.getName());
            logger.log(Level.INFO, String.format(LOG_PREFIX + "Done collecting commit messages"));
            listener.getLogger().println(String.format(LOG_PREFIX + "Done collecting commit messages"));

            // Finding author of the commit, re-using a call back method like 'FindCommitMessageCallback'
            logger.log(Level.INFO, String.format(LOG_PREFIX + "Collecting author of last commit on development branch"));
            listener.getLogger().println(String.format(LOG_PREFIX + "Collecting author of last commit on development branch"));
            commitAuthor = client.withRepository(new FindCommitAuthorCallback(listener, gitDataBranch.getSHA1()));
            logger.log(Level.INFO, String.format(LOG_PREFIX + "Done colecting last commit author: %s", commitAuthor));
            listener.getLogger().println(String.format(LOG_PREFIX + "Done colecting last commit author: %s", commitAuthor));

            // Merge and commit must be splitted in two steps, to allow setting author which only is supported on commit command, not merge.
            logger.info("Starting accumulated merge (no-ff) - without commit:");
            listener.getLogger().println(String.format(LOG_PREFIX + "Starting accumulated merge (no-ff) - without commit:"));
            String commitMsg = String.format("%s%n%s", headerLine, commits);
            String modifiedCommitMsg = commitMsg.replaceAll("\"","'");
            exitCodeMerge = gitbridge.git(build, launcher, listener, out, "merge", "--no-ff", "--no-commit", "-m", modifiedCommitMsg, commit);
            logger.info("Accumulated merge done");
            listener.getLogger().println(String.format(LOG_PREFIX + "Accumulated merge done"));
        } catch (Exception ex) {
            // We handle all exceptions here, as we will not continue with
            // anything if there is problems, even if it is only null pointer
            // in debug printing.
            // So we throw an exception, that is handled by the build wrapper
            // which also print out the exception to the job console to let the
            // user easily investigate.
            logger.log(Level.SEVERE, "Exception while merging. Logging exception", ex);
            listener.getLogger().println(LOG_PREFIX + String.format("Exception while merging. Logging exception msg: %s", ex.getMessage()));
            logger.exiting("AccumulatedCommitStrategy", "integrate-mergeFailure");
            throw new IntegationFailedExeception(ex);
        }
        // NOTICE: The catch-throw exception above means we have handles all 
        // exceptions at this point, and only need to look to at exit codes.
        if (exitCodeMerge != 0) {
            logger.log(Level.SEVERE, "Failed to merge.");
            logger.log(Level.SEVERE, String.format("Git command failed with exit code '%d' and error message:", exitCodeMerge));
            logger.log(Level.SEVERE, out.toString());
            listener.getLogger().println(LOG_PREFIX + "Failed to merge.");
            listener.getLogger().println(String.format(LOG_PREFIX + "Git command failed with exit code '%d' and error message:", exitCodeMerge));
            listener.getLogger().println(out.toString());

            try {
                logger.fine("Setting build description 'Failed to merge':");
                build.setDescription(String.format("Failed to merge."));
                logger.fine("Done setting build description.");
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Failed to update build description", ex);
                logger.exiting("AccumulatedCommitStrategy", "integrate");
                // It is not fatal to fail setting build description on the job
                // throw new IntegationFailedExeception(ex);
            }
            logger.exiting("AccumulatedCommitStrategy", "integrate");
            throw new IntegationFailedExeception("Could not merge changes. Git output: " + out.toString());
        }
        logger.log(Level.INFO, String.format(LOG_PREFIX + "Merge was successful"));
        listener.getLogger().println(String.format(LOG_PREFIX + "Merge was successful"));

        
        try {
            logger.info("Starting to commit accumulated merge changes:");
            listener.getLogger().println(String.format(LOG_PREFIX + "Starting to commit accumulated merge changes:"));
            exitCodeCommit = gitbridge.git(build, launcher, listener, out, "commit", "--no-edit", "--author=" + commitAuthor);
            logger.info("Commit of accumulated merge done");
            listener.getLogger().println(String.format(LOG_PREFIX + "Commit of accumulated merge done"));
        } catch (Exception ex) {
            // We handle all exceptions here, as we will not continue with
            // anything if there is problems, even if it is only null pointer
            // in debug printing.
            // So we throw an exception, that is handled by the build wrapper
            // which also print out the exception to the job console to let the
            // user easily investigate.
            logger.log(Level.SEVERE, "Exception while comitting. Logging exception", ex);
            listener.getLogger().println(LOG_PREFIX + String.format("Exception while committing. Logging exception msg: %s", ex.getMessage()));
            logger.exiting("AccumulatedCommitStrategy", "integrate-commitFailure");
            throw new IntegationFailedExeception(ex);
        }
        // NOTICE: The catch-throw exception above means we have handles all 
        // exceptions at this point, and only need to look to at exit codes.
        if (exitCodeCommit != 0) {
            logger.log(Level.SEVERE, "Failed to commit merge changes.");
            logger.log(Level.SEVERE, String.format("Git command failed with exit code '%d' and error message:", exitCodeCommit));
            logger.log(Level.SEVERE, out.toString());
            listener.getLogger().println(LOG_PREFIX + "Failed to commit merge changes.");
            listener.getLogger().println(String.format(LOG_PREFIX + "Git command failed with exit code '%d' and error message:", exitCodeCommit));
            listener.getLogger().println(out.toString());
            try {
                if (out.toString().contains("nothing to commit")) {
                    logger.fine("Git says nothing to commit.");
                    logger.fine("Setting build description 'Nothing to do':");
                    build.setDescription(String.format("Nothing to do"));
                    logger.fine("Done setting build description.");
                } else {
                    logger.fine("Git could not commit merge changes.");
                    logger.fine("Setting build description 'Failed to commit merge changes':");
                    build.setDescription(String.format("Failed to commit merge changes"));
                    logger.fine("Done setting build description.");
                }
            } catch (IOException ex ) {
                logger.log(Level.SEVERE, "Failed to update build description", ex);
                logger.exiting("AccumulatedCommitStrategy", "integrate");
                // It is not fatal to fail setting build description on the job
                //throw new IntegationFailedExeception(ex);
            }

            if (out.toString().contains("nothing to commit")) {
                logger.fine("Git says nothing to commit.");
                logger.exiting("AccumulatedCommitStrategy", "integrate");// Generated code DONT TOUCH! Bookmark: c9b422ba65a6a142f9cc7f27faeea6e9
                throw new NothingToDoException();
            }
            logger.exiting("AccumulatedCommitStrategy", "integrate");
            throw new IntegationFailedExeception("Could not commit merge change. Git output: " + out.toString());
        }
        // if no exceptions...
        logger.log(Level.INFO, String.format(LOG_PREFIX + "Commit was successful"));
        listener.getLogger().println(String.format(LOG_PREFIX + "Commit was successful"));
    }
    
    @Extension
    public static final class DescriptorImpl extends IntegrationStrategyDescriptor<AccumulatedCommitStrategy> {

		public DescriptorImpl() {
            load();
        }
        
        @Override
        public String getDisplayName() {
            return B_NAME;
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractSCMBridge> bridge) {
            return GitBridge.class.equals(bridge);
        }
        
    }
    
}
