package org.jenkinsci.plugins.pretestedintegration.integration.scm.git;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.util.RunList;
import junit.framework.TestCase;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.lib.Repository;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.jenkinsci.plugins.pretestedintegration.integration.scm.git.TestUtilsFactory.STRATEGY_TYPE;

/**
 * 
 * <h3>Set of tests that test that we react correctly to merge conflicts</h3> 
 * <p>Created by Andrius on 9/2/14.</p>
 * <p>All tests here are using single repository integration, that is the default Git configuration</p>
 * <p>The tests are all using the 'Squashed commit' strategy in the Prettested integration plugin configuration</p>
 */
public class SquashCommitStrategyIT {

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();
    private Repository repository;

    @After
    public void tearDown() throws Exception {        
        repository.close();
        if (repository.getDirectory().getParentFile().exists()) {
            FileUtils.deleteQuietly(repository.getDirectory().getParentFile());
        }        
    }

    @Test
    public void oneValidFeatureBranch_1BuildIsTriggeredTheBranchGetsIntegratedBuildMarkedAsSUCCESS() throws Exception {
        repository = TestUtilsFactory.createValidRepository("test-repo");            
        String readmeFromDev = FileUtils.readFileToString(new File(repository.getDirectory().getParent() +"/readme"));

        final int COMMIT_COUNT_BEFORE_EXECUTION = TestUtilsFactory.countCommits(repository);

        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, STRATEGY_TYPE.SQUASH, repository);
        TestUtilsFactory.triggerProject(project);
        jenkinsRule.waitUntilNoActivityUpTo(60000);

        int nextBuildNumber = project.getNextBuildNumber();
        FreeStyleBuild build = project.getBuildByNumber(nextBuildNumber - 1);

        Result result = build.getResult();

        assertTrue(result.isCompleteBuild());
        assertTrue(result.isBetterOrEqualTo(Result.SUCCESS));

        String readmeFileContents = FileUtils.readFileToString(new File(repository.getDirectory().getParent() +"/readme"));
        assertEquals(readmeFromDev, readmeFileContents);

        final int COMMIT_COUNT_AFTER_EXECUTION = TestUtilsFactory.countCommits(repository);

