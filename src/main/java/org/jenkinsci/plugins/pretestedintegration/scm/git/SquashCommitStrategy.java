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
/**
 *
 * @author Mads
 */
public class SquashCommitStrategy extends IntegrationStrategy {

    private static final String B_NAME = "Squashed commit";
    private static final Logger logger = Logger.getLogger(SquashCommitStrategy.class.getName());

    @DataBoundConstructor
    public SquashCommitStrategy() { }

    @Override
    public void integrate(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener, AbstractSCMBridge bridge, Commit<?> commit) throws IntegationFailedExeception, NothingToDoException {
        int exitCode = -999;
        int exitCodeCommit = -999;
        GitBridge gitbridge = (GitBridge)bridge;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BuildData gitBuildData = build.getAction(BuildData.class);
        Branch gitDataBranch = gitBuildData.lastBuild.revision.getBranches().iterator().next();
        GitClient client;

        String integrationSHA = "Not specified";
        try {
            integrationSHA = (String)build.getAction(PretestedIntegrationAction.class).getCurrentIntegrationTip().getId();
        } catch (Exception ex) {}

        listener.getLogger().println( String.format( "Preparing to merge changes in commit %s to integration branch %s(%s)", gitDataBranch.getSHA1String(), bridge.getBranch(), integrationSHA) );
        boolean found = false;
        try {
            client = Git.with(listener, build.getEnvironment(listener)).in(build.getWorkspace()).getClient();

            for(Branch b : client.getRemoteBranches()) {
                if(b.getName().equals(gitDataBranch.getName())) {
                    found = true;
                    break;
                }
            }
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "GitClient error", ex);
            throw new IntegationFailedExeception("Unspecified GitClient error",ex);
        }

        if(!found) {
            try {
                build.setDescription(String.format("Nothing to do"));
            } catch (IOException ex) {
                logger.log(Level.FINE, "Failed to update description", ex);
            }
            throw new NothingToDoException();
        }

        try {
            String commitMessage = GitUtils.getCommitMessageUsingSHA1(client.getRepository(), gitDataBranch.getSHA1());
            String branchNameWithNoRemote = GitUtils.removeRemoteFromBranchName(client.getRepository(), gitDataBranch.getName());

            exitCode = gitbridge.git(build, launcher, listener, out, "merge", "--squash", gitDataBranch.getName());
            exitCodeCommit = gitbridge.git(build, launcher, listener, out, "commit", "-m", String.format("%s [%s]",
                                                                                                        commitMessage,
                                                                                                        branchNameWithNoRemote));
        } catch (Exception ex) { /*Handled below */ }

        if (exitCode != 0) {
            listener.getLogger().println("Failed to merge changes. Error message below");
            listener.getLogger().println(out.toString());
            try {
                build.setDescription(String.format("Merge conflict"));
            } catch (IOException ex) {
                logger.log(Level.FINE, "Failed to update description", ex);
            }
            throw new IntegationFailedExeception();
        }

        if (exitCodeCommit != 0 && exitCodeCommit != -999 ) {
            listener.getLogger().println("Failed to commit merged changes. Error message below");
            listener.getLogger().println(out.toString());

            try {
                if(out.toString().contains("nothing to commit")) {
                    build.setDescription(String.format("Nothing to do"));
                } else {
                    build.setDescription(String.format("Failed to commit merges"));
                }
            } catch (IOException ex ) {
                logger.log(Level.FINE, "Failed to update description", ex);
            }

            if(out.toString().contains("nothing to commit")) {
                throw new NothingToDoException();
            }

            throw new IntegationFailedExeception("Could commit merges. Git output: " + out.toString());
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
