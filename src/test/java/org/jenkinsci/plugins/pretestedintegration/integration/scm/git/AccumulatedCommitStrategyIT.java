package org.jenkinsci.plugins.pretestedintegration.integration.scm.git;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;


import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;

import static junit.framework.Assert.assertTrue;
import static junit.framework.TestCase.assertEquals;
import static org.jenkinsci.plugins.pretestedintegration.integration.scm.git.TestUtilsFactory.STRATEGY_TYPE;

/**
 * <h3>Set of tests that test that we react correctly to merge conflicts</h3> 
 * <p>Created by Andrius on 9/2/14.</p>
 * <p>All tests here are using single repository integration, that is the default Git configuration</p>
 * <p>The tests are all using the 'Accumulated commit' strategy in the Pretested integration plugin configuration</p>
 */ 
public class AccumulatedCommitStrategyIT {
    
    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    final String FEATURE_BRANCH_NAME = "ready/feature_1";

    private Repository repository;

    @After
    public void tearDown() throws Exception {        
        repository.close();
        if (repository.getDirectory().getParentFile().exists()) {
            FileUtils.deleteQuietly(repository.getDirectory().getParentFile());
        }       
    }

    /**
     * Git Plugin
     * 
     * Test that show that a ready/feature_1 branch get integrated into master
     * 
     * Pretested integration:
     *  - 'Integration branch' : master (default)
     *  - 'Repository name' : origin (default) 
     *  - 'Strategy' : Squash Commit
     * 
     * GitSCM:
     *  - 'Name' : (empty)
     * 
     * Workflow
     *  - Create a repository containing a 'ready' branch.
     *  - The build is triggered. 
     * 
     * Results
     *  - We expect that the plugin triggers, and that the commits on ready branch
     *    is merged into our integration branch master and build result becomes SUCCESS.
     * 
     * @throws Exception 
     */
    @Test
    public void oneValidFeatureBranch_1BuildIsTriggeredTheBranchGetsIntegratedBuildMarkedAsSUCCESS() throws Exception {        
        repository = TestUtilsFactory.createValidRepository("test-repo");
        Git git = new Git(repository);
        
        String readmeFromIntegration = FileUtils.readFileToString(new File(repository.getDirectory().getParent() +"/readme"));

        git.checkout().setName(FEATURE_BRANCH_NAME).call();
        final int COMMIT_COUNT_ON_FEATURE_BEFORE_EXECUTION = TestUtilsFactory.countCommits(repository);
        git.checkout().setName("master").call();

        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.ACCUMULATED, repository);
        TestUtilsFactory.triggerProject(project);

        jenkinsRule.waitUntilNoActivityUpTo(60000);

        int nextBuildNumber = project.getNextBuildNumber();
        FreeStyleBuild build = project.getBuildByNumber(nextBuildNumber - 1);

        Result result = build.getResult();

        assertTrue(result.isCompleteBuild());
        assertTrue(result.isBetterOrEqualTo(Result.SUCCESS));

        String readmeFileContents = FileUtils.readFileToString(new File(repository.getDirectory().getParent() +"/readme"));
        assertEquals(readmeFromIntegration, readmeFileContents);

