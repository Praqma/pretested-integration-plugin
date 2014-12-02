/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jenkinsci.plugins.pretestedintegration.integration.scm.git;

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
import hudson.scm.SCM;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.fail;
import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.assertTrue;
import org.eclipse.jgit.lib.Repository;
import org.jenkinsci.plugins.multiplescms.MultiSCM;
import org.jenkinsci.plugins.pretestedintegration.exceptions.UnsupportedConfigurationException;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * @author Mads
 * 
 * General theme of this test is:
 * 
 * 1)Test that the introduction of additional configuration (repository name) works with the default configurations. 
 * 2)Establish a set of rules for when we fail. 
 * 3)Operation with multiple git repositories. More specifically, the operation with the MultiSCM plugin
 * 
 */
public class JENKINS_24754_IT {
    
    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();
    
    private Repository repository;
    
    @After
    public void tearDown() throws Exception {
        TestUtilsFactory.destroyRepo(repository);
    }
    
    /**
     * Test case 1-1:
     * 
     * * Two git repositories is configured in the git scm plugin
     * * repo1 will default be named 'origin' by the git scm plugin
     * * repo2 will default be named 'origin1' by the git scm plugin
     * * pretested integration plugin is not configured, so default to:
     *      * integration branch: master
     *      * integration repo: origin
     * * job should be a success the default plugin configuration matches 
     * @throws java.lang.Exception
     */
    @Test
    public void succesWithDefaultConfiguration2RepositoriesWithoutNames() throws Exception {
        repository = TestUtilsFactory.createValidRepository("repo1");
        Repository repo2 = TestUtilsFactory.createValidRepository("repo2");
        
        List<UserRemoteConfig> userRemoteConfig = new ArrayList<UserRemoteConfig>();
        userRemoteConfig.add(new UserRemoteConfig("file://"+repository.getDirectory().getAbsolutePath(), null, null, null));
        userRemoteConfig.add(new UserRemoteConfig("file://"+repo2.getDirectory().getAbsolutePath(), null, null, null));
                
        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.SQUASH, userRemoteConfig, null, true);
        TestUtilsFactory.triggerProject(project);
        
        jenkinsRule.waitUntilNoActivityUpTo(60000);
        TestUtilsFactory.destroyRepo(repo2);               
        
        Iterator<FreeStyleBuild> biterator = project.getBuilds().iterator();
        
        while(biterator.hasNext()) {            
            FreeStyleBuild bitstuff = biterator.next();
            String text = jenkinsRule.createWebClient().getPage(bitstuff, "console").asText();
            System.out.println("=====BUILD-LOG=====");
            System.out.println(text);
            System.out.println("=====BUILD-LOG=====");
            if(text.contains("push origin :ready/feature_1")) {
                assertTrue(bitstuff.getResult().equals(Result.SUCCESS));
            } else {
                assertTrue(bitstuff.getResult().equals(Result.NOT_BUILT));
            }
            
        }  
        
    }
    
    
    /**
     * Test case 1-2:
     * 
    * * Two git repositories is configured in the git scm plugin
     * * repo1 will default be named 'origin' by the git scm plugin
     * * repo2 will default be named 'origin1' by the git scm plugin
     * * pretested integration plugin is configured, to match the second default git scm repo
     *      * integration branch: master
     *      * integration repo: origin1
     * * job should be a success, as a change there is named repository ('origin1') that matched the pretest config
     * @throws java.lang.Exception
     */
    @Test
    public void succesWithDefaultConfiguration2RepositoriesWithName() throws Exception {
        repository = TestUtilsFactory.createValidRepository("repo1");
        Repository repo2 = TestUtilsFactory.createValidRepository("repo2");
        
        List<UserRemoteConfig> userRemoteConfig = new ArrayList<UserRemoteConfig>();
        userRemoteConfig.add(new UserRemoteConfig("file://"+repository.getDirectory().getAbsolutePath(), null, null, null));
        userRemoteConfig.add(new UserRemoteConfig("file://"+repo2.getDirectory().getAbsolutePath(), null, null, null));
                
        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.SQUASH, userRemoteConfig, "origin1", true);
        TestUtilsFactory.triggerProject(project);
        
        jenkinsRule.waitUntilNoActivityUpTo(60000);
        TestUtilsFactory.destroyRepo(repo2);               
        
        Iterator<FreeStyleBuild> biterator = project.getBuilds().iterator();
        
        int checkCounter = 0;
        while(biterator.hasNext()) {            
            FreeStyleBuild bitstuff = biterator.next();
            String text = jenkinsRule.createWebClient().getPage(bitstuff, "console").asText();
            System.out.println("=====BUILD-LOG start =====");
            System.out.println(text);
            System.out.println("=====BUILD-LOG end =====");
            if(text.contains("The git repository name origin/ready/feature_1 does not match pretested configuration")) {
                System.out.println("Verified first build");
                assertTrue(bitstuff.getResult().equals(Result.NOT_BUILT));
                System.out.println("checkCounter++");
                checkCounter += 1;
            }
            else if (text.contains("git push origin1 :ready/feature_1")) {
                System.out.println("Verified second build");
                assertTrue(bitstuff.getResult().equals(Result.SUCCESS));
                System.out.println("checkCounter++");            
                checkCounter += 1;
            }
            
        }
        assertEquals("Could not verify both build as expected", checkCounter, 2);
       
    }    
    
    
    
