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
import hudson.scm.SCM;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static junit.framework.Assert.assertEquals;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

import org.eclipse.jgit.lib.Repository;
import org.jenkinsci.plugins.pretestedintegration.exceptions.UnsupportedConfigurationException;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * General theme of this test is:
 * <p>
 * 1)Test that the introduction of additional configuration (repository name) works with the default configurations.
 * 2)Establish a set of rules for when we fail.
 * 3)Operation with multiple git repositories. More specifically, the operation with the MultiSCM plugin
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
     * <p>
     * * Two git repositories is configured in the git scm plugin
     * * repo1 will default be named 'origin' by the git scm plugin
     * * repo2 will default be named 'origin1' by the git scm plugin
     * * pretested integration plugin is not configured, so default to:
     * * integration branch: master
     * * integration repo: origin
     * * job should be a success the default plugin configuration matches
     *
     * @throws java.lang.Exception
     */
    // This test should also be working, and should integrate repository 'repo1' as this should default be given the name 'origin' by the git scm and this is also our default name we use in the plugin when nothing else stated.
    @Test
    public void succesWithDefaultConfiguration2RepositoriesWithoutNames() throws Exception {
        repository = TestUtilsFactory.createValidRepository("repo1");
        Repository repo2 = TestUtilsFactory.createValidRepository("repo2");

        List<UserRemoteConfig> userRemoteConfig = new ArrayList<>();
        userRemoteConfig.add(new UserRemoteConfig("file://" + repository.getDirectory().getAbsolutePath(), null, null, null));
        userRemoteConfig.add(new UserRemoteConfig("file://" + repo2.getDirectory().getAbsolutePath(), null, null, null));

        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.SQUASH, userRemoteConfig, null, true);
        TestUtilsFactory.triggerProject(project);

        jenkinsRule.waitUntilNoActivityUpTo(60000);
        TestUtilsFactory.destroyRepo(repo2);

        Iterator<FreeStyleBuild> biterator = project.getBuilds().iterator();

        while (biterator.hasNext()) {
            FreeStyleBuild bitstuff = biterator.next();
            String text = jenkinsRule.createWebClient().getPage(bitstuff, "console").asText();
            System.out.println("=====BUILD-LOG=====");
            System.out.println(text);
            System.out.println("=====BUILD-LOG=====");
            if (text.contains("push origin :origin/ready/feature_1")) {
                assertEquals("Unexpected build result.", bitstuff.getResult(), Result.SUCCESS);
            } else {
                assertEquals("Unexpected build result.", bitstuff.getResult(), Result.NOT_BUILT);
            }

        }

    }

    /**
     * Test case 1-2:
     * <p>
     * * Two git repositories is configured in the git scm plugin
     * * repo1 will default be named 'origin' by the git scm plugin
     * * repo2 will default be named 'origin1' by the git scm plugin
     * * pretested integration plugin is configured, to match the second default git scm repo
     * * integration branch: master
     * * integration repo: origin1
     * * job should be a success, as a change there is named repository ('origin1') that matched the pretest config
     *
     * @throws java.lang.Exception
     */
    // Here the problem can be that git scm is not any more using the default names 'origin', 'origin1', 'origin2'...
    // The description above is valid.
    @Test
    public void succesWithDefaultConfiguration2RepositoriesWithName() throws Exception {
        repository = TestUtilsFactory.createValidRepository("repo1");
        Repository repo2 = TestUtilsFactory.createValidRepository("repo2");

        List<UserRemoteConfig> userRemoteConfig = new ArrayList<>();
        userRemoteConfig.add(new UserRemoteConfig("file://" + repository.getDirectory().getAbsolutePath(), null, null, null));
        userRemoteConfig.add(new UserRemoteConfig("file://" + repo2.getDirectory().getAbsolutePath(), null, null, null));

        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.SQUASH, userRemoteConfig, "origin1", true);
        TestUtilsFactory.triggerProject(project);

        jenkinsRule.waitUntilNoActivityUpTo(60000);
        TestUtilsFactory.destroyRepo(repo2);

        Iterator<FreeStyleBuild> biterator = project.getBuilds().iterator();

        boolean checkedFirst = false;
        boolean checkedSecond = false;
        while (biterator.hasNext()) {
            FreeStyleBuild bitstuff = biterator.next();
            String text = jenkinsRule.createWebClient().getPage(bitstuff, "console").asText();
            System.out.println("=====BUILD-LOG start =====");
            System.out.println(text);
            System.out.println("=====BUILD-LOG end =====");
            if (text.contains("No revision matches configuration in 'Integration repository'")) {
                System.out.println("Verified first build");
                assertEquals("Unexpected build result.", bitstuff.getResult(), Result.NOT_BUILT);
                checkedFirst = true;
                //Do not check for 'git' push because on my machine (windows it says git.exe push origin1..." So now it will match windows as well :)
            } else if (text.contains("push origin1 :origin1/ready/feature_1")) {
                System.out.println("Verified second build");
                assertEquals("Unexpected build result.", bitstuff.getResult(), Result.SUCCESS);
                checkedSecond = true;
            }

        }

        assertTrue("Failed to assert that first build was checked", checkedFirst);
        assertTrue("Failed to assert that second build was checked", checkedSecond);
    }

    /**
     * Test case 1-3:
     * <p>
     * * Two git repositories is configured in the git scm plugin
     * * repo1 will default be named 'origin' by the git scm plugin
     * * repo2 is explicitly named 'origin1' by the git scm plugin
     * * pretested integration plugin is configured, to match the second default git scm repo
     * * integration branch: master
     * * integration repo: origin1
     * * job should be a success, as a change there is named repository ('origin1') that matched the pretest config
     *
     * @throws java.lang.Exception
     */
    @Test
    public void succesWithDefaultConfiguration2RepositoriesWithNameMatchDefaultConfig() throws Exception {
        repository = TestUtilsFactory.createValidRepository("repo1");
        Repository repo2 = TestUtilsFactory.createValidRepository("repo2");

        List<UserRemoteConfig> userRemoteConfig = new ArrayList<>();
        userRemoteConfig.add(new UserRemoteConfig("file://" + repository.getDirectory().getAbsolutePath(), null, null, null));
        userRemoteConfig.add(new UserRemoteConfig("file://" + repo2.getDirectory().getAbsolutePath(), "origin1", null, null));

        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.SQUASH, userRemoteConfig, "origin1", true);
        TestUtilsFactory.triggerProject(project);

        jenkinsRule.waitUntilNoActivityUpTo(60000);
        TestUtilsFactory.destroyRepo(repo2);

        Iterator<FreeStyleBuild> biterator = project.getBuilds().iterator();

        int checkCounter = 0;
        while (biterator.hasNext()) {
            FreeStyleBuild bitstuff = biterator.next();
            String text = jenkinsRule.createWebClient().getPage(bitstuff, "console").asText();
            System.out.println("=====BUILD-LOG start =====");
            System.out.println(text);
            System.out.println("=====BUILD-LOG end =====");
            if (text.contains("No revision matches configuration in 'Integration repository'")) {
                System.out.println("Verified first build");
                assertEquals("Unexpected build result.", bitstuff.getResult(), Result.NOT_BUILT);
                System.out.println("checkCounter++");
                checkCounter += 1;
            } else if (text.contains("push origin1 :origin1/ready/feature_1")) {
                System.out.println("Verified second build");
                assertEquals("Unexpected build result.", bitstuff.getResult(), Result.SUCCESS);
                System.out.println("checkCounter++");
                checkCounter += 1;
            }

        }
        assertEquals("Could not verify both build as expected", checkCounter, 2);

    }

    /**
     * When more than 1 git repository is chosen, and the user has specified
     * repository names which do not match what is configured in the pretested
     * integration plugin. We should fail the build.
     *
     * @throws java.lang.Exception
     */
    @Test
    public void failWith2RepositoriesWithNoMatchingName() throws Exception {
        repository = TestUtilsFactory.createValidRepository("repo1");
        Repository repo2 = TestUtilsFactory.createValidRepository("repo2");

        List<UserRemoteConfig> userRemoteConfig = new ArrayList<>();
        userRemoteConfig.add(new UserRemoteConfig("file://" + repository.getDirectory().getAbsolutePath(), "repo1", null, null));
        userRemoteConfig.add(new UserRemoteConfig("file://" + repo2.getDirectory().getAbsolutePath(), "repo2", null, null));

        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.SQUASH, userRemoteConfig, null, true);
        TestUtilsFactory.triggerProject(project);

        jenkinsRule.waitUntilNoActivityUpTo(60000);
        TestUtilsFactory.destroyRepo(repo2);

        // FIXME when this test works with first build, we should loop over all builds and improve the test
        FreeStyleBuild build = project.getBuilds().getFirstBuild();

        String text = jenkinsRule.createWebClient().getPage(build, "console").asText();
        System.out.println("=====BUILD-LOG=====");
        System.out.println(text);
        System.out.println("=====BUILD-LOG=====");

        assertEquals("Unexpected build result.", Result.FAILURE, build.getResult());

    }

    /**
     * TODO: isn't this a copy of
     * {@link GeneralBehaviourIT#remoteOrigin1WithMoreThan1RepoShouldBeSuccessfulFirstRepo()}
     * <p>
     * When more than 1 git repository is chosen, and the user has specified
     * repository names which do match what is configured in the pretested
     * integration plugin. We should get 2 builds, one of which should be
     * NOT_BUILT, the other one should be SUCCESS.
     *
     * @throws java.lang.Exception
     */

    //FIXME this test fails in as the plugin tries to merge changes from repository 2 into repository 1.
    // So a bug in the plugin allows for trying to run integration, without checking that we're started with the correct
    // repository, which is the one we configure in our pretested integration part of the job.
    // The configuration below should only allow us to integrate things on repo1.
    @Test
    public void successWithMatchingRepositoryNames() throws Exception {
        Repository repo1 = TestUtilsFactory.createValidRepository("repo1");
        Repository repo2 = TestUtilsFactory.createValidRepository("repo2");

        List<UserRemoteConfig> userRemoteConfig = new ArrayList<>();
        userRemoteConfig.add(new UserRemoteConfig("file://" + repo1.getDirectory().getAbsolutePath(), "repo1", null, null));
        userRemoteConfig.add(new UserRemoteConfig("file://" + repo2.getDirectory().getAbsolutePath(), "repo2", null, null));

        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.SQUASH, userRemoteConfig, "repo1", true);
        TestUtilsFactory.triggerProject(project);

        jenkinsRule.waitUntilNoActivityUpTo(60000);

        TestUtilsFactory.destroyRepo(repo1, repo2);

        Iterator<FreeStyleBuild> biterator = project.getBuilds().iterator();

        while (biterator.hasNext()) {
            FreeStyleBuild bitstuff = biterator.next();
            String text = jenkinsRule.createWebClient().getPage(bitstuff, "console").asText();
            System.out.println("=====BUILD-LOG=====");
            System.out.println(text);
            System.out.println("=====BUILD-LOG=====");
            if (text.contains("No revision matches configuration in 'Integration repository'")) {
                assertEquals("Unexpected build result.", Result.NOT_BUILT, bitstuff.getResult());
            } else {
                assertEquals("Unexpected build result.", Result.SUCCESS, bitstuff.getResult());
            }

        }

    }

    /**
     * TODO: This one is very much redundant. We've covered the case multiple
     * times
     * <p>
     * We should work with out of the box default configuration. This one should
     * finish successfully with the merge going well.
     * <p>
     * Expect: Build success.
     *
     * @throws Exception
     */
    @Test
    public void successWithDefaultConfiguration() throws Exception {
        Repository repo1 = TestUtilsFactory.createValidRepository("repo1");

        List<UserRemoteConfig> userRemoteConfig = new ArrayList<>();
        userRemoteConfig.add(new UserRemoteConfig("file://" + repo1.getDirectory().getAbsolutePath(), null, null, null));

        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.SQUASH, userRemoteConfig, null, true);
        TestUtilsFactory.triggerProject(project);

        jenkinsRule.waitUntilNoActivityUpTo(60000);

        Iterator<FreeStyleBuild> biterator = project.getBuilds().iterator();

        while (biterator.hasNext()) {
            FreeStyleBuild bitstuff = biterator.next();
            String text = jenkinsRule.createWebClient().getPage(bitstuff, "console").asText();
            System.out.println("=====BUILD-LOG=====");
            System.out.println(text);
            System.out.println("=====BUILD-LOG=====");
        }

        TestUtilsFactory.destroyRepo(repo1);
        Result buildResult = project.getBuilds().getFirstBuild().getResult();
        assertEquals("Unexpected build result.", buildResult, Result.SUCCESS);
    }
}
