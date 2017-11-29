package org.jenkinsci.plugins.pretestedintegration.integration.scm.git;

import hudson.model.AbstractBuild;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.impl.CleanCheckout;
import hudson.plugins.git.extensions.impl.PruneStaleBranch;
import hudson.plugins.git.extensions.impl.RelativeTargetDirectory;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.SubmoduleConfig;
import hudson.plugins.git.UserRemoteConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static junit.framework.Assert.assertTrue;
import static junit.framework.TestCase.assertEquals;

import org.eclipse.jgit.lib.Repository;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.JenkinsRule;

public class GeneralBehaviourIT {

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    /**
     * Git Plugin
     * <p>
     * Test that we operate using a default configuration, with two repositories
     * in a single Git Plugin configuration.
     * <p>
     * Pretested integration:
     * - 'Integration branch' : master (default)
     * - 'Repository name' : origin (default)
     * - 'Strategy' : Accumulated commit
     * <p>
     * GitSCM:
     * - 'Name' : (empty)
     * - 'Name' : (empty)
     * <p>
     * Workflow
     * - Create two repositories each containing a 'ready' branch.
     * - The build is triggered.
     * <p>
     * Results
     * - Two builds. One NOT_BUILT and one SUCCESS.
     *
     * @throws Exception
     */
    @Test
    public void defaultGitConfigurationTwoRemotes1_NOT_BUILT_1_SUCCESS() throws Exception {
        Repository repository = TestUtilsFactory.createValidRepository("test-repo");
        Repository repository2 = TestUtilsFactory.createValidRepository("test-repo2");

        List<UserRemoteConfig> config = Arrays.asList(new UserRemoteConfig("file://" + repository.getDirectory().getAbsolutePath(), null, null, null), new UserRemoteConfig("file://" + repository2.getDirectory().getAbsolutePath(), null, null, null));

        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.ACCUMULATED, config, null, true);
        TestUtilsFactory.triggerProject(project);

        assertEquals(1, jenkinsRule.jenkins.getQueue().getItems().length);

        jenkinsRule.waitUntilNoActivityUpTo(60000);