        TestCase.assertTrue(COMMIT_COUNT_AFTER_EXECUTION == COMMIT_COUNT_BEFORE_EXECUTION + 1);
    }

    @Test
    public void oneInvalidFeatureBranch_1BuildIsTriggeredNothingGetsIntegratedBuildMarkedAsFAILURE() throws Exception {
        repository = TestUtilsFactory.createRepositoryWithMergeConflict("test-repo");
        final int COMMIT_COUNT_BEFORE_EXECUTION = TestUtilsFactory.countCommits(repository);

        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, STRATEGY_TYPE.SQUASH, repository);
        TestUtilsFactory.triggerProject(project);

        jenkinsRule.waitUntilNoActivityUpTo(60000);

        int nextBuildNumber = project.getNextBuildNumber();
        FreeStyleBuild build = project.getBuildByNumber(nextBuildNumber - 1);

        Result result = build.getResult();

        assertTrue(result.isCompleteBuild());
        assertTrue(result.isWorseOrEqualTo(Result.FAILURE));

        final int COMMIT_COUNT_AFTER_EXECUTION = TestUtilsFactory.countCommits(repository);

        assertTrue(COMMIT_COUNT_AFTER_EXECUTION == COMMIT_COUNT_BEFORE_EXECUTION);
    }

    @Test
    public void twoFeatureBranchesBothValid_2BuildsAreTriggeredBothBranchesGetIntegratedBuildMarkedAsSUCCESS() throws Exception {
        repository = TestUtilsFactory.createValidRepositoryWith2FeatureBranches("test-repo");
        final int COMMIT_COUNT_BEFORE_EXECUTION = TestUtilsFactory.countCommits(repository);

        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, STRATEGY_TYPE.SQUASH, repository);
        TestUtilsFactory.triggerProject(project);

        jenkinsRule.waitUntilNoActivityUpTo(60000);

        RunList<FreeStyleBuild> builds = project.getBuilds();

        assertEquals(2, project.getNextBuildNumber() - 1);

        for (FreeStyleBuild build : builds) {
            Result result = build.getResult();
            assertTrue(result.isBetterOrEqualTo(Result.SUCCESS));
        }

        final int COMMIT_COUNT_AFTER_EXECUTION = TestUtilsFactory.countCommits(repository);

        assertTrue(COMMIT_COUNT_AFTER_EXECUTION == COMMIT_COUNT_BEFORE_EXECUTION + 2);
    }

    @Test
    public void twoFeatureBranches1ValidAnd1Invalid_2BuildsAreTriggeredValidBranchGetsIntegrated() throws Exception {
        repository = TestUtilsFactory.createRepositoryWith2FeatureBranches1Valid1Invalid("test-repo");

        final int COMMIT_COUNT_BEFORE_EXECUTION = TestUtilsFactory.countCommits(repository);

        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, STRATEGY_TYPE.SQUASH, repository);
        TestUtilsFactory.triggerProject(project);

        jenkinsRule.waitUntilNoActivityUpTo(60000);

        RunList<FreeStyleBuild> builds = project.getBuilds();

        assertEquals(2, project.getNextBuildNumber() - 1);

        Result result = builds.getFirstBuild().getResult();

        assertTrue(result.isCompleteBuild());
        assertTrue(result.isBetterOrEqualTo(Result.SUCCESS));

        FreeStyleBuild lastFailedBuild = project.getLastFailedBuild();
        assertNotNull(lastFailedBuild);

        final int COMMIT_COUNT_AFTER_EXECUTION = TestUtilsFactory.countCommits(repository);

        assertTrue(COMMIT_COUNT_AFTER_EXECUTION == COMMIT_COUNT_BEFORE_EXECUTION + 1);
    }

    @Test
    public void oneValidFeatureBranchRunningOnSlave_1BuildIsTriggeredTheBranchGetsIntegratedBuildMarkedAsSUCCESS() throws Exception {
        repository = TestUtilsFactory.createValidRepository("test-repo");
        String readmeFromDev = FileUtils.readFileToString(new File(repository.getDirectory().getParent() +"/readme"));

        final int COMMIT_COUNT_BEFORE_EXECUTION = TestUtilsFactory.countCommits(repository);

        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, STRATEGY_TYPE.SQUASH, repository);
        TestUtilsFactory.triggerProject(project);

        jenkinsRule.waitUntilNoActivityUpTo(60000);

        int nextBuildNumber = project.getNextBuildNumber();
        FreeStyleBuild build = project.getBuildByNumber(nextBuildNumber - 1);

        Result result = build.getResult();
        assertTrue(result.isBetterOrEqualTo(Result.SUCCESS));

        String readmeFileContents = FileUtils.readFileToString(new File(repository.getDirectory().getParent()+"/readme"));
        assertEquals(readmeFromDev, readmeFileContents);

        final int COMMIT_COUNT_AFTER_EXECUTION = TestUtilsFactory.countCommits(repository);

        TestCase.assertTrue(COMMIT_COUNT_AFTER_EXECUTION == COMMIT_COUNT_BEFORE_EXECUTION + 1);
    }

    @Test
    public void oneInvalidFeatureBranchRunningOnSlave_1BuildIsTriggeredNothingGetsIntegratedBuildMarkedAsFAILURE() throws Exception {
        repository = TestUtilsFactory.createRepositoryWithMergeConflict("test-repo");

        final int COMMIT_COUNT_BEFORE_EXECUTION = TestUtilsFactory.countCommits(repository);

        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, STRATEGY_TYPE.SQUASH, repository);
        TestUtilsFactory.triggerProject(project);
        
        jenkinsRule.waitUntilNoActivityUpTo(60000);

        int nextBuildNumber = project.getNextBuildNumber();
        FreeStyleBuild build = project.getBuildByNumber(nextBuildNumber - 1);

        Result result = build.getResult();
        assertTrue(result.isWorseOrEqualTo(Result.FAILURE));

        final int COMMIT_COUNT_AFTER_EXECUTION = TestUtilsFactory.countCommits(repository);

        TestCase.assertTrue(COMMIT_COUNT_AFTER_EXECUTION == COMMIT_COUNT_BEFORE_EXECUTION);
    }

    @Test
    public void twoFeatureBranchesBothValidRunningOnSlave_2BuildsAreTriggeredBothBranchesGetIntegratedBuildMarkedAsSUCCESS() throws Exception {
        repository = TestUtilsFactory.createValidRepositoryWith2FeatureBranches(TestUtilsFactory.AUTHER_NAME);

        final int COMMIT_COUNT_BEFORE_EXECUTION = TestUtilsFactory.countCommits(repository);

        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, STRATEGY_TYPE.SQUASH, repository);
        TestUtilsFactory.triggerProject(project);

        jenkinsRule.waitUntilNoActivityUpTo(60000);

        RunList<FreeStyleBuild> builds = project.getBuilds();

        assertEquals(2, project.getNextBuildNumber() - 1);

        for (FreeStyleBuild build : builds) {
            Result result = build.getResult();
            assertTrue(result.isBetterOrEqualTo(Result.SUCCESS));
        }

        final int COMMIT_COUNT_AFTER_EXECUTION = TestUtilsFactory.countCommits(repository);

        assertTrue(COMMIT_COUNT_AFTER_EXECUTION == COMMIT_COUNT_BEFORE_EXECUTION + 2);
    }

    @Test
    public void twoFeatureBranches1ValidAnd1InvalidRunningOnSlave_2BuildsAreTriggeredValidBranchGetsIntegratedBuildMarkedAsFAILURE() throws Exception {
        repository = TestUtilsFactory.createRepositoryWith2FeatureBranches1Valid1Invalid("test-repo");

        final int COMMIT_COUNT_BEFORE_EXECUTION = TestUtilsFactory.countCommits(repository);

        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, STRATEGY_TYPE.SQUASH, repository);
        TestUtilsFactory.triggerProject(project);
        

        jenkinsRule.waitUntilNoActivityUpTo(60000);

        RunList<FreeStyleBuild> builds = project.getBuilds();

        assertEquals(2, project.getNextBuildNumber() - 1);

        Result result = builds.getFirstBuild().getResult();

        assertTrue(result.isCompleteBuild());
        assertTrue(result.isBetterOrEqualTo(Result.SUCCESS));

        FreeStyleBuild lastFailedBuild = project.getLastFailedBuild();
        assertNotNull(lastFailedBuild);

        final int COMMIT_COUNT_AFTER_EXECUTION = TestUtilsFactory.countCommits(repository);

        assertTrue(COMMIT_COUNT_AFTER_EXECUTION == COMMIT_COUNT_BEFORE_EXECUTION + 1);
    }
}
