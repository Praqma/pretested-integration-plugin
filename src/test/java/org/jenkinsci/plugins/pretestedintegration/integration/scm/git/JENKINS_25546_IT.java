package org.jenkinsci.plugins.pretestedintegration.integration.scm.git;

import hudson.model.AbstractBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.impl.CleanCheckout;
import hudson.plugins.git.extensions.impl.PruneStaleBranch;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.SubmoduleConfig;
import hudson.plugins.git.UserRemoteConfig;
import hudson.scm.SCM;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.eclipse.jgit.lib.Repository;
import org.jenkinsci.plugins.pretestedintegration.exceptions.UnsupportedConfigurationException;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class JENKINS_25546_IT {

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    /**
     * Branch specifier for all are the same.
     *
     * Pretested integration repo name
     *  - blank/origin
     *
     * MultiSCM configuration
     *  - GitSCM 
     *      - 1 repo: named origin
     *  - GitSCM
     *      - 1 repo: named origin
     *
     * Workflow
     *  1. Let the build(s) finish.
     *  2. Once finished commit a new branch on the first repository
     *
     * Expected results
     *  We expect the build to fail. Because the remote (origin) cannot be determined.
     *
     * @throws Exception
     */
    @Test
    public void ambiguityInMultiSCMRemote() throws Exception {
        Repository repo1 = TestUtilsFactory.createValidRepository("scenario1-1");
        Repository repo2 = TestUtilsFactory.createValidRepository("scenario1-2");

        List<GitSCMExtension> gitSCMExtensions = new ArrayList<>();
        gitSCMExtensions.add(new PruneStaleBranch());
        gitSCMExtensions.add(new CleanCheckout());

        SCM gitSCM1 = new GitSCM(Collections.singletonList(new UserRemoteConfig("file://" + repo1.getDirectory().getAbsolutePath(), "origin", null, null)),
                Collections.singletonList(new BranchSpec("*/ready/**")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null, gitSCMExtensions);

        SCM gitSCM2 = new GitSCM(Collections.singletonList(new UserRemoteConfig("file://" + repo2.getDirectory().getAbsolutePath(), "origin", null, null)),
                Collections.singletonList(new BranchSpec("*/ready/**")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null, gitSCMExtensions);

        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPluginWithMultiSCM(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.SQUASH, Arrays.asList(gitSCM1, gitSCM2), "origin");
        TestUtilsFactory.triggerProject(project);

        jenkinsRule.waitUntilNoActivityUpTo(60000);

        new UniqueBranchGenerator(repo1, "One", "Two", "Three").usingBranch("ready/scenario1").build();

        TestUtilsFactory.triggerProject(project);

        jenkinsRule.waitUntilNoActivityUpTo(60000);

        TestUtilsFactory.destroyRepo(repo2);

        AbstractBuild<?, ?> lastBuild = project.getLastBuild();

        String consoleOut = jenkinsRule.createWebClient().getPage(lastBuild, "console").asText();

        try (BuildResultValidator validator = new BuildResultValidator(lastBuild, repo1, consoleOut)) {
            TestUtilsFactory.destroyRepo(repo2);
            validator.hasResult(Result.FAILURE).buildLogContains(UnsupportedConfigurationException.AMBIGUITY_IN_REMOTE_NAMES).validate();
        }

    }

    /**
     * Branch specifier for all are the same.
     * Pretested integration repository name
     *  - stable
     *
     * MultiSCM
     *  - GitSCM
     *      - stable
     *      - stable-1
     *  - GitSCM
     *      - non-stable
     *
     * Workflow
     *  - Create three repositories without matching branches
     *  - Create a ready/scenario2 branch on the stable repository.
     *
     * Results
     *  - There is still ambiguous remote names. But not on the branch we plan
     *    to integrate with. If every repository had a commit on a branch that matches
     *    the branch specifier, we would get 3 builds, 2 nothing to do and on fail/pass result.
     *
     * @throws Exception
     */
    @Test
    public void ambituigyInMultiSCMRemoteNotIntegrationTargetSuccess() throws Exception {
        jenkinsRule.setQuietPeriod(0);
        Repository repo1 = TestUtilsFactory.createRepoWithoutBranches("scenario2-1");
        Repository repo2 = TestUtilsFactory.createRepoWithoutBranches("scenario2-2");
        Repository repo3 = TestUtilsFactory.createRepoWithoutBranches("scenario2-3");

        List<GitSCMExtension> gitSCMExtensions = new ArrayList<>();
        gitSCMExtensions.add(new PruneStaleBranch());
        gitSCMExtensions.add(new CleanCheckout());

        SCM gitSCM1 = new GitSCM(Arrays.asList(new UserRemoteConfig("file:///" + repo1.getDirectory().getAbsolutePath(), "stable", null, null), new UserRemoteConfig("file:///" + repo3.getDirectory().getAbsolutePath(), "stable-1", null, null)),
                Collections.singletonList(new BranchSpec("*/ready/**")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null, gitSCMExtensions);

        SCM gitSCM2 = new GitSCM(Collections.singletonList(new UserRemoteConfig("file:///" + repo2.getDirectory().getAbsolutePath(), "non-stable", null, null)),
                Collections.singletonList(new BranchSpec("*/ready/**")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null, gitSCMExtensions);

        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPluginWithMultiSCM(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.SQUASH, Arrays.asList(gitSCM1, gitSCM2), "stable");

        TestUtilsFactory.triggerProject(project);

        jenkinsRule.waitUntilNoActivityUpTo(60000);

        new UniqueBranchGenerator(repo1, "One", "Two", "Three").usingBranch("ready/scenario2").build();

        TestUtilsFactory.triggerProject(project);

        jenkinsRule.waitUntilNoActivityUpTo(60000);

        AbstractBuild<?, ?> lastBuild = project.getLastBuild();

        String consoleOut = jenkinsRule.createWebClient().getPage(lastBuild, "console").asText();

        try (BuildResultValidator validator = new BuildResultValidator(lastBuild, repo1, consoleOut)) {
            TestUtilsFactory.destroyRepo(repo2, repo3);
            validator.hasResult(Result.SUCCESS).hasHeadCommitContents("One", "Two", "Three").validate();
        }

    }

    /**
     * Branch specifier for all are the same . Pretested integration repository
     * name - stable
     *
     * MultiSCM
     *  - GitSCM
     *      - stable
     *      - stable-two (default)
     *  - GitSCM
     *      - stable 
     *
     * Workflow
     *  1. Create a branch named ready/scenario3
     *  2. Push branch
     *
     * Results
     *  - We expect to fail because the integration repository is not unique, and 
     *    is present in both GitSCM's

     * @throws Exception
     */
    @Test
    public void ambiguityMultiSCMIntegrationTarget() throws Exception {
        Repository repo1 = TestUtilsFactory.createValidRepository("scenario3-1");
        Repository repo2 = TestUtilsFactory.createValidRepository("scenario3-2");
        Repository repo3 = TestUtilsFactory.createValidRepository("scenario3-3");

        List<GitSCMExtension> gitSCMExtensions = new ArrayList<>();
        gitSCMExtensions.add(new PruneStaleBranch());
        gitSCMExtensions.add(new CleanCheckout());

        SCM gitSCM1 = new GitSCM(Arrays.asList(new UserRemoteConfig("file://" + repo1.getDirectory().getAbsolutePath(), "stable", null, null), new UserRemoteConfig("file://" + repo3.getDirectory().getAbsolutePath(), "stable-two", null, null)),
                Collections.singletonList(new BranchSpec("*/ready/**")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null, gitSCMExtensions);

        SCM gitSCM2 = new GitSCM(Collections.singletonList(new UserRemoteConfig("file://" + repo2.getDirectory().getAbsolutePath(), "stable", null, null)),
                Collections.singletonList(new BranchSpec("*/ready/**")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null, gitSCMExtensions);

        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPluginWithMultiSCM(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.SQUASH, Arrays.asList(gitSCM1, gitSCM2), "stable");
        TestUtilsFactory.triggerProject(project);

        jenkinsRule.waitUntilNoActivityUpTo(45000);

        new UniqueBranchGenerator(repo1, "Message1", "Message2").usingBranch("ready/scenario3").build();

        TestUtilsFactory.triggerProject(project);

        jenkinsRule.waitUntilNoActivityUpTo(45000);

        String console = jenkinsRule.createWebClient().getPage(project.getLastBuild(), "console").asText();

        try (BuildResultValidator validator = new BuildResultValidator(project.getLastBuild(), repo1, console)) {
            TestUtilsFactory.destroyRepo(repo2, repo3);
            validator.buildLogContains(UnsupportedConfigurationException.AMBIGUITY_IN_REMOTE_NAMES)
                    .hasResult(Result.FAILURE).validate();
        }

    }

    /**
     * Branch specifier for all are the same.
     *
     * Pretested integration repo name
     *  - blank/origin
     *
     * MultiSCM configuration
     *  - GitSCM 
     *      - 1 repo named: myrepo
     *  - GitSCM
     *      - 1 repo unnamed defaults to origin (default)
     *
     * Workflow
     *  1. Create a couple of repositories without matching branches
     *  2. Create a branch in the repo you're using as integration target.
     *
     * Results
     *  - We expect the plugin to successfully merge the new branch. The generated
     *    branch contains a new file, so we expect no merge conflicts and commit should be 
     *    squashed
     *
     * @throws Exception
     */
    @Test
    public void matchingRemoteIntegrationTargetSuccess() throws Exception {
        Repository repo1 = TestUtilsFactory.createRepoWithoutBranches("scenario4-1");
        Repository repo2 = TestUtilsFactory.createRepoWithoutBranches("scenario4-2");

        List<GitSCMExtension> gitSCMExtensions = new ArrayList<>();
        gitSCMExtensions.add(new PruneStaleBranch());
        gitSCMExtensions.add(new CleanCheckout());

        SCM gitSCM1 = new GitSCM(Collections.singletonList(new UserRemoteConfig("file://" + repo1.getDirectory().getAbsolutePath(), "myrepo", null, null)),
                Collections.singletonList(new BranchSpec("*/ready/**")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null, gitSCMExtensions);

        SCM gitSCM2 = new GitSCM(Collections.singletonList(new UserRemoteConfig("file://" + repo2.getDirectory().getAbsolutePath(), "notmyrepo", null, null)),
                Collections.singletonList(new BranchSpec("*/ready/**")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null, gitSCMExtensions);

        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPluginWithMultiSCM(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.SQUASH, Arrays.asList(gitSCM1, gitSCM2), "myrepo");
        TestUtilsFactory.triggerProject(project);

        jenkinsRule.waitUntilNoActivityUpTo(45000);

        //Generate a new branch 
        new UniqueBranchGenerator(repo1, "One1", "Two2", "Three3").usingBranch("ready/scenario4").build();

        TestUtilsFactory.triggerProject(project);

        jenkinsRule.waitUntilNoActivityUpTo(30000);

        AbstractBuild<?, ?> lastBuild = project.getLastBuild();

        String consoleOut = jenkinsRule.createWebClient().getPage(lastBuild, "console").asText();

        try (BuildResultValidator validator = new BuildResultValidator(lastBuild, repo1, consoleOut)) {
            TestUtilsFactory.destroyRepo(repo2);
            validator.hasResult(Result.SUCCESS).hasHeadCommitContents("One1", "Two2", "Three3", "Squashed commit").validate();
        }
    }
}
