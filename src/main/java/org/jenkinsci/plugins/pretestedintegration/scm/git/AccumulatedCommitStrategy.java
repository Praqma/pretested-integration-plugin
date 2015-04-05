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

    @DataBoundConstructor
    public AccumulatedCommitStrategy() { }

    @Override
    public void integrate(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener, AbstractSCMBridge bridge) throws IntegationFailedExeception, NothingToDoException, UnsupportedConfigurationException {
        logger.entering("AccumulatedCommitStrategy", "integrate", new Object[] { build, listener, bridge, launcher });// Generated code DONT TOUCH! Bookmark: ee74dbf7df6fa51582ccc15f5fee72da
		int exitCode = -999;

        GitClient client;

        GitBridge gitbridge = (GitBridge)bridge;
        
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BuildData gitBuildData = gitbridge.checkAndDetermineRelevantBuildData(build.getActions(BuildData.class));
        String commit = gitBuildData.lastBuild.revision.getSha1String();
        
        //TODO: Implement robustness, in which situations does this one contain 
        // multiple revisons, when two branches point to the same commit? 
        // (JENKINS-24909). Check branch spec before doing anything     
        Branch gitDataBranch = gitBuildData.lastBuild.revision.getBranches().iterator().next();
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

        listener.getLogger().println(String.format(LOG_PREFIX + "Preparing to merge changes in commit %s to integration branch %s", commit, gitbridge.getBranch()));
        try {
            // FIXME I don't like this call back design, where we collect data 
            // for commit message based on a series of commits which aren't 
            // really ensure to match what the 'git merge' command ends up 
            // merging.
            // The method that get all commits from a branch walk the git tree 
            // using JGit, so it is complete independent from the following
            // merge operation. Worst case is that the merge commit message is
            // based on other commits than the actual merge commit consist of.
            String commits = client.withRepository(new GetAllCommitsFromBranchCallback(listener, gitDataBranch.getSHA1(), gitbridge.getBranch()));
            String headerLine = String.format("Accumulated commit of the following from branch '%s':%n", gitDataBranch.getName());
            exitCode = gitbridge.git(build, launcher, listener, out, "merge", "-m", String.format("%s%n%s", headerLine, commits), commit, "--no-ff");

        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Exception while merging, logging exception", ex);
            logger.exiting("AccumulatedCommitStrategy ", "integrate-mergeFailure"); // Generated code DONT TOUCH! Bookmark: 26b6ce59c6edbad7afa29f96febc6fd7
			throw new IntegationFailedExeception(ex);
        }
        
        if (exitCode > 0) {
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
            }  catch (IOException ex) {
                logger.log(Level.SEVERE, "Failed to update build description", ex);
                logger.exiting("AccumulatedCommitStrategy", "integrate-setDescription() failed");// Generated code DONT TOUCH! Bookmark: 26b6ce59c6edbad7afa29f96febc6fd7
                // It is not fatal to fail setting build description on the job
                // throw new IntegationFailedExeception(ex);
            }
            logger.exiting("AccumulatedCommitStrategy", "integrate");// Generated code DONT TOUCH! Bookmark: 26b6ce59c6edbad7afa29f96febc6fd7
			throw new IntegationFailedExeception();
        }
        
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
