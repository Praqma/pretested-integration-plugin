package org.jenkinsci.plugins.pretestedintegration.integration.scm.git;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.util.RunList;
import junit.framework.TestCase;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.util.Iterator;

import static org.jenkinsci.plugins.pretestedintegration.integration.scm.git.TestUtilsFactory.STRATEGY_TYPE;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import org.junit.After;

/**
 * Created by andrius on 9/2/14.
 */
public class SquashCommitStrategyIT {
    
    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    private final File GIT_DIR = new File("test-repo/.git");
    private final File GIT_PARENT_DIR = GIT_DIR.getParentFile().getAbsoluteFile();
    private final String README_FILE_PATH = GIT_PARENT_DIR.getPath().concat("/" + "readme");

    private final String AUTHER_NAME = "john Doe";
    private final String AUTHER_EMAIL = "Joh@praqma.net";

    private Repository repository;

    private String readmeFileContents_fromDevBranch;

    @After
    public void tearDown() throws Exception {        
        repository.close();
        if (repository.getDirectory().getParentFile().exists()) {
            FileUtils.deleteQuietly(repository.getDirectory().getParentFile());
        }        
    }

    private int countCommits() {
        Git git = new Git(repository);
        int commitCount = 0;

        try {
            Iterator<RevCommit> iterator = git.log().call().iterator();
            for ( ; iterator.hasNext() ; ++commitCount ) iterator.next();
        } catch (GitAPIException e) {
            e.printStackTrace();
        }

        return commitCount;
    }

    @Test
    public void canSquashMergeAFeatureBranch() throws Exception {
        try {
            repository = TestUtilsFactory.createValidRepository("test-repo");            
        } catch (GitAPIException e) {
            e.printStackTrace();
        }
        
        String readmeFromDev = FileUtils.readFileToString(new File(repository.getDirectory().getParent() +"/readme"));

        final int COMMIT_COUNT_BEFORE_EXECUTION = countCommits();

        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule.createFreeStyleProject(), STRATEGY_TYPE.SQUASH, repository);
        jenkinsRule.waitUntilNoActivityUpTo(60000);

        int nextBuildNumber = project.getNextBuildNumber();
        FreeStyleBuild build = project.getBuildByNumber(nextBuildNumber - 1);

        Result result = build.getResult();

        assertTrue(result.isCompleteBuild());
        assertTrue(result.isBetterOrEqualTo(Result.SUCCESS));

        String readmeFileContents = FileUtils.readFileToString(new File(repository.getDirectory().getParent() +"/readme"));
        assertEquals(readmeFromDev, readmeFileContents);

        final int COMMIT_COUNT_AFTER_EXECUTION = countCommits();

        TestCase.assertTrue(COMMIT_COUNT_AFTER_EXECUTION == COMMIT_COUNT_BEFORE_EXECUTION + 1);
    }

    @Test
    public void ShouldFailWithAMergeConflictPresent() throws Exception {
        try {
            repository = TestUtilsFactory.createRepositoryWithMergeConflict("test-repo");
        } catch (GitAPIException e) {
            e.printStackTrace();
        }
        
        final int COMMIT_COUNT_BEFORE_EXECUTION = countCommits();

        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule.createFreeStyleProject(), STRATEGY_TYPE.SQUASH, repository);

        jenkinsRule.waitUntilNoActivityUpTo(60000);

        int nextBuildNumber = project.getNextBuildNumber();
        FreeStyleBuild build = project.getBuildByNumber(nextBuildNumber - 1);

        Result result = build.getResult();

        assertTrue(result.isCompleteBuild());
        assertTrue(result.isWorseOrEqualTo(Result.FAILURE));

        final int COMMIT_COUNT_AFTER_EXECUTION = countCommits();

        assertTrue(COMMIT_COUNT_AFTER_EXECUTION == COMMIT_COUNT_BEFORE_EXECUTION);
    }

    @Test
    public void squashCommitStrategy_2FeatureBranchesBothValid_2BuildsAreTriggeredBothBranchesGetIntegrated() throws Exception {
        repository = TestUtilsFactory.createValidRepositoryWith2FeatureBranches("test-repo");

        final int COMMIT_COUNT_BEFORE_EXECUTION = countCommits();

        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule.createFreeStyleProject(), STRATEGY_TYPE.SQUASH, repository);

        jenkinsRule.waitUntilNoActivityUpTo(60000);

        RunList<FreeStyleBuild> builds = project.getBuilds();

        assertEquals(2, project.getNextBuildNumber() - 1);

        for (FreeStyleBuild build : builds) {
            System.out.println("===CONSOLE===");
            System.out.println(FileUtils.readFileToString(build.getLogFile()));
            System.out.println("===CONSOLE===");            
            Result result = build.getResult();
            assertTrue(result.isBetterOrEqualTo(Result.SUCCESS));
        }

        final int COMMIT_COUNT_AFTER_EXECUTION = countCommits();

        assertTrue(COMMIT_COUNT_AFTER_EXECUTION == COMMIT_COUNT_BEFORE_EXECUTION + 2);
    }

    @Test
    public void squashCommitStrategy_2FeatureBranches1ValidAnd1Invalid_2BuildsAreTriggeredValidBranchGetIntegrated() throws Exception {
        repository = TestUtilsFactory.createRepositoryWith2FeatureBranches1Valid1Invalid("test-repo");

        final int COMMIT_COUNT_BEFORE_EXECUTION = countCommits();

        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule.createFreeStyleProject(), STRATEGY_TYPE.SQUASH, repository);

        jenkinsRule.waitUntilNoActivityUpTo(60000);

        RunList<FreeStyleBuild> builds = project.getBuilds();

        assertEquals(2, project.getNextBuildNumber() - 1);

        Result result = builds.getFirstBuild().getResult();

        assertTrue(result.isCompleteBuild());
        assertTrue(result.isBetterOrEqualTo(Result.SUCCESS));

        FreeStyleBuild lastFailedBuild = project.getLastFailedBuild();
        assertNotNull(lastFailedBuild);

        final int COMMIT_COUNT_AFTER_EXECUTION = countCommits();

        assertTrue(COMMIT_COUNT_AFTER_EXECUTION == COMMIT_COUNT_BEFORE_EXECUTION + 1);
    }
}
