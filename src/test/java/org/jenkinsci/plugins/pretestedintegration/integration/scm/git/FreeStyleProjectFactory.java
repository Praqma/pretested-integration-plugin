/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jenkinsci.plugins.pretestedintegration.integration.scm.git;

import antlr.ANTLRException;
import hudson.model.FreeStyleProject;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.SubmoduleConfig;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.impl.CleanCheckout;
import hudson.plugins.git.extensions.impl.PruneStaleBranch;
import hudson.triggers.SCMTrigger;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jenkinsci.plugins.pretestedintegration.PretestedIntegrationBuildWrapper;
import org.jenkinsci.plugins.pretestedintegration.PretestedIntegrationPostCheckout;
import org.jenkinsci.plugins.pretestedintegration.scm.git.AccumulatedCommitStrategy;
import org.jenkinsci.plugins.pretestedintegration.scm.git.GitBridge;
import org.jenkinsci.plugins.pretestedintegration.scm.git.SquashCommitStrategy;

/**
 *
 * @author Mads
 */
public class FreeStyleProjectFactory {
    public enum STRATEGY_TYPE { SQUASH, ACCUMULATED };
    public static final File GIT_DIR = new File("test-repo/.git");
    
    public static FreeStyleProject configurePretestedIntegrationPlugin(FreeStyleProject project, FreeStyleProjectFactory.STRATEGY_TYPE type) throws IOException, ANTLRException, InterruptedException {
        return configurePretestedIntegrationPlugin(project, type, Collections.singletonList(new UserRemoteConfig("file://" + GIT_DIR.getAbsolutePath(), null, null, null)));
    }
    
    public static FreeStyleProject configurePretestedIntegrationPlugin(FreeStyleProject project, FreeStyleProjectFactory.STRATEGY_TYPE type, List<UserRemoteConfig> repoList) throws IOException, ANTLRException, InterruptedException {
        GitBridge gitBridge = null;
        if(type == STRATEGY_TYPE.SQUASH) {
            gitBridge = new GitBridge(new SquashCommitStrategy(), "master");
        } else {
            gitBridge = new GitBridge(new AccumulatedCommitStrategy(), "master");
        }

        project.getBuildWrappersList().add(new PretestedIntegrationBuildWrapper(gitBridge));
        project.getPublishersList().add(new PretestedIntegrationPostCheckout());

        List<GitSCMExtension> gitSCMExtensions = new ArrayList<GitSCMExtension>();
        gitSCMExtensions.add(new PruneStaleBranch());
        gitSCMExtensions.add(new CleanCheckout());

        GitSCM gitSCM = new GitSCM(Collections.singletonList(new UserRemoteConfig(GIT_DIR.getAbsolutePath(), null, null, null)),
                Collections.singletonList(new BranchSpec("origin/ready/**")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null, gitSCMExtensions);

        project.setScm(gitSCM);

        SCMTrigger scmTrigger = new SCMTrigger("@daily", true);
        project.addTrigger(scmTrigger);

        scmTrigger.start(project, true);
        scmTrigger.new Runner().run();

        Thread.sleep(1000);

        return project;
    }
}
