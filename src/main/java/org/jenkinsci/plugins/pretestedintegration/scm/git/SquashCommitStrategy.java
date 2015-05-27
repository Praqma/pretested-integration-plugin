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
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.pretestedintegration.*;
import org.jenkinsci.plugins.pretestedintegration.exceptions.IntegationFailedExeception;
import org.jenkinsci.plugins.pretestedintegration.exceptions.NothingToDoException;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jenkinsci.plugins.pretestedintegration.exceptions.UnsupportedConfigurationException;

/**
 *
 * @author Mads
 */
public class SquashCommitStrategy extends IntegrationStrategy {

    private static final String B_NAME = "Squashed commit";
    private static final Logger logger = Logger.getLogger(SquashCommitStrategy.class.getName());
    private static final String LOG_PREFIX = "[PREINT] ";
    private static final int unLikelyExitCode = -999; // An very unlikely exit code, that we use as default


    @DataBoundConstructor
    public SquashCommitStrategy() { }

    @Override
    public void integrate(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener, AbstractSCMBridge bridge) throws IntegationFailedExeception, NothingToDoException, UnsupportedConfigurationException {
        logger.entering("SquashCommitStrategy", "integrate", new Object[]{build, listener, bridge, launcher});// Generated code DONT TOUCH! Bookmark: 36174744d49c892c3aeed5e2bc933991
        int exitCodeMerge = unLikelyExitCode;
        int exitCodeCommit = unLikelyExitCode;
        GitBridge gitbridge = (GitBridge) bridge;


        ByteArrayOutputStream out = new ByteArrayOutputStream();

        //TODO: How can you add more than 1 action, MultiSCM plugin with two 
        // seperate gits?
        BuildData gitBuildData = gitbridge.checkAndDetermineRelevantBuildData(build.getActions(BuildData.class));

        Branch gitDataBranch = gitBuildData.lastBuild.revision.getBranches().iterator().next();
        GitClient client;

//        String integrationSHA = "Not specified";
//        try {
//            logger.fine("Getting current integration SHA");
//            integrationSHA = (String) build.getAction(PretestedIntegrationAction.class).getCurrentIntegrationTip().getId();
//            logger.fine("Found integration SHA");
//        } catch (Exception ex) {
//            logger.log(Level.SEVERE, "Integrate() error, integration SHA not found", ex);
//        }

        logger.log(Level.INFO, String.format("Preparing to merge changes in commit %s on development branch %s to integration branch %s", gitDataBranch.getSHA1String(), gitDataBranch.getName(), gitbridge.getBranch()));
        listener.getLogger().println(String.format(LOG_PREFIX + "Preparing to merge changes in commit %s on development branch %s to integration branch %s", gitDataBranch.getSHA1String(), gitDataBranch.getName(), gitbridge.getBranch()));
        boolean found = false;
        try {
            logger.fine("Resolving and getting git client from workspace:");
            client = gitbridge.findScm(build).createClient(listener, build.getEnvironment(listener), build, build.getWorkspace());

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
            logger.exiting("SquashCommitStrategy", "integrate");// Generated code DONT TOUCH! Bookmark: c9b422ba65a6a142f9cc7f27faeea6e9
            throw new IntegationFailedExeception("Unspecified GitClient error", ex);
        }

        if (!found) {
            logger.fine("Found no remote branches.");
            try {
                logger.fine("Setting build description 'Nothing to do':");
                build.setDescription(String.format("Nothing to do"));
                logger.fine("Done setting build description.");
            } catch (IOException ex) {
                logger.log(Level.FINE, "Failed to update build description", ex);
            }
            String msg = GitMessages.NoRelevantSCMchange(gitDataBranch != null ? gitDataBranch.getName() : "null");
            logger.log(Level.WARNING, msg);
            logger.exiting("SquashCommitStrategy", "integrate");// Generated code DONT TOUCH! Bookmark: c9b422ba65a6a142f9cc7f27faeea6e9
            throw new NothingToDoException(msg);
        }

        String commitAuthor; //leaving un-assigned, want to fail later if not assigned
        try {
            logger.log(Level.INFO, String.format(LOG_PREFIX + "Collecting commit messages on development branch (for debug printing): %s", gitDataBranch.getName()));
            listener.getLogger().println(String.format(LOG_PREFIX + "Collecting commit messages on development branch (for debug printing): %s", gitDataBranch.getName()));
            logger.log(Level.INFO, String.format(LOG_PREFIX + "Done collecting commit messages"));
            listener.getLogger().println(String.format(LOG_PREFIX + "Done collecting commit messages"));

            // Finding author of the commit, re-using a call back method like 'FindCommitMessageCallback'
            logger.log(Level.INFO, String.format(LOG_PREFIX + "Collecting author of last commit on development branch"));
            listener.getLogger().println(String.format(LOG_PREFIX + "Collecting author of last commit on development branch"));
            commitAuthor = client.withRepository(new FindCommitAuthorCallback(listener, gitDataBranch.getSHA1()));
            logger.log(Level.INFO, String.format(LOG_PREFIX + "Done colecting last commit author: %s", commitAuthor));
            listener.getLogger().println(String.format(LOG_PREFIX + "Done colecting last commit author: %s", commitAuthor));

            logger.info("Starting squash merge - without commit:");
            listener.getLogger().println(String.format(LOG_PREFIX + "Starting squash merge - without commit:"));
            listener.getLogger().println( String.format("%smerge --squash %s",LOG_PREFIX, gitDataBranch.getName()) );            
            client.merge().setSquash(true).setRevisionToMerge(gitDataBranch.getSHA1()).execute();
            /*
            TODO: We do not have an exit code for the merge anymore. We get exceptions
            */
            //exitCodeMerge = gitbridge.git(build, launcher, listener, out, "merge", "--squash", gitDataBranch.getName());
            
            
            
            exitCodeMerge = 0;
            logger.info("Squash merge done");
            listener.getLogger().println(String.format(LOG_PREFIX + "Squash merge done"));
        } catch (Exception ex) {
            // We handle all exceptions here, as we will not continue with
            // anything if there is problems, even if it is only null pointer
            // in debug printing.
            // So we throw an exception, that is handled by the build wrapper
            // which also print out the exception to the job console to let the
            // user easily investigate.
            logger.log(Level.SEVERE, "Exception while merging. Logging exception", ex);
            listener.getLogger().println(LOG_PREFIX + String.format("Exception while merging. Logging exception msg: %s", ex.getMessage()));
            logger.exiting("SquashCommitStrategy", "integrate-mergeFailure");            
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
                logger.exiting("SquashCommitStrategy", "integrate");
                // It is not fatal to fail setting build description on the job
                // throw new IntegationFailedExeception(ex);
            }
            logger.exiting("SquashCommitStrategy", "integrate");// Generated code DONT TOUCH! Bookmark: c9b422ba65a6a142f9cc7f27faeea6e9
            throw new IntegationFailedExeception("Could not merge changes. Git output: " + out.toString());
        }
        // if no exceptions...
        logger.log(Level.INFO, String.format(LOG_PREFIX + "Merge was successful"));
        listener.getLogger().println(String.format(LOG_PREFIX + "Merge was successful"));


