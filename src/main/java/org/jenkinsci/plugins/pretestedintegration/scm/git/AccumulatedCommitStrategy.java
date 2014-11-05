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
import org.jenkinsci.plugins.pretestedintegration.Commit;
import org.jenkinsci.plugins.pretestedintegration.exceptions.IntegationFailedExeception;
import org.jenkinsci.plugins.pretestedintegration.IntegrationStrategy;
import org.jenkinsci.plugins.pretestedintegration.IntegrationStrategyDescriptor;
import org.jenkinsci.plugins.pretestedintegration.exceptions.NothingToDoException;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 *
 * @author Mads
 */
public class AccumulatedCommitStrategy extends IntegrationStrategy {
    
    private static final String B_NAME = "Accumulated commit";
    private static final Logger logger = Logger.getLogger(AccumulatedCommitStrategy.class.getName());

    @DataBoundConstructor
    public AccumulatedCommitStrategy() { }

    @Override
    public void integrate(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener, AbstractSCMBridge bridge, Commit<?> commit) throws IntegationFailedExeception, NothingToDoException {
        logger.entering("AccumulatedCommitStrategy", "integrate", new Object[] { build, listener, bridge, launcher, commit });// Generated code DONT TOUCH! Bookmark: ee74dbf7df6fa51582ccc15f5fee72da
		int exitCode = -999;

        GitClient client;

        GitBridge gitbridge = (GitBridge)bridge;
        
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BuildData gitBuildData = build.getAction(BuildData.class);
        Branch gitDataBranch = gitBuildData.lastBuild.revision.getBranches().iterator().next();
        boolean found = false;
        
        try {
            client = Git.with(listener, build.getEnvironment(listener)).in(gitbridge.resolveWorkspace(build, listener)).getClient();
            
            logger.fine("Finding remote branches");
            for(Branch b : client.getRemoteBranches()) {
                logger.fine(String.format("Found remote branch %s", b.getName()));
                
                if(b.getName().equals(gitDataBranch.getName())) {                    
                    found = true;
                    break;
                }
            }
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "GitClient error", ex);
            logger.exiting("AccumulatedCommitStrategy", "integrate");// Generated code DONT TOUCH! Bookmark: 26b6ce59c6edbad7afa29f96febc6fd7
			throw new IntegationFailedExeception("GitClient error, unspecified", ex);
        }
        
        if(!found) {
            try {
                build.setDescription(String.format("Noting to do"));
            } catch (Exception ex) {
                logger.log(Level.FINE, "Failed to update description", ex);
            }
            logger.log(Level.WARNING, String.format("Nothing to do. The branch name (%s) contained in the git build data object, did not match a remote branch name", gitDataBranch != null ? gitDataBranch.getName() : "null"));
            logger.exiting("AccumulatedCommitStrategy", "integrate");// Generated code DONT TOUCH! Bookmark: 26b6ce59c6edbad7afa29f96febc6fd7
			throw new NothingToDoException();
        }

        listener.getLogger().println( String.format( "Preparing to merge changes in commit %s to integration branch %s", (String) commit.getId(), gitbridge.getBranch() ) );
        try {
            
            String commitMessage = client.withRepository(new FindCommitMessageCallback(listener, gitDataBranch.getSHA1()));                                 
            exitCode = gitbridge.git(build, launcher, listener, out, "merge","-m", String.format("%s\n[%s]", commitMessage, gitDataBranch.getName()), (String) commit.getId(), "--no-ff");
        } catch (Exception ex) {
            logger.exiting("AccumulatedCommitStrategy ", "integrate-mergeFailure");// Generated code DONT TOUCH! Bookmark: 26b6ce59c6edbad7afa29f96febc6fd7
            logger.log(Level.WARNING, "Exception while merging, logging exception",ex);
			throw new IntegationFailedExeception(ex);
        }
        
        if (exitCode > 0) {
            listener.getLogger().println("Failed to merge changes.");
            listener.getLogger().println(out.toString());
            try {
                build.setDescription(String.format("Merge conflict"));
            }  catch (IOException ex) {
                logger.exiting("AccumulatedCommitStrategy", "integrate-setDescription() failed");// Generated code DONT TOUCH! Bookmark: 26b6ce59c6edbad7afa29f96febc6fd7
				throw new IntegationFailedExeception(ex);
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
