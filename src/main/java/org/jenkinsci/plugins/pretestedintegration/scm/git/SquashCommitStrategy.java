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

    @DataBoundConstructor
    public SquashCommitStrategy() { }

    @Override
    public void integrate(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener, AbstractSCMBridge bridge) throws IntegationFailedExeception, NothingToDoException, UnsupportedConfigurationException {
        logger.entering("SquashCommitStrategy", "integrate", new Object[] { build, listener, bridge, launcher });// Generated code DONT TOUCH! Bookmark: 36174744d49c892c3aeed5e2bc933991
		int exitCode = -999;
        int exitCodeCommit = -999;
        GitBridge gitbridge = (GitBridge)bridge;
        

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        
        //TODO: How can you add more than 1 action, MultiSCM plugin with two 
        // seperate gits?
        BuildData gitBuildData = gitbridge.checkAndDetermineRelevantBuildData(build.getActions(BuildData.class));
        
        Branch gitDataBranch = gitBuildData.lastBuild.revision.getBranches().iterator().next();
        GitClient client;

        String integrationSHA = "Not specified";
        try {
            logger.fine("Getting current integration SHA");
            integrationSHA = (String) build.getAction(PretestedIntegrationAction.class).getCurrentIntegrationTip().getId();
            logger.fine("Found integration SHA");
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Integrate() error, integration SHA not found", ex);
        }

        listener.getLogger().println(String.format(LOG_PREFIX + "Preparing to merge changes in commit %s to integration branch %s(%s)", gitDataBranch.getSHA1String(), bridge.getBranch(), integrationSHA));
        boolean found = false;
        try {
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

        listener.getLogger().println(String.format(LOG_PREFIX + "Preparing to merge changes from branch %s", gitbridge.getBranch()));
        try {
            logger.fine("Collecting and gathering commit message for debug out put after commit:");
            String commitMessage = client.withRepository(new FindCommitMessageCallback(listener, gitDataBranch.getSHA1()));
            logger.fine("Done creating commit message");

            logger.fine("Starting squash merge - without commit:");
            exitCode = gitbridge.git(build, launcher, listener, out, "merge", "--squash", gitDataBranch.getName());
            logger.fine("Squash merge done.");
            logger.fine("Starting to commit prepared squash merge:");
            exitCodeCommit = gitbridge.git(build, launcher, listener, out, "commit", "--no-edit");
            logger.fine("Commit of squashed merge done.");

            listener.getLogger().println(String.format(LOG_PREFIX + "Commit message:%n%s", commitMessage));
        } catch (Exception ex) {
            /*Handled below */
            logger.log(Level.SEVERE, "Exception while merging, logging exception", ex);
        }

        if (exitCode != 0) {
            logger.log(Level.SEVERE, "Failed to merge changes.");
            logger.log(Level.SEVERE, String.format("Git command failed with exit code '%d' and error message:", exitCode));
            logger.log(Level.SEVERE, out.toString());
            listener.getLogger().println(LOG_PREFIX + "Failed to merge changes.");
            listener.getLogger().println(String.format(LOG_PREFIX + "Git command failed with exit code '%d' and error message:", exitCode));
            listener.getLogger().println(LOG_PREFIX + out.toString());

            try {
                logger.fine("Setting build description 'Merge conflict':");
                build.setDescription(String.format("Merge conflict"));
                logger.fine("Done setting build description.");
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Failed to update build description", ex);
                logger.exiting("SquashCommitStrategy", "integrate");
                // It is not fatal to fail setting build description on the job
                // throw new IntegationFailedExeception(ex);
            }
            logger.exiting("SquashCommitStrategy", "integrate");// Generated code DONT TOUCH! Bookmark: c9b422ba65a6a142f9cc7f27faeea6e9
            throw new IntegationFailedExeception();
        }

        if (exitCodeCommit != 0 && exitCodeCommit != -999 ) {
            logger.log(Level.SEVERE, "Failed to commit merged changes.");
            logger.log(Level.SEVERE, String.format("Git command failed with exit code '%d' and error message:", exitCodeCommit));
            logger.log(Level.SEVERE, out.toString());
            listener.getLogger().println(LOG_PREFIX + "Failed to commit merged changes.");
            listener.getLogger().println(String.format(LOG_PREFIX + "Git command failed with exit code '%d' and error message:", exitCodeCommit));
            listener.getLogger().println(LOG_PREFIX + out.toString());

            try {
                if (out.toString().contains("nothing to commit")) {
                    logger.fine("Git says nothing to commit.");
                    logger.fine("Setting build description 'Nothing to do':");
                    build.setDescription(String.format("Nothing to do"));
                    logger.fine("Done setting build description.");
                } else {
                    logger.fine("Git couldnot commit merges.");
                    logger.fine("Setting build description 'Failed to commit merges':");
                    build.setDescription(String.format("Failed to commit merges"));
                    logger.fine("Done setting build description.");
                }
            } catch (IOException ex ) {
                logger.log(Level.SEVERE, "Failed to update build description", ex);
                logger.exiting("SquashCommitStrategy", "integrate");
                // It is not fatal to fail setting build description on the job
                //throw new IntegationFailedExeception(ex);
            }

            if (out.toString().contains("nothing to commit")) {
                logger.exiting("SquashCommitStrategy", "integrate");// Generated code DONT TOUCH! Bookmark: c9b422ba65a6a142f9cc7f27faeea6e9
                throw new NothingToDoException();
            }

            logger.exiting("SquashCommitStrategy", "integrate");// Generated code DONT TOUCH! Bookmark: c9b422ba65a6a142f9cc7f27faeea6e9
            throw new IntegationFailedExeception("Could not commit merges. Git output: " + out.toString());
        }
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
