/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jenkinsci.plugins.pretestedintegration.integration.scm.git;

import hudson.matrix.MatrixProject;
import hudson.matrix.MatrixRun;
import hudson.model.AbstractProject;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Run;
import hudson.plugins.git.UserRemoteConfig;
import hudson.util.RunList;
import java.io.File;
import java.util.Collections;
import static junit.framework.TestCase.assertEquals;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 *
 * @author Mads
 */

public class MatrixProjectCompatabilityTestIT {
    /*
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
    /*
    @Ignore //This test fails due to some changes in accumulatedStrategy, but exactly what is unknown. but the repo only contains 2 commits and not 5 - The errors does not come from version bumps
    @Test
    public void oneBuildBasicSmokeTest() throws Exception {
        repository = TestUtilsFactory.createValidRepository("test-repo");

        File workDir = new File("test-repo");

        Git.cloneRepository().setURI("file:///" + repository.getDirectory().getAbsolutePath()).setDirectory(workDir)
                .setBare(false)
                .setCloneAllBranches(true)
                .setNoCheckout(false)
                .call().close();

        Git git = Git.open(workDir);

        System.out.println("Opening git repository in: " + workDir.getAbsolutePath());

        String readmeFromIntegration = FileUtils.readFileToString(new File("test-repo/readme"));

        git.checkout().setName(FEATURE_BRANCH_NAME).setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK).setCreateBranch(true).call();
        final int COMMIT_COUNT_ON_FEATURE_BEFORE_EXECUTION = TestUtilsFactory.countCommits(git);
        git.checkout().setName("master").setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK).call();

        GitProjectBuilder builder = new GitProjectBuilder()
        .setGitRepos(Collections.singletonList(new UserRemoteConfig("file://" + repository.getDirectory().getAbsolutePath(), null, null, null)))
        .setUseSlaves(true).setRule(jenkinsRule)
        .setStrategy(GitProjectBuilder.STRATEGY_TYPE.ACCUMULATED);
        builder.setJobType(MatrixProject.class);

        MatrixProject project = (MatrixProject)builder.generateJenkinsJob();
        TestUtilsFactory.triggerProject(project);

        jenkinsRule.waitUntilNoActivityUpTo(60000);

        assertEquals("2 runs for this particular matrix build", 2, project.getLastBuild().getRuns().size());

        String readmeFileContents = FileUtils.readFileToString(new File("test-repo/readme"));
        assertEquals(readmeFromIntegration, readmeFileContents);

        git.pull().call();

        final int COMMIT_COUNT_ON_MASTER_AFTER_EXECUTION = TestUtilsFactory.countCommits(git);

        git.close();

        //We assert that 2 commits from branch gets merged + 1 combined merge commit since we do --no-ff
        assertEquals(COMMIT_COUNT_ON_FEATURE_BEFORE_EXECUTION + 3, COMMIT_COUNT_ON_MASTER_AFTER_EXECUTION);
    }

    /**
     *
     * TODO: How do we solve this one.
     *
     * We need to test and make our plugin not spawn 2 failed runs if the parent job fails with a merge conflict
     * @throws Exception
     */
    /*
    @Test
    @Ignore
    public void oneBuildBasicMergeFailure() throws Exception {
        repository = TestUtilsFactory.createRepositoryWithMergeConflict("test-repo");

        File workDir = new File("test-repo");

        Git.cloneRepository().setURI("file:///" + repository.getDirectory().getAbsolutePath()).setDirectory(workDir)
                .setBare(false)
                .setCloneAllBranches(true)
                .setNoCheckout(false)
                .call().close();

                GitProjectBuilder builder = new GitProjectBuilder()
        .setGitRepos(Collections.singletonList(new UserRemoteConfig("file://" + repository.getDirectory().getAbsolutePath(), null, null, null)))
        .setUseSlaves(true).setRule(jenkinsRule)
        .setStrategy(GitProjectBuilder.STRATEGY_TYPE.ACCUMULATED);
        builder.setJobType(MatrixProject.class);

        MatrixProject project = (MatrixProject)builder.generateJenkinsJob();
        TestUtilsFactory.triggerProject(project);

        jenkinsRule.waitUntilNoActivityUpTo(60000);

        assertEquals("Since merge failed...we should get no child jobs to spawn", 0, project.getLastBuild().getRuns().size());
    }
    */
}
