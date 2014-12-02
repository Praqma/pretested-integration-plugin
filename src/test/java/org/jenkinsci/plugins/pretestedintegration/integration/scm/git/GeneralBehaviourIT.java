/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jenkinsci.plugins.pretestedintegration.integration.scm.git;

import hudson.model.AbstractBuild;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.SubmoduleConfig;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.impl.CleanCheckout;
import hudson.plugins.git.extensions.impl.PruneStaleBranch;
import hudson.plugins.git.extensions.impl.RelativeTargetDirectory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import static junit.framework.Assert.assertTrue;
import static junit.framework.TestCase.assertEquals;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.jenkinsci.plugins.pretestedintegration.exceptions.UnsupportedConfigurationException;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.JenkinsRule;

/**
 *
 * @author Mads
 */
public class GeneralBehaviourIT {
    
    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();
    
    private Repository repository;
    
    @After
    public void tearDown() throws Exception {
        if(repository != null) {
            repository.close();
            if (repository.getDirectory().getParentFile().exists()) {
                FileUtils.deleteQuietly(repository.getDirectory().getParentFile());
            }
        }
    }

    @Test
    public void failWhenRepNameIsBlankAndGitHasMoreThanOneRepo() throws Exception {
        repository = TestUtilsFactory.createValidRepository("test-repo");
        Repository repository2 = TestUtilsFactory.createValidRepository("test-repo2");
        
        Git git = new Git(repository);
        git.checkout().setName("master").call();

        List<UserRemoteConfig> config = Arrays.asList(new UserRemoteConfig("file://" + repository.getDirectory().getAbsolutePath(), null, null, null), new UserRemoteConfig("file://" + repository2.getDirectory().getAbsolutePath(), null, null, null));
        
        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.ACCUMULATED, config, null, true);
        TestUtilsFactory.triggerProject(project);
        
        assertEquals(1, jenkinsRule.jenkins.getQueue().getItems().length);

        jenkinsRule.waitUntilNoActivityUpTo(60000);
        
