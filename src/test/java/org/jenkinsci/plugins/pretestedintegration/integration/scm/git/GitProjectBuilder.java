/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jenkinsci.plugins.pretestedintegration.integration.scm.git;

import hudson.matrix.Axis;
import hudson.matrix.AxisList;
import hudson.matrix.MatrixProject;
import hudson.model.AbstractProject;
import hudson.model.FreeStyleProject;
import hudson.model.Job;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.SubmoduleConfig;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.impl.CleanCheckout;
import hudson.plugins.git.extensions.impl.PruneStaleBranch;
import hudson.slaves.DumbSlave;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.jenkinsci.plugins.pretestedintegration.PretestedIntegrationPostCheckout;
import org.jenkinsci.plugins.pretestedintegration.scm.git.AccumulatedCommitStrategy;
import org.jenkinsci.plugins.pretestedintegration.scm.git.GitBridge;
import org.jenkinsci.plugins.pretestedintegration.scm.git.SquashCommitStrategy;
import org.jvnet.hudson.test.JenkinsRule;

/**
 *
 * @author Mads
 */
public class GitProjectBuilder {

    public enum STRATEGY_TYPE {

        SQUASH, ACCUMULATED
    };

    private JenkinsRule rule;
    private String integrationBranchName = "master";
    private String repoName = "origin";
    private Class<? extends Job> jobType;
    private STRATEGY_TYPE type;
    private List<UserRemoteConfig> gitRepos;
    private boolean useSlave;

    public GitProjectBuilder setRule(JenkinsRule rule) {
        this.rule = rule;
        return this;
    }

    public GitProjectBuilder setIntegrationBranchName(String integrationBranchName) {
        this.integrationBranchName = integrationBranchName;
        return this;
    }

    public GitProjectBuilder setJobType(Class<? extends Job> jobType) {
        this.jobType = jobType;
        return this;
    }

    public GitProjectBuilder setStrategy(STRATEGY_TYPE type) {
        this.type = type;
        return this;
    }

    public GitProjectBuilder setGitRepos(List<UserRemoteConfig> gitRepos) {
        this.gitRepos = gitRepos;
        return this;
    }

    public GitProjectBuilder setUseSlaves(boolean useSlave) {
        this.useSlave = useSlave;
        return this;
    }

    public GitProjectBuilder setRepoName(String repoName) {
        this.repoName = repoName;
        return this;
    }

    public AbstractProject<?,?> generateJenkinsJob() throws IOException, Exception {

        assert jobType.equals(FreeStyleProject.class) || jobType.equals(MatrixProject.class) : "We must use either matrix or free style job types";

        AbstractProject<?,?> project = null;

        GitBridge gitBridge;
        if (type == STRATEGY_TYPE.SQUASH) {
            gitBridge = new GitBridge(new SquashCommitStrategy(), integrationBranchName, repoName);
        } else {
            gitBridge = new GitBridge(new AccumulatedCommitStrategy(), integrationBranchName, repoName);
        }

        if (jobType.equals(FreeStyleProject.class)) {
            project = rule.createFreeStyleProject();
            ((FreeStyleProject)project).getBuildWrappersList().add(new PretestedIntegrationBuildWrapper(gitBridge));
            project.getPublishersList().add(new PretestedIntegrationPostCheckout());
        } else if(jobType.equals(MatrixProject.class)) {
            project = rule.createProject(MatrixProject.class);
            ((MatrixProject)project).getBuildWrappersList().add(new PretestedIntegrationBuildWrapper(gitBridge));
            project.getPublishersList().add(new PretestedIntegrationPostCheckout());
            ((MatrixProject)project).setAxes(new AxisList(new Axis("X", Arrays.asList("X1","X2"))));
        }

        if (useSlave) {
            DumbSlave onlineSlave = rule.createOnlineSlave();
            project.setAssignedNode(onlineSlave);
        }

        List<GitSCMExtension> gitSCMExtensions = new ArrayList<>();
        gitSCMExtensions.add(new PruneStaleBranch());
        gitSCMExtensions.add(new CleanCheckout());

        GitSCM gitSCM = new GitSCM(gitRepos,
                Collections.singletonList(new BranchSpec("*/ready/**")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null, gitSCMExtensions);

        project.setScm(gitSCM);

        return project;
    }

}
