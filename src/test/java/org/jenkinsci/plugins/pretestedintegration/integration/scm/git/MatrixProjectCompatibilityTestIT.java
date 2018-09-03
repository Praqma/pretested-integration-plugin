/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jenkinsci.plugins.pretestedintegration.integration.scm.git;

import hudson.matrix.MatrixProject;
import hudson.model.Result;
import hudson.plugins.git.UserRemoteConfig;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.util.Collections;

import static junit.framework.TestCase.assertEquals;

/**
 *
 * @author Mads
 */

public class MatrixProjectCompatibilityTestIT {

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    private static final String FEATURE_BRANCH_NAME = "ready/feature_1";
    private Repository repository;

    @After
    public void tearDown() throws Exception {
        TestUtilsFactory.destroyRepo(repository);
    }

    /**
     * Git Plugin
     *
     * Test that show that a ready/feature_1 branch get integrated into master
     * using a Matrix job type.
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
    public void oneBuildBasicSmokeTest() throws Exception {
        repository = TestUtilsFactory.createValidRepository("test-repo");
        File workDir = new File(TestUtilsFactory.WORKDIR,"test-repo");
        Git.cloneRepository().setURI("file:///" + repository.getDirectory().getAbsolutePath()).setDirectory(workDir)
                .setBare(false)
                .setCloneAllBranches(true)
                .setNoCheckout(false)
                .call().close();

        Git git = Git.open(workDir);

        System.out.println("Opening git repository in: " + workDir.getAbsolutePath());

        String readmeFromIntegration = FileUtils.readFileToString(new File(workDir,"readme"));

        git.checkout().setName(FEATURE_BRANCH_NAME).setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK).setCreateBranch(true).call();
        final int COMMIT_COUNT_ON_FEATURE_BEFORE_EXECUTION = TestUtilsFactory.countCommits(git);
        git.checkout().setName("master").setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK).call();

        MatrixProjectBuilder builder = new MatrixProjectBuilder()
        .setGitRepos(Collections.singletonList(new UserRemoteConfig("file://" + repository.getDirectory().getAbsolutePath(), null, null, null)))
        .setUseSlaves(true).setRule(jenkinsRule)
        .setStrategy(TestUtilsFactory.STRATEGY_TYPE.ACCUMULATED);
        builder.setJobType(MatrixProject.class);

        MatrixProject project = (MatrixProject)builder.generateJenkinsJob();
        TestUtilsFactory.triggerProject(project);

        jenkinsRule.waitUntilNoActivityUpTo(60000);

        String readmeFileContents = FileUtils.readFileToString(new File(workDir,"readme"));
        assertEquals(readmeFromIntegration, readmeFileContents);
        git.pull().call();

        assertEquals("3 runs for this particular matrix build", 3, project.getLastBuild().getRuns().size());

        final int COMMIT_COUNT_ON_MASTER_AFTER_EXECUTION = TestUtilsFactory.countCommits(git);
        git.close();
        //We assert that 2 commits from branch gets merged + 1 combined merge commit since we do --no-ff
        assertEquals(COMMIT_COUNT_ON_FEATURE_BEFORE_EXECUTION + 3, COMMIT_COUNT_ON_MASTER_AFTER_EXECUTION);
    }

    @Test
    public void oneBuildBasicMergeFailure() throws Exception {
        repository = TestUtilsFactory.createRepositoryWithMergeConflict("test-repo");
        File workDir = new File(TestUtilsFactory.WORKDIR,"test-repo");
        Git.cloneRepository().setURI("file:///" + repository.getDirectory().getAbsolutePath()).setDirectory(workDir)
                .setBare(false)
                .setCloneAllBranches(true)
                .setNoCheckout(false)
                .call().close();
                MatrixProjectBuilder builder = new MatrixProjectBuilder()
        .setGitRepos(Collections.singletonList(new UserRemoteConfig("file://" + repository.getDirectory().getAbsolutePath(), null, null, null)))
        .setUseSlaves(true).setRule(jenkinsRule)
        .setStrategy(TestUtilsFactory.STRATEGY_TYPE.ACCUMULATED);
        builder.setJobType(MatrixProject.class);
        MatrixProject project = (MatrixProject)builder.generateJenkinsJob();
        TestUtilsFactory.triggerProject(project);
        jenkinsRule.waitUntilNoActivityUpTo(60000);
        jenkinsRule.assertBuildStatus(Result.FAILURE, project.getLastBuild());
        assertEquals("0 runs for this particular matrix build. We never go to any of the actual subruns", 0, project.getLastBuild().getRuns().size());
    }

}
