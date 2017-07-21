/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jenkinsci.plugins.pretestedintegration.scm.git;

import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.Revision;
import hudson.plugins.git.extensions.GitClientType;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.pretestedintegration.IntegrationStrategy;
import org.jenkinsci.plugins.pretestedintegration.IntegrationStrategyDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 *
 * @author florenthaxha
 */
public class GitPhlowExtension extends GitSCMExtension {

    private IntegrationStrategy integrationStrategy;

    private String branch;

    private String repoName = "origin";

    @DataBoundConstructor
    public GitPhlowExtension(IntegrationStrategy integrationStrategy, String branch) {
        this.integrationStrategy = integrationStrategy;
        this.branch = branch;
    }

    @Override
    public Revision decorateRevisionToBuild(GitSCM scm, Run<?, ?> build, GitClient git, TaskListener listener, Revision marked, Revision rev) throws IOException, InterruptedException, GitException {
        return super.decorateRevisionToBuild(scm, build, git, listener, marked, rev); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public GitClientType getRequiredClient() {
        return GitClientType.GITCLI;
    }

    @Symbol("git-phlow")
    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {

        @Override
        public String getDisplayName() {
            return "Git Phlow Extension";
        }

        public IntegrationStrategy getDefaultStrategy() {
            return new SquashCommitStrategy();
        }

        /**
         * @return Descriptors of the Integration Strategies
         */
        public List<IntegrationStrategyDescriptor<?>> getIntegrationStrategies() {
            List<IntegrationStrategyDescriptor<?>> list = new ArrayList<>();
            for (IntegrationStrategyDescriptor<?> descr : IntegrationStrategy.all()) {
                list.add(descr);
            }

            return list;
        }

    }

    public IntegrationStrategy getIntegrationStrategy() {
        return integrationStrategy;
    }

    public void setIntegrationStrategy(IntegrationStrategy integrationStrategy) {
        this.integrationStrategy = integrationStrategy;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public String getRepoName() {
        return repoName;
    }

    @DataBoundSetter
    public void setRepoName(String repoName) {
        this.repoName = repoName;
    }
}