        final int COMMIT_COUNT_ON_MASTER_AFTER_EXECUTION = TestUtilsFactory.countCommits(repository);
        assertTrue(COMMIT_COUNT_ON_MASTER_AFTER_EXECUTION == COMMIT_COUNT_ON_FEATURE_BEFORE_EXECUTION + 1);
    }
    
    /**
     * Git Plugin
     * 
     * Test that shows that a ready/feature_1 branch does not get integrated to master
     * branch because of merge conflict.
     * 
     * Pretested integration:
     *  - 'Integration branch' : master (default)
     *  - 'Repository name' : origin (default) 
     *  - 'Strategy' : Squash Commit
     * 
     * GitSCM:
     *  - 'Name' : (empty)
     * 
     * Workflow
     *  - Create a repository containing a 'ready' branch.
     *  - The build is triggered. 
     * 
     * Results
     *  - We expect that the plugin triggers, and that the build is FAILED with a merge
     *    conflict error.
     * 
     * @throws Exception 
     */
    @Test
    public void oneInvalidFeatureBranch_1BuildIsTriggeredNothingGetsIntegratedBuildMarkedAsFAILURE() throws Exception {
        repository = TestUtilsFactory.createRepositoryWithMergeConflict("test-repo");

        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, STRATEGY_TYPE.ACCUMULATED, repository);
        TestUtilsFactory.triggerProject(project);

        jenkinsRule.waitUntilNoActivityUpTo(60000);

        int nextBuildNumber = project.getNextBuildNumber();
        FreeStyleBuild build = project.getBuildByNumber(nextBuildNumber - 1);

        Result result = build.getResult();

        assertTrue(result.isWorseOrEqualTo(Result.FAILURE));
    }

    /**
     * Test that show that a ready/feature_1 branch get integrated into master
     * 
     * Pretested integration:
     *  - 'Integration branch' : master (default)
     *  - 'Repository name' : origin (default) 
     *  - 'Strategy' : Squash Commit
     * 
     * GitSCM:
     *  - 'Name' : (empty)
     * 
     * Workflow
     *  - Create a repository containing a 'ready' branch.
     *  - The build is triggered. 
     * 
     * Results
     *  - We expect that the plugin triggers, and that the commits on ready branch
     *    is merged into our integration branch master and build result becomes SUCCESS.
     * 
     * @throws Exception 
     */    
    @Test
    public void oneValidFeatureBranchRunningOnSlave_1BuildIsTriggeredTheBranchGetsIntegratedBuildMarkedAsSUCCESS() throws Exception {
        repository = TestUtilsFactory.createValidRepository("test-repo");
        String fromIntegration = FileUtils.readFileToString(new File(repository.getDirectory().getParent() +"/readme"));
        Git git = new Git(repository);

        git.checkout().setName(FEATURE_BRANCH_NAME).call();
        final int COMMIT_COUNT_ON_FEATURE_BEFORE_EXECUTION = TestUtilsFactory.countCommits(repository);
        git.checkout().setName("master").call();

        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, STRATEGY_TYPE.ACCUMULATED, repository);
        TestUtilsFactory.triggerProject(project);

        jenkinsRule.waitUntilNoActivityUpTo(60000);

        int nextBuildNumber = project.getNextBuildNumber();
        FreeStyleBuild build = project.getBuildByNumber(nextBuildNumber - 1);

        Result result = build.getResult();
        assertTrue(result.isBetterOrEqualTo(Result.SUCCESS));

        String readmeFileContents = FileUtils.readFileToString(new File(repository.getDirectory().getParent() +"/readme"));
        assertEquals(fromIntegration, readmeFileContents);

        final int COMMIT_COUNT_ON_MASTER_AFTER_EXECUTION = TestUtilsFactory.countCommits(repository);
        assertTrue(COMMIT_COUNT_ON_MASTER_AFTER_EXECUTION == COMMIT_COUNT_ON_FEATURE_BEFORE_EXECUTION + 1);
    }

    /**
     * Test that shows that a ready/feature_1 branch does not get integrated to master
     * branch because of merge conflict.
     * 
     * Pretested integration:
     *  - 'Integration branch' : master (default)
     *  - 'Repository name' : origin (default) 
     *  - 'Strategy' : Squash Commit
     * 
     * GitSCM:
     *  - 'Name' : (empty)
     * 
     * Workflow
     *  - Create a repository containing a 'ready' branch.
     *  - The build is triggered. 
     * 
     * Results
     *  - We expect that the plugin triggers, and that the build is FAILED with a merge
     *    conflict error.
     * 
     * @throws Exception 
     */
    @Test
    public void oneInvalidFeatureBranchRunningOnSlave_1BuildIsTriggeredNothingGetsIntegratedBuildMarkedAsFAILURE() throws Exception {
        repository = TestUtilsFactory.createRepositoryWithMergeConflict("test-repo");

        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, STRATEGY_TYPE.ACCUMULATED, repository);
        TestUtilsFactory.triggerProject(project);

        jenkinsRule.waitUntilNoActivityUpTo(60000);

        int nextBuildNumber = project.getNextBuildNumber();
        FreeStyleBuild build = project.getBuildByNumber(nextBuildNumber - 1);

        Result result = build.getResult();

        assertTrue(result.isWorseOrEqualTo(Result.FAILURE));
    }
}