        TestUtilsFactory.destroyRepo(repository2);
        for(Iterator<FreeStyleBuild> builds = project.getBuilds().iterator(); builds.hasNext();) {
            AbstractBuild<?,?> b = builds.next();
            String text = jenkinsRule.createWebClient().getPage(b, "console").asText();
            System.out.println("=====BUILD-LOG=====");
            System.out.println(text);
            System.out.println("=====BUILD-LOG=====");
            if(text.contains("push origin :ready/feature_1")) {
                assertTrue(b.getResult().equals(Result.SUCCESS)); 
            } else {
                assertTrue(b.getResult().equals(Result.NOT_BUILT));
            }
        }
                
    }
    
    /**
     * 1.1
     * 
     **/
    @Test
    public void remoteOrigin1WithMoreThan1RepoShouldBeSuccessfulFirstRepo() throws Exception {
        repository = TestUtilsFactory.createValidRepository("test-repo");
        Repository repository2 = TestUtilsFactory.createValidRepository("test-repo2");
        
        Git git = new Git(repository);
        git.checkout().setName("master").call();

        List<UserRemoteConfig> config = Arrays.asList(new UserRemoteConfig("file://" + repository.getDirectory().getAbsolutePath(), "origin1", null, null), new UserRemoteConfig("file://" + repository2.getDirectory().getAbsolutePath(), "magic", null, null));
        
        List<GitSCMExtension> gitSCMExtensions = new ArrayList<GitSCMExtension>();
        gitSCMExtensions.add(new PruneStaleBranch());
        gitSCMExtensions.add(new CleanCheckout());
                        
        GitSCM gitSCM = new GitSCM(config,
        Collections.singletonList(new BranchSpec("*/ready/**")),
            false, Collections.<SubmoduleConfig>emptyList(),
            null, null, gitSCMExtensions);
        
        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.ACCUMULATED, config, "origin1", true);
        project.setScm(gitSCM);
        TestUtilsFactory.triggerProject(project);
        
        assertEquals(1, jenkinsRule.jenkins.getQueue().getItems().length);

        jenkinsRule.waitUntilNoActivityUpTo(60000);
    
        int nextBuildNumber = project.getNextBuildNumber();
        FreeStyleBuild build = project.getBuildByNumber(nextBuildNumber - 1);

        //Show the log for the latest build
        String text = jenkinsRule.createWebClient().getPage(build, "console").asText();
        System.out.println("=====BUILD-LOG=====");
        System.out.println(text);
        System.out.println("=====BUILD-LOG=====");
                
        assertTrue(build.getResult().isWorseOrEqualTo(Result.SUCCESS));
        repository2.close();
        if (repository2.getDirectory().getParentFile().exists()) {
            FileUtils.deleteQuietly(repository2.getDirectory().getParentFile());
        }
    }
    
    /**
     * 1.2
     * 
     **/
    @Test
    public void remoteOrigin1WithMoreThan1RepoShouldBeSuccessfulSecondRepo() throws Exception {
        repository = TestUtilsFactory.createValidRepository("test-repo");
        Repository repository2 = TestUtilsFactory.createValidRepository("test-repo2");
        
        Git git = new Git(repository);
        git.checkout().setName("master").call();

        List<UserRemoteConfig> config = Arrays.asList(new UserRemoteConfig("file://" + repository.getDirectory().getAbsolutePath(), "magic", null, null), new UserRemoteConfig("file://" + repository2.getDirectory().getAbsolutePath(), "orgin1", null, null));
        
        List<GitSCMExtension> gitSCMExtensions = new ArrayList<GitSCMExtension>();
        gitSCMExtensions.add(new PruneStaleBranch());
        gitSCMExtensions.add(new CleanCheckout());
                        
        GitSCM gitSCM = new GitSCM(config,
        Collections.singletonList(new BranchSpec("*/ready/**")),
            false, Collections.<SubmoduleConfig>emptyList(),
            null, null, gitSCMExtensions);
        
        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.ACCUMULATED, config, "origin1", true);
        project.setScm(gitSCM);
        TestUtilsFactory.triggerProject(project);
        
        assertEquals(1, jenkinsRule.jenkins.getQueue().getItems().length);

        jenkinsRule.waitUntilNoActivityUpTo(60000);
    
        int nextBuildNumber = project.getNextBuildNumber();
        FreeStyleBuild build = project.getBuildByNumber(nextBuildNumber - 1);

        //Show the log for the latest build
        String text = jenkinsRule.createWebClient().getPage(build, "console").asText();
        System.out.println("=====BUILD-LOG=====");
        System.out.println(text);
        System.out.println("=====BUILD-LOG=====");
                
        assertTrue(build.getResult().isWorseOrEqualTo(Result.SUCCESS));
        repository2.close();
        if (repository2.getDirectory().getParentFile().exists()) {
            FileUtils.deleteQuietly(repository2.getDirectory().getParentFile());
        }
    }
    
    @Test
    public void remoteNoRepoSpecifiedWithMoreThan1RepoShouldNotBeSuccessful() throws Exception {
        repository = TestUtilsFactory.createValidRepository("test-repo");
        Repository repository2 = TestUtilsFactory.createValidRepository("test-repo2");
        
        Git git = new Git(repository);
        git.checkout().setName("master").call();

        List<UserRemoteConfig> config = Arrays.asList(new UserRemoteConfig("file://" + repository.getDirectory().getAbsolutePath(), null, null, null), new UserRemoteConfig("file://" + repository2.getDirectory().getAbsolutePath(), null, null, null));
        
        List<GitSCMExtension> gitSCMExtensions = new ArrayList<GitSCMExtension>();
        gitSCMExtensions.add(new PruneStaleBranch());
        gitSCMExtensions.add(new CleanCheckout());
                        
        GitSCM gitSCM = new GitSCM(config,
        Collections.singletonList(new BranchSpec("*/ready/**")),
            false, Collections.<SubmoduleConfig>emptyList(),
            null, null, gitSCMExtensions);
        
        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.ACCUMULATED, config, null, true);
        project.setScm(gitSCM);
        TestUtilsFactory.triggerProject(project);
        
        jenkinsRule.waitUntilNoActivityUpTo(60000);
        TestUtilsFactory.destroyRepo(repository2);

        //Show the log for the latest build
        for(Iterator<FreeStyleBuild> builds = project.getBuilds().iterator(); builds.hasNext();) {
            AbstractBuild<?,?> b = builds.next();
            String text = jenkinsRule.createWebClient().getPage(b, "console").asText();
            System.out.println("=====BUILD-LOG=====");
            System.out.println(text);
            System.out.println("=====BUILD-LOG=====");
            if(text.contains("push origin :ready/feature_1")) {
                assertTrue(b.getResult().equals(Result.SUCCESS)); 
            } else {
                assertTrue(b.getResult().equals(Result.NOT_BUILT));
            }
        }
                
    }
        
    /**
     * Test case for JENKINS-25445
     * @throws Exception 
     */
    @Test 
    public void testCheckOutToSubDirectoryWithSqushShouldSucceed() throws Exception {
        repository = TestUtilsFactory.createValidRepository("test-repo-sqSubdir");
        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.SQUASH, repository, true);        
        GitSCM scm = (GitSCM)project.getScm();
        scm.getExtensions().add(new RelativeTargetDirectory("rel-dir"));
        TestUtilsFactory.triggerProject(project);
        
        jenkinsRule.waitUntilNoActivityUpTo(60000);
        
        FreeStyleBuild build = project.getBuilds().getFirstBuild();

        String text = jenkinsRule.createWebClient().getPage(build, "console").asText();
        System.out.println("=====BUILD-LOG=====");
        System.out.println(text);
        System.out.println("=====BUILD-LOG=====");
        
        assertTrue(build.getResult().isBetterOrEqualTo(Result.SUCCESS));
    }
        
    
    /**
     * Test case for JENKINS-25445
     * @throws Exception 
     */
    @Test 
    public void testCheckOutToSubDirectoryWithAccumulateShouldSucceed() throws Exception {
        repository = TestUtilsFactory.createValidRepository("test-repo-accSubdir");
        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.ACCUMULATED, repository, true);
        GitSCM scm = (GitSCM)project.getScm();
        scm.getExtensions().add(new RelativeTargetDirectory("rel-dir"));
        TestUtilsFactory.triggerProject(project); 
        
        jenkinsRule.waitUntilNoActivityUpTo(60000);
        
        FreeStyleBuild build = project.getBuilds().getFirstBuild();
        
        String text = jenkinsRule.createWebClient().getPage(build, "console").asText();
        System.out.println("=====BUILD-LOG=====");
        System.out.println(text);
        System.out.println("=====BUILD-LOG=====");
        
        assertTrue(build.getResult().isBetterOrEqualTo(Result.SUCCESS));        
    }
    
    /**
     * Incorrect check of origin (repo) lead to wrongful merge attempt on unwanted remote, which was the auto generated remote origin1 by the git plugin.
     * @throws Exception 
     */
    @Bug(25545)
    @Test
    public void testOriginAndEmptyInDualConfig() throws Exception {
        Repository repository1 = TestUtilsFactory.createValidRepository("test-repo1");
        Repository repository2 = TestUtilsFactory.createValidRepository("test-repo2");
        
        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, 
                TestUtilsFactory.STRATEGY_TYPE.SQUASH, 
                Arrays.asList(new UserRemoteConfig(repository1.getDirectory().getAbsolutePath(), "origin", null, null),
                        new UserRemoteConfig(repository2.getDirectory().getAbsolutePath(), null, null, null)), null, true);
        
        TestUtilsFactory.triggerProject(project);
        
        jenkinsRule.waitUntilNoActivityUpTo(60000);
        
        TestUtilsFactory.destroyRepo(repository1);
        TestUtilsFactory.destroyRepo(repository2);
        
        Iterator<FreeStyleBuild> bs = project.getBuilds().iterator();
        while(bs.hasNext()) {
            FreeStyleBuild build = bs.next();
            String text = jenkinsRule.createWebClient().getPage(build, "console").asText();
            System.out.println("=====BUILD-LOG=====");
            System.out.println(text);
            System.out.println("=====BUILD-LOG=====");
            
            //TODO: How to implement this in a better way??? Since the build seem to start in random order.
            if(text.contains("push origin :ready/feature_1")) {
                assertTrue(build.getResult().equals(Result.SUCCESS));
            } else {
                assertTrue(build.getResult().equals(Result.NOT_BUILT));
            }
        }
    }
    
    @Bug(25618)
    @Test
    public void validateSquashCommitMessageContents() throws Exception {
        Repository repository1 = TestUtilsFactory.createValidRepository("test-repo1");
        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.SQUASH, repository1);
        
        TestUtilsFactory.triggerProject(project);
        
        jenkinsRule.waitUntilNoActivityUpTo(60000);
        
        AbstractBuild<?,?> build = project.getFirstBuild();
        //Squashed commit of the following
        try(BuildResultValidator brv =  new BuildResultValidator(build, repository1).hasResult(Result.SUCCESS)
                .hasHeadCommitContents("Squashed commit of the following", "feature 1 commit 1-ready/feature_1-test-repo1")) {
            brv.validate();
        } 
        
    }
}
