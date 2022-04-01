package org.jenkinsci.plugins.pretestedintegration.integration.scm.git;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.lib.Repository;
import org.junit.After;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.WithTimeout;

public class FastForwardSingleCommitsIT {

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    private Repository repository;

    @After
    public void tearDown() throws Exception {
        TestUtilsFactory.destroyRepo(repository);
    }

    @Test
    @WithTimeout(600)
    public void squash_fastForwardsSingleCommit_PassWhenPossible() throws Exception {
        List<TestCommit> commits = new ArrayList<TestCommit>() {
            {
                add(new TestCommit("master", "README.md", "# Commit 1", "1: added readme"));
                add(new TestCommit("ready/feature_1", "README.md", "# Commit 2", "2: updated readme"));
            }
        };
        repository = TestUtilsFactory.createRepository("acc_fastForwardsSingleCommits", commits);

        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.SQUASH, repository);
        TestUtilsFactory.triggerProject(project);
        jenkinsRule.waitUntilNoActivityUpTo(60000);

        FreeStyleBuild build = project.getFirstBuild();
        jenkinsRule.assertBuildStatus(Result.SUCCESS, build);
        String console = jenkinsRule.createWebClient().getPage(project.getLastBuild(), "console").asText();
        System.out.println(console);
        assertTrue("Should have FF merged the commit.", console.contains("FF merge successful."));
    }

    @Test
    @WithTimeout(600)
    public void squash_fastForwardSingleCommitFails_FailsWhenImpossible() throws Exception {
        List<TestCommit> commits = new ArrayList<TestCommit>() {
            {
                add(new TestCommit("master", "README.md", "# Commit 1", "1: added readme"));
                add(new TestCommit("ready/feature_1", "README.md", "# 2", "Commit 2: updated readme"));
                add(new TestCommit("master", "README.md", "# Commit 3", "3: added readme"));
            }
        };
        repository = TestUtilsFactory.createRepository("acc_fastForwardsSingleCommits", commits);

        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.SQUASH, repository);
        TestUtilsFactory.triggerProject(project);
        jenkinsRule.waitUntilNoActivityUpTo(60000);

        FreeStyleBuild build = project.getFirstBuild();
        Assert.assertEquals(Result.FAILURE, build.getResult());

        String console = jenkinsRule.createWebClient().getPage(project.getLastBuild(), "console").asText();
        System.out.println(console);
        assertTrue("FF merge should have failed.", console.contains("FF merge failed."));
    }

    @Test
    @WithTimeout(600)
    public void squash_fastForwardSingleCommitFails_SkipsWhenMultipleCommits() throws Exception {
        List<TestCommit> commits = new ArrayList<TestCommit>() {
            {
                add(new TestCommit("master", "README.md", "# Commit 1", "1: added readme"));
                add(new TestCommit("ready/feature_1", "README.md", "# Commit 2", "2: updated readme"));
                add(new TestCommit("ready/feature_1", "README.md", "# Commit 3", "3: updated readme some more"));
            }
        };
        repository = TestUtilsFactory.createRepository("acc_fastForwardsSingleCommits", commits);

        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.SQUASH, repository);
        TestUtilsFactory.triggerProject(project);
        jenkinsRule.waitUntilNoActivityUpTo(60000);

        FreeStyleBuild build = project.getFirstBuild();
        assertTrue("The result should be SUCCESS, but is : " + build.getResult(), build.getResult().isBetterOrEqualTo(Result.SUCCESS));
    }

    @Test
    @WithTimeout(600)
    public void acc_fastForwardsSingleCommit_PassWhenPossible() throws Exception {
        List<TestCommit> commits = new ArrayList<TestCommit>() {
            {
                add(new TestCommit("master", "README.md", "# Commit 1", "1: added readme"));
                add(new TestCommit("ready/feature_1", "README.md", "# Commit 2", "2: updated readme"));
            }
        };
        repository = TestUtilsFactory.createRepository("acc_fastForwardsSingleCommits", commits);

        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.ACCUMULATED, repository);
        TestUtilsFactory.triggerProject(project);
        jenkinsRule.waitUntilNoActivityUpTo(60000);

        FreeStyleBuild build = project.getFirstBuild();
        jenkinsRule.assertBuildStatus(Result.SUCCESS, build);
        String console = jenkinsRule.createWebClient().getPage(project.getLastBuild(), "console").asText();
        System.out.println(console);
        assertTrue("Should have FF merged the commit.", console.contains("FF merge successful."));
    }

    @Test
    @WithTimeout(600)
    public void acc_fastForwardSingleCommitFails_FailsWhenImpossible() throws Exception {
        List<TestCommit> commits = new ArrayList<TestCommit>() {
            {
                add(new TestCommit("master", "README.md", "# Commit 1", "1: added readme"));
                add(new TestCommit("ready/feature_1", "README.md", "# 2", "Commit 2: updated readme"));
                add(new TestCommit("master", "README.md", "# Commit 3", "3: added readme"));
            }
        };
        repository = TestUtilsFactory.createRepository("acc_fastForwardsSingleCommits", commits);

        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.ACCUMULATED, repository);
        TestUtilsFactory.triggerProject(project);
        jenkinsRule.waitUntilNoActivityUpTo(60000);

        FreeStyleBuild build = project.getFirstBuild();
        assertEquals(Result.FAILURE, build.getResult());

        String console = jenkinsRule.createWebClient().getPage(project.getLastBuild(), "console").asText();
        System.out.println(console);
        assertTrue("FF merge should have failed.", console.contains("FF merge failed."));
    }

    @Test
    @WithTimeout(600)
    public void acc_fastForwardSingleCommitFails_SkipsWhenMultipleCommits() throws Exception {
        List<TestCommit> commits = new ArrayList<TestCommit>() {
            {
                add(new TestCommit("master", "README.md", "# Commit 1", "1: added readme"));
                add(new TestCommit("ready/feature_1", "README.md", "# Commit 2", "2: updated readme"));
                add(new TestCommit("ready/feature_1", "README.md", "# Commit 3", "3: updated readme some more"));
            }
        };
        repository = TestUtilsFactory.createRepository("acc_fastForwardsSingleCommits", commits);

        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.ACCUMULATED, repository);
        TestUtilsFactory.triggerProject(project);
        jenkinsRule.waitUntilNoActivityUpTo(60000);

        FreeStyleBuild build = project.getFirstBuild();
        assertTrue("The result should be SUCCESS, but is : " + build.getResult(),build.getResult().isBetterOrEqualTo(Result.SUCCESS));
    }

}
