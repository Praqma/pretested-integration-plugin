/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jenkinsci.plugins.pretestedintegration.integration.scm.git;

import hudson.matrix.Axis;
import hudson.matrix.MatrixProject;
import hudson.model.AbstractProject;
import hudson.model.FreeStyleProject;
import hudson.model.TopLevelItem;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.SubmoduleConfig;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.impl.CleanCheckout;
import hudson.plugins.git.extensions.impl.PruneStaleBranch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.jenkinsci.plugins.pretestedintegration.PretestedIntegrationPostCheckout;
import org.jenkinsci.plugins.pretestedintegration.scm.git.AccumulatedCommitStrategy;
import org.jenkinsci.plugins.pretestedintegration.scm.git.PretestedIntegrationAsGitPluginExt;
import org.jenkinsci.plugins.pretestedintegration.scm.git.SquashCommitStrategy;
import org.jvnet.hudson.test.JenkinsRule;

/**
 *
 * @author Mads
 */
public class MatrixProjectBuilder {
    
    private JenkinsRule rule;
    private String integrationBranchName = "master";
    private String repoName = "origin";
    private Class<? extends TopLevelItem> jobType;
    private TestUtilsFactory.STRATEGY_TYPE type;
    private List<UserRemoteConfig> gitRepos;
    private boolean useSlave;
    
    public MatrixProjectBuilder setRule(JenkinsRule rule) {
        this.rule = rule;
        return this;
    }
    
    public MatrixProjectBuilder setIntegrationBranchName(String integrationBranchName) {
        this.integrationBranchName = integrationBranchName;
        return this;
    }
    
    public MatrixProjectBuilder setJobType(Class<? extends TopLevelItem> jobType) {
        this.jobType = jobType;
        return this;
    }
    
    public MatrixProjectBuilder setStrategy(TestUtilsFactory.STRATEGY_TYPE type) {
        this.type = type;
        return this;
    }
    
    public MatrixProjectBuilder setGitRepos(List<UserRemoteConfig> gitRepos) {
        this.gitRepos = gitRepos;
        return this;
    }
    
    public MatrixProjectBuilder setUseSlaves(boolean useSlave) {
        this.useSlave = useSlave;
        return this;
    }
    
    public MatrixProjectBuilder setRepoName(String repoName) {
        this.repoName = repoName;
        return this;
    }
    
    public AbstractProject<?,?> generateJenkinsJob() throws Exception {
        assert jobType.equals(FreeStyleProject.class) || jobType.equals(MatrixProject.class) : "We must use either matrix or free style job types";
        //TODO: This does not work with type
        MatrixProject project = rule.createProject(MatrixProject.class, "GeneratedJenkinsJob");

        List<GitSCMExtension> gitSCMExtensions = new ArrayList<>();
        gitSCMExtensions.add(new PruneStaleBranch());
        gitSCMExtensions.add(new CleanCheckout());
        gitSCMExtensions.add(new PretestedIntegrationAsGitPluginExt(type == TestUtilsFactory.STRATEGY_TYPE.SQUASH ? new SquashCommitStrategy() : new AccumulatedCommitStrategy(), integrationBranchName, repoName));
    
        GitSCM gitSCM = new GitSCM(gitRepos,
                Collections.singletonList(new BranchSpec("*/ready/**")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null, gitSCMExtensions);
    
        project.setScm(gitSCM);
        project.getPublishersList().add(new PretestedIntegrationPostCheckout());

        project.getAxes().add(new Axis("OS", Arrays.asList("Linux", "Windows","OS2")));

    
        return project;
    }


}
