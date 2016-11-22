package org.jenkinsci.plugins.pretestedintegration.integration.scm.git;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.impl.CleanCheckout;
import hudson.plugins.git.extensions.impl.PruneStaleBranch;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.SubmoduleConfig;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.util.BuildData;
import hudson.scm.SCM;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.jenkinsci.plugins.pretestedintegration.PretestedIntegrationBuildWrapper;
import org.jenkinsci.plugins.pretestedintegration.PretestedIntegrationPostCheckout;
import org.jenkinsci.plugins.pretestedintegration.scm.git.GitBridge;
import org.jenkinsci.plugins.pretestedintegration.scm.git.SquashCommitStrategy;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Pretested integration does not allow users to push 'master' or the integration
 * branch as 'ready' branches. as they would end up being deleted.
 * We don't allow this, even though it could be valid, to protect most people
 * from wrongly configuring the plugin and destroying their branches.
 */
public class IntegrationBranchNameRestrictions_IT {

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    public Repository repository;

    @After
    public void tearDown() throws Exception {
        TestUtilsFactory.destroyRepo(repository);
    }

    // Tests if pretested denies 'master' as a branch to integrate
    @Test
    public void integrateMasterBranch() throws Exception {

        repository = TestUtilsFactory.createRepoWithoutBranches("master");

        List<GitSCMExtension> gitSCMExtensions = new ArrayList<>();
        gitSCMExtensions.add(new PruneStaleBranch());
        gitSCMExtensions.add(new CleanCheckout());

        SCM gitSCM1 = new GitSCM(Collections.singletonList(new UserRemoteConfig("file://" + repository.getDirectory().getAbsolutePath(), null, null, null)),
                Collections.singletonList(new BranchSpec("master")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null, gitSCMExtensions);

        FreeStyleProject project = jenkinsRule.createFreeStyleProject();
        project.setScm(gitSCM1);
        GitBridge gitBridge = new GitBridge(new SquashCommitStrategy(), "master", "origin", null);

        project.getBuildWrappersList().add(new PretestedIntegrationBuildWrapper(gitBridge));
        project.getPublishersList().add(new PretestedIntegrationPostCheckout());

        TestUtilsFactory.triggerProject(project);

        jenkinsRule.waitUntilNoActivityUpTo(60000);

        FreeStyleBuild build1 = project.getLastBuild();

        System.out.println("==build1 actions==");
        for (BuildData action : build1.getActions(BuildData.class)) {
            System.out.println(action.lastBuild.revision.getBranches().iterator().next().getName());
        }
        String console = jenkinsRule.createWebClient().getPage(build1, "console").asText();
        System.out.println("===CONSOLE===");
        System.out.println(console);
        System.out.println("===CONSOLE===");
        System.out.println("===Result check 1===");
        String msg = "Using the master or integration branch for polling and development is not "
                   + "allowed since it will attempt to merge it to other branches and delete it after. Failing build.";
        assertTrue(console.contains(msg));
        System.out.println("===Result check 2===");
        assertEquals("Unexpected build result.", Result.FAILURE, build1.getResult());
        System.out.println("===Result check done===");
    }

    // Tests if branches with names similar to master don't fail (JENKINS-31138)
    @Test
    public void branchContainingMasterDoesntFail() throws Exception {
        // Create the test repository
        String repoName = "JENKINS_31138_master";
        repository = TestUtilsFactory.createRepository(repoName, new ArrayList<TestCommit>() {
            {
                add(new TestCommit("master", "README.md", "# Title", "Commit 1: readme"));
                add(new TestCommit("ready/king_master", "README.md", "## Subtitle", "Commit 2: readme"));
            }
        });

        // Clone test repo
        File workDir = new File(repoName);
        Git.cloneRepository().setURI("file:///" + repository.getDirectory().getAbsolutePath()).setDirectory(workDir)
                .setBare(false)
                .setCloneAllBranches(true)
                .setNoCheckout(false)
                .call().close();
        Git git = Git.open(workDir);

        // Build the project, assert SUCCESS
        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.SQUASH, repository);
        TestUtilsFactory.triggerProject(project);
        jenkinsRule.waitUntilNoActivityUpTo(60000);
        FreeStyleBuild build = project.getLastBuild();
        String consoleOutput = jenkinsRule.createWebClient().getPage(build, "console").asText();
        System.out.println(consoleOutput);
        jenkinsRule.assertBuildStatus(Result.SUCCESS, build);
        git.pull().call();
        RevCommit lastCommit = git.log().setMaxCount(1).call().iterator().next();
        String commitMessage = lastCommit.getFullMessage();
        git.close();

        assertEquals("Expected commit 2 message.", "Commit 2: readme", commitMessage);
    }

    // Tests if branches with names similar to the integration branch don't fail (JENKINS-31138)
    @Test
    public void branchContainingIntegrationBranchDoesntFail() throws Exception {
        // Create the test repository
        String repoName = "JENKINS_31138_integration";
        repository = TestUtilsFactory.createRepository(repoName, new ArrayList<TestCommit>() {
            {
                add(new TestCommit("master", "fun.groovy", "", "Commit 1: groovy"));
                add(new TestCommit("main", "fun.groovy", "println 'fun'", "Commit 2: groovy"));
                add(new TestCommit("ready/main_sub", "fun.groovy", "println 'more fun", "Commit 3: groovy"));
            }
        });

        // Clone test repo
        File workDir = new File(repoName);
        Git.cloneRepository().setURI("file:///" + repository.getDirectory().getAbsolutePath()).setDirectory(workDir)
                .setBare(false)
                .setCloneAllBranches(true)
                .setNoCheckout(false)
                .call().close();
        Git git = Git.open(workDir);

        // Build the project, assert SUCCESS
        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.SQUASH, repository, true, "main");
        TestUtilsFactory.triggerProject(project);
        jenkinsRule.waitUntilNoActivityUpTo(60000);
        FreeStyleBuild build = project.getLastBuild();
        String consoleOutput = jenkinsRule.createWebClient().getPage(build, "console").asText();
        System.out.println(consoleOutput);
        jenkinsRule.assertBuildStatus(Result.SUCCESS, build);
        git.pull().call();
        git.checkout().setName("origin/main").call();
        RevCommit lastCommit = git.log().setMaxCount(1).call().iterator().next();
        String commitMessage = lastCommit.getFullMessage();
        git.close();

        assertEquals("Expected commit 3 message.", "Commit 3: groovy", commitMessage);
    }
}