/**
     * Test case 1-3:
     * 
    * * Two git repositories is configured in the git scm plugin
     * * repo1 will default be named 'origin' by the git scm plugin
     * * repo2 is explicitly named 'origin1' by the git scm plugin
     * * pretested integration plugin is configured, to match the second default git scm repo
     *      * integration branch: master
     *      * integration repo: origin1
     * * job should be a success, as a change there is named repository ('origin1') that matched the pretest config
     * @throws java.lang.Exception
     */
    @Test
    public void succesWithDefaultConfiguration2RepositoriesWithNameMatchDefaultConfig() throws Exception {
        repository = TestUtilsFactory.createValidRepository("repo1");
        Repository repo2 = TestUtilsFactory.createValidRepository("repo2");
        
        List<UserRemoteConfig> userRemoteConfig = new ArrayList<UserRemoteConfig>();
        userRemoteConfig.add(new UserRemoteConfig("file://"+repository.getDirectory().getAbsolutePath(), null, null, null));
        userRemoteConfig.add(new UserRemoteConfig("file://"+repo2.getDirectory().getAbsolutePath(), "origin1", null, null));
                
        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.SQUASH, userRemoteConfig, "origin1", true);
        TestUtilsFactory.triggerProject(project);
        
        jenkinsRule.waitUntilNoActivityUpTo(60000);
        TestUtilsFactory.destroyRepo(repo2);               
        
        Iterator<FreeStyleBuild> biterator = project.getBuilds().iterator();
        
        int checkCounter = 0;
        while(biterator.hasNext()) {            
            FreeStyleBuild bitstuff = biterator.next();
            String text = jenkinsRule.createWebClient().getPage(bitstuff, "console").asText();
            System.out.println("=====BUILD-LOG start =====");
            System.out.println(text);
            System.out.println("=====BUILD-LOG end =====");
            if(text.contains("The git repository name origin/ready/feature_1 does not match pretested configuration")) {
                System.out.println("Verified first build");
                assertTrue(bitstuff.getResult().equals(Result.NOT_BUILT));
                System.out.println("checkCounter++");
                checkCounter += 1;
            }
            else if (text.contains("git push origin1 :ready/feature_1")) {
                System.out.println("Verified second build");
                assertTrue(bitstuff.getResult().equals(Result.SUCCESS));
                System.out.println("checkCounter++");            
                checkCounter += 1;
            }
            
        }
        assertEquals("Could not verify both build as expected", checkCounter, 2);
       
    }    
    
    
    
    
    
    
    
    

    /**
     * When more than 1 git repository is chosen, and the user has specified repository names which do not match what is configured
     * in the pretested integration plugin. We should fail the build.
     * @throws java.lang.Exception
     */
    @Test
    public void failWithIncorrectConfiguration2RepositoriesWithNoMatchingName() throws Exception {
        repository = TestUtilsFactory.createValidRepository("repo1");
        Repository repo2 = TestUtilsFactory.createValidRepository("repo2");
        
        List<UserRemoteConfig> userRemoteConfig = new ArrayList<UserRemoteConfig>();
        userRemoteConfig.add(new UserRemoteConfig("file://"+repository.getDirectory().getAbsolutePath(), "repo1", null, null));
        userRemoteConfig.add(new UserRemoteConfig("file://"+repo2.getDirectory().getAbsolutePath(), "repo2", null, null));
                
        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.SQUASH, userRemoteConfig, null, true);
        TestUtilsFactory.triggerProject(project);
        
        jenkinsRule.waitUntilNoActivityUpTo(60000);
        TestUtilsFactory.destroyRepo(repo2);               
        
        FreeStyleBuild build = project.getBuilds().getFirstBuild();
        
        String text = jenkinsRule.createWebClient().getPage(build, "console").asText();
        System.out.println("=====BUILD-LOG=====");
        System.out.println(text);
        System.out.println("=====BUILD-LOG=====");
        
        assertTrue(text.contains(UnsupportedConfigurationException.ILLEGAL_CONFIG_NO_REPO_NAME_DEFINED));
        assertTrue(build.getResult().equals(Result.FAILURE));
        
    }
    
    /**
     * When more than 1 git repository is chosen, and the user has specified repository names which do match what is configured
     * in the pretested integration plugin. We should get 2 builds, one of which should be NOT_BUILT, the other one should be SUCCESS. 
     * @throws java.lang.Exception
     */
    @Test
    public void successWithMatchingRepositoryNames() throws Exception {
        Repository repo1 = TestUtilsFactory.createValidRepository("repo1");        
        Repository repo2 = TestUtilsFactory.createValidRepository("repo2");
        
        List<UserRemoteConfig> userRemoteConfig = new ArrayList<UserRemoteConfig>();
        userRemoteConfig.add(new UserRemoteConfig("file://"+repo1.getDirectory().getAbsolutePath(), "repo1", null, null));
        userRemoteConfig.add(new UserRemoteConfig("file://"+repo2.getDirectory().getAbsolutePath(), "repo2", null, null));
                
        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.SQUASH, userRemoteConfig, "repo1", true);
        TestUtilsFactory.triggerProject(project);
        
        jenkinsRule.waitUntilNoActivityUpTo(60000);
        
        TestUtilsFactory.destroyRepo(repo2);      
        TestUtilsFactory.destroyRepo(repo1);  
        
        Iterator<FreeStyleBuild> biterator = project.getBuilds().iterator();
        
        while(biterator.hasNext()) {            
            FreeStyleBuild bitstuff = biterator.next();
            String text = jenkinsRule.createWebClient().getPage(bitstuff, "console").asText();
            System.out.println("=====BUILD-LOG=====");
            System.out.println(text);
            System.out.println("=====BUILD-LOG=====");
            if(text.contains("The git repository name repo2/ready/feature_1 does not match pretested configuration")) {
                assertTrue(bitstuff.getResult().equals(Result.NOT_BUILT));
            } else {
                assertTrue(bitstuff.getResult().equals(Result.SUCCESS));
            }
            
        }
             
    }
    
    /**
     * We should work with out of the box default configuration. This one should finish succesfully with the merge going well.
     * 
     * Expect: Build success.
     * @throws Exception 
     */   
    @Test
    public void successWithDefaultConfiguration() throws Exception {
        Repository repo1 = TestUtilsFactory.createValidRepository("repo1");        
        
        List<UserRemoteConfig> userRemoteConfig = new ArrayList<UserRemoteConfig>();
        userRemoteConfig.add(new UserRemoteConfig("file://"+repo1.getDirectory().getAbsolutePath(), null, null, null));
                
        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.SQUASH, userRemoteConfig, null, true);
        TestUtilsFactory.triggerProject(project);
        
        jenkinsRule.waitUntilNoActivityUpTo(60000);
        
        Iterator<FreeStyleBuild> biterator = project.getBuilds().iterator();
        
        while(biterator.hasNext()) {            
            FreeStyleBuild bitstuff = biterator.next();
            String text = jenkinsRule.createWebClient().getPage(bitstuff, "console").asText();
            System.out.println("=====BUILD-LOG=====");
            System.out.println(text);
            System.out.println("=====BUILD-LOG=====");
        }
   
        TestUtilsFactory.destroyRepo(repo1);               
        assertTrue(project.getBuilds().getFirstBuild().getResult().equals(Result.SUCCESS));
    }
    
    @Test
    public void testOperationWithMultiSCM() throws Exception {
        repository = TestUtilsFactory.createValidRepository("test-repo");
        
        List<UserRemoteConfig> config = Arrays.asList(new UserRemoteConfig("file://" + repository.getDirectory().getAbsolutePath(), null, null, null), new UserRemoteConfig("file://" +repository.getDirectory().getAbsolutePath(), null, null, null));
        
        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPluginWithMultiSCM(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.SQUASH, config, "origin", repository);
        TestUtilsFactory.triggerProject(project);
        
        jenkinsRule.waitUntilNoActivityUpTo(60000);
        
        int nextBuildNumber = project.getNextBuildNumber();
         
        FreeStyleBuild build = project.getBuilds().getFirstBuild();
        
        //Show the log for the latest build
        String text = jenkinsRule.createWebClient().getPage(build, "console").asText();
        System.out.println("=====BUILD-LOG=====");
        System.out.println(text);
        System.out.println("=====BUILD-LOG=====");
        assertTrue(build.getResult().isBetterOrEqualTo(Result.SUCCESS));
    }
    
    /**
     * Objective: Verify the correct behaviour when the multi-scm plugin contains
     * two GitSCM repositories that use the same remote name (default configuration)
     * and identical branch specification. 
     * 
     * Setup: Two repositories that are identical, the contents are not that important for this test.
     * 
     * Integration: We do not need to check correct integration 
     * 
     * Expected result: We expect the build to fail because the configuration is illegal. When used this way you will have remotes with
     * the same name pointing to two different repositories. 
     * 
     * @throws Exception 
     */
    @Test
    public void testOperationWithMultiSCMTwoSeperateGitRepos() throws Exception {
        Repository repo1 = TestUtilsFactory.createValidRepository("test-repo");
        Repository repo2 = TestUtilsFactory.createValidRepository("test-repo2");
                
        List<GitSCMExtension> gitSCMExtensions = new ArrayList<GitSCMExtension>();
        gitSCMExtensions.add(new PruneStaleBranch());
        gitSCMExtensions.add(new CleanCheckout());
        
        SCM gitSCM2 = new GitSCM(Collections.singletonList(new UserRemoteConfig("file://" + repo1.getDirectory().getAbsolutePath(), null, null, null)),
                Collections.singletonList(new BranchSpec("origin/ready/**")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null, gitSCMExtensions);
        
        SCM gitSCM3 = new GitSCM(Collections.singletonList(new UserRemoteConfig("file://" + repo2.getDirectory().getAbsolutePath(), null, null, null)),
                Collections.singletonList(new BranchSpec("origin/ready/**")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null, gitSCMExtensions);    
        
        
        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPluginWithMultiSCM(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.SQUASH, Arrays.asList(gitSCM2,gitSCM3), "origin");
    
        TestUtilsFactory.triggerProject(project);
        
        jenkinsRule.waitUntilNoActivityUpTo(60000);
        
        TestUtilsFactory.destroyRepo(repo2);
        TestUtilsFactory.destroyRepo(repo1);
        
        Iterator<FreeStyleBuild> builds = project.getBuilds().iterator();
        while(builds.hasNext()) {
            FreeStyleBuild b = builds.next();
            String text = jenkinsRule.createWebClient().getPage(b, "console").asText();
            System.out.println("=====BUILD-LOG=====");
            System.out.println(text);
            System.out.println("=====BUILD-LOG=====");
            assertTrue(b.getResult().isBetterOrEqualTo(Result.FAILURE));
        }
    }
}