        try {
            logger.info("Starting to commit squash merge changes:");
            listener.getLogger().println(String.format(LOG_PREFIX + "Starting to commit squash merge changes:"));
            exitCodeCommit = gitbridge.git(build, launcher, listener, out, "commit", "--no-edit", "--author=" + commitAuthor);
            logger.info("Commit of squashed merge done");
            listener.getLogger().println(String.format(LOG_PREFIX + "Commit of squashed merge done"));

        } catch (Exception ex) {
            // We handle all exceptions here, as we will not continue with
            // anything if there is problems, even if it is only null pointer
            // in debug printing.
            // So we throw an exception, that is handled by the build wrapper
            // which also print out the exception to the job console to let the
            // user easily investigate.
            logger.log(Level.SEVERE, "Exception while merging or comitting, logging exception", ex);
            listener.getLogger().println(LOG_PREFIX + String.format("Exception while committing. Logging exception msg: %s", ex.getMessage()));
            logger.exiting("SquashCommitStrategy", "integrate-commitFailure");
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
                logger.exiting("SquashCommitStrategy", "integrate");
                // It is not fatal to fail setting build description on the job
                //throw new IntegationFailedExeception(ex);
            }

            if (out.toString().contains("nothing to commit")) {
                logger.fine("Git says nothing to commit.");
                logger.exiting("SquashCommitStrategy", "integrate");// Generated code DONT TOUCH! Bookmark: c9b422ba65a6a142f9cc7f27faeea6e9
                throw new NothingToDoException();
            }

            logger.exiting("SquashCommitStrategy", "integrate");// Generated code DONT TOUCH! Bookmark: c9b422ba65a6a142f9cc7f27faeea6e9
            throw new IntegationFailedExeception("Could not commit merge changes. Git output: " + out.toString());
        }
        // if no exceptions...
        logger.log(Level.INFO, String.format(LOG_PREFIX + "Commit was successful"));
        listener.getLogger().println(String.format(LOG_PREFIX + "Commit was successful"));
    }

    @Extension
    public static final class DescriptorImpl extends IntegrationStrategyDescriptor<SquashCommitStrategy> {

		public DescriptorImpl() {
            load();
        }

        public String getDisplayName() {
            return B_NAME;
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractSCMBridge> bridge) {
            return GitBridge.class.equals(bridge);
        }
    }

}