        TestUtilsFactory.destroyRepo(repository2, repository);
        for (AbstractBuild<?, ?> b : project.getBuilds()) {
            String text = jenkinsRule.createWebClient().getPage(b, "console").asText();
            System.out.println("=====BUILD-LOG=====");
            System.out.println(text);
            System.out.println("=====BUILD-LOG=====");
            if (text.contains("push origin :origin/ready/feature_1")) {
                assertEquals("Unexpected build result.", Result.SUCCESS, b.getResult());
            } else {
                assertEquals("Unexpected build result.", Result.NOT_BUILT, b.getResult());
            }
        }

    }

    /**
     * Git Plugin
     * <p>
     * Test that we operate using a default configuration, with two repositories
     * in a single Git Plugin configuration.
     * <p>
     * Pretested integration:
     * - 'Integration branch' : master (default)
     * - 'Repository name' : origin1
     * - 'Strategy' : Accumulated commit
     * <p>
     * GitSCM:
     * - 'Name' : origin1
     * - 'Name' : magic
     * <p>
     * Workflow
     * - Create two repositories each containing a 'ready' branch.
     * - The build is triggered.
     * <p>
     * Results
     * - One Build. That should be successful.
     *
     * @throws Exception
     */
    @Test
    public void remoteOrigin1WithMoreThan1RepoShouldBeSuccessfulFirstRepo() throws Exception {
        Repository repository = TestUtilsFactory.createValidRepository("test-repo");
        Repository repository2 = TestUtilsFactory.createValidRepository("test-repo2");

        List<UserRemoteConfig> config = Arrays.asList(new UserRemoteConfig("file://" + repository.getDirectory().getAbsolutePath(), "origin1", null, null), new UserRemoteConfig("file://" + repository2.getDirectory().getAbsolutePath(), "magic", null, null));

        List<GitSCMExtension> gitSCMExtensions = new ArrayList<>();
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
        TestUtilsFactory.destroyRepo(repository2, repository);
    }

    /**
     * Git Plugin
     * <p>
     * Test that we operate using a default configuration, with two repositories
     * in a single Git Plugin configuration.
     * <p>
     * Pretested integration:
     * - 'Integration branch' : master (default)
     * - 'Repository name' : origin1
     * - 'Strategy' : Accumulated commit
     * <p>
     * GitSCM:
     * - 'Name' : magic
     * - 'Name' : origin1
     * <p>
     * Workflow
     * - Create two repositories each containing a 'ready' branch.
     * - The build is triggered.
     * <p>
     * Results
     * - One Build. That should be successful. We merge feature_1 from origin1 into
     * master.
     *
     * @throws Exception
     */
    @Test
    public void remoteOrigin1WithMoreThan1RepoShouldBeSuccessfulSecondRepo() throws Exception {
        Repository repository = TestUtilsFactory.createValidRepository("test-repo");
        Repository repository2 = TestUtilsFactory.createValidRepository("test-repo2");

        List<UserRemoteConfig> config = Arrays.asList(new UserRemoteConfig("file://" + repository.getDirectory().getAbsolutePath(), "magic", null, null), new UserRemoteConfig("file://" + repository2.getDirectory().getAbsolutePath(), "orgin1", null, null));

        List<GitSCMExtension> gitSCMExtensions = new ArrayList<>();
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

        TestUtilsFactory.destroyRepo(repository, repository2);

        int nextBuildNumber = project.getNextBuildNumber();
        FreeStyleBuild build = project.getBuildByNumber(nextBuildNumber - 1);

        //Show the log for the latest build
        String text = jenkinsRule.createWebClient().getPage(build, "console").asText();
        System.out.println("=====BUILD-LOG=====");
        System.out.println(text);
        System.out.println("=====BUILD-LOG=====");

        assertTrue(build.getResult().isWorseOrEqualTo(Result.SUCCESS));
    }

    /**
     * TODO: isn't this a copy of
     * {@link #defaultGitConfigurationTwoRemotes1_NOT_BUILT_1_SUCCESS()}
     * <p>
     * Git Plugin
     * <p>
     * Test that we operate using a default configuration, with two repositories
     * in a single Git Plugin configuration.
     * <p>
     * Pretested integration:
     * - 'Integration branch' : master (default)
     * - 'Repository name' : origin1
     * - 'Strategy' : Accumulated commit
     * <p>
     * GitSCM:
     * - 'Name' : (default)
     * - 'Name' : (default)
     * <p>
     * Workflow
     * - Create two repositories each containing a 'ready' branch.
     * - The build is triggered.
     * <p>
     * Results
     * - One Build. That should be successful. We merge feature_1 from origin1 into
     * master.
     *
     * @throws Exception
     */
    @Test
    public void remoteNoRepoSpecifiedWithMoreThan1RepoShouldNotBeSuccessful() throws Exception {
        Repository repository = TestUtilsFactory.createValidRepository("test-repo");
        Repository repository2 = TestUtilsFactory.createValidRepository("test-repo2");

        List<UserRemoteConfig> config = Arrays.asList(new UserRemoteConfig("file://" + repository.getDirectory().getAbsolutePath(), null, null, null), new UserRemoteConfig("file://" + repository2.getDirectory().getAbsolutePath(), null, null, null));


        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.ACCUMULATED, config, null, true);

        TestUtilsFactory.triggerProject(project);

        jenkinsRule.waitUntilNoActivityUpTo(60000);
        TestUtilsFactory.destroyRepo(repository2, repository);

        //Show the log for the latest build
        for (AbstractBuild<?, ?> b : project.getBuilds()) {
            String text = jenkinsRule.createWebClient().getPage(b, "console").asText();
            System.out.println("=====BUILD-LOG=====");
            System.out.println(text);
            System.out.println("=====BUILD-LOG=====");
            if (text.contains("push origin :origin/ready/feature_1")) {
                assertEquals("Unexpected build result.", Result.SUCCESS, b.getResult());
            } else {
                assertEquals("Unexpected build result.", Result.NOT_BUILT, b.getResult());
            }
        }

    }

    /**
     * Basically we need to verify that the plugin works when you checkout to a
     * subdirectory. Using squashed strategy.
     *
     * @throws Exception
     */
    @Bug(25445)
    @Test
    public void testCheckOutToSubDirectoryWithSqushShouldSucceed() throws Exception {
        Repository repository = TestUtilsFactory.createValidRepository("test-repo-sqSubdir");
        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.SQUASH, repository, true);
        GitSCM scm = (GitSCM) project.getScm();
        scm.getExtensions().add(new RelativeTargetDirectory("rel-dir"));
        TestUtilsFactory.triggerProject(project);

        jenkinsRule.waitUntilNoActivityUpTo(60000);

        TestUtilsFactory.destroyRepo(repository);

        FreeStyleBuild build = project.getBuilds().getFirstBuild();

        String text = jenkinsRule.createWebClient().getPage(build, "console").asText();
        System.out.println("=====BUILD-LOG=====");
        System.out.println(text);
        System.out.println("=====BUILD-LOG=====");

        assertTrue(build.getResult().isBetterOrEqualTo(Result.SUCCESS));
    }

    /**
     * Basically we need to verify that the plugin works when you checkout to a
     * subdirectory. Using accumulated strategy.
     *
     * @throws Exception
     */
    @Bug(25445)
    @Test
    public void testCheckOutToSubDirectoryWithAccumulateShouldSucceed() throws Exception {
        Repository repository = TestUtilsFactory.createValidRepository("test-repo-accSubdir");
        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.ACCUMULATED, repository, true);
        GitSCM scm = (GitSCM) project.getScm();
        scm.getExtensions().add(new RelativeTargetDirectory("rel-dir"));
        TestUtilsFactory.triggerProject(project);

        jenkinsRule.waitUntilNoActivityUpTo(60000);

        TestUtilsFactory.destroyRepo(repository);

        FreeStyleBuild build = project.getBuilds().getFirstBuild();

        String text = jenkinsRule.createWebClient().getPage(build, "console").asText();
        System.out.println("=====BUILD-LOG=====");
        System.out.println(text);
        System.out.println("=====BUILD-LOG=====");

        assertTrue(build.getResult().isBetterOrEqualTo(Result.SUCCESS));
    }

    /**
     * Incorrect check of origin (repo) lead to wrongful merge attempt on
     * unwanted remote, which was the auto generated remote origin1 by the git
     * plugin.
     *
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

        TestUtilsFactory.destroyRepo(repository1, repository2);

        Iterator<FreeStyleBuild> bs = project.getBuilds().iterator();
        while (bs.hasNext()) {
            FreeStyleBuild build = bs.next();
            String text = jenkinsRule.createWebClient().getPage(build, "console").asText();
            System.out.println("=====BUILD-LOG=====");
            System.out.println(text);
            System.out.println("=====BUILD-LOG=====");

            if (text.contains("push origin :origin/ready/feature_1")) {
                assertEquals("Unexpected build result.", build.getResult(), Result.SUCCESS);
            } else {
                assertEquals("Unexpected build result.", build.getResult(), Result.NOT_BUILT);
            }
        }
    }

    /**
     * We want to test that the squash commit merge message we crease is the
     * standard message. So we assert and check that the tip of our repository
     * has a commit that follows the patter of the default Squash merge commit
     * message.
     *
     * @throws Exception
     */
    @Bug(25618)
    @Test
    public void validateSquashCommitMessageContents() throws Exception {
        Repository repository1 = TestUtilsFactory.createValidRepository("test-repo1");
        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.SQUASH, repository1);

        TestUtilsFactory.triggerProject(project);

        jenkinsRule.waitUntilNoActivityUpTo(60000);

        AbstractBuild<?, ?> build = project.getFirstBuild();
        //Squashed commit of the following
        try (BuildResultValidator brv = new BuildResultValidator(build, repository1).hasResult(Result.SUCCESS)
                .hasHeadCommitContents("Squashed commit of the following", "feature 1 commit 1-ready/feature_1-test-repo1")) {
            brv.validate();
        }

    }
}
