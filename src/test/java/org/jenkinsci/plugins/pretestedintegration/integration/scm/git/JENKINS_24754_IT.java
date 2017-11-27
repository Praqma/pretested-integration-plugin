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
 *
 * 1)Test that the introduction of additional configuration (repository name) works with the default configurations. 
 * 2)Establish a set of rules for when we fail. 
 * 3)Operation with multiple git repositories. More specifically, the operation with the MultiSCM plugin
 *
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
     *
     * * Two git repositories is configured in the git scm plugin
     * * repo1 will default be named 'origin' by the git scm plugin
     * * repo2 will default be named 'origin1' by the git scm plugin
     * * pretested integration plugin is not configured, so default to:
     *      * integration branch: master
     *      * integration repo: origin
     * * job should be a success the default plugin configuration matches 
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
     *
    * * Two git repositories is configured in the git scm plugin
     * * repo1 will default be named 'origin' by the git scm plugin
     * * repo2 will default be named 'origin1' by the git scm plugin
     * * pretested integration plugin is configured, to match the second default git scm repo
     *      * integration branch: master
     *      * integration repo: origin1
     * * job should be a success, as a change there is named repository ('origin1') that matched the pretest config
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
     *
    * * Two git repositories is configured in the git scm plugin
     * * repo1 will default be named 'origin' by the git scm plugin
     * * repo2 is explicitly named 'origin1' by the git scm plugin
     * * pretested integration plugin is configured, to match the second default git scm repo
     *      * integration branch: master
     *      * integration repo: origin1
     * * job should be a success, as a change there is named repository ('origin1') that matched the pretest config
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
    // FIXME this test should still be working, any of the triggered jobs should report a fail back
    // as the pretested plugin is configured to only integrate a repository named 'origin' and not the one
    // being configured and called 'repo1' and 'repo2' so this means no matter how many builds we get
    // the plugin should refuse to integrate and throw a nothing to do exception
    @Test
    public void nothingToDo2RepositoriesWithNoMatchingName() throws Exception {
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

        assertTrue(text.contains("Nothing to do. The reason is:"));
        assertEquals("Unexpected build result.", Result.FAILURE, build.getResult());

    }

    /**
     *
     * TODO: isn't this a copy of
     * {@link GeneralBehaviourIT#remoteOrigin1WithMoreThan1RepoShouldBeSuccessfulFirstRepo()}
     *
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
                assertEquals("Unexpected build result.", bitstuff.getResult(), Result.NOT_BUILT);
            } else {
                assertEquals("Unexpected build result.", bitstuff.getResult(), Result.SUCCESS);
            }

        }

    }

    /**
     * TODO: This one is very much redundant. We've covered the case multiple
     * times
     *
     * We should work with out of the box default configuration. This one should
     * finish successfully with the merge going well.
     *
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

    /**
     * Test scenario: Check that ambiguous name across MultiScm git configuration is detected
     *
     * * Using two MultiScm configurations
     *      * MultiScm configuration 1 - two git repositories (same name)
     *          * 'repo1_1' with 'Name': 'test-repo'
     *          * 'repo1_2' with 'Name': 'test-repo'
     *      * MultiScm configuration 2 - one git repositories
     *          * 'repo2' with 'Name': 'test-repo2'
     * * Using Pretested Integration plugin configured:
     *      * 'Integration branch': 'master'
     *      * 'Integration repository': 'test-repo1'
     *
     * Expected results:
     * * The Git plugin will automatically assign unique names for the git configuration
     *   with two remotes, so 'repo1_2' automatically is named 'test-repo1' 
     *   (which is the one we try to integrate to)
     *
     * * The Pretested Integration Plugin will never try to integrate as the
     *   configuration check will fail the job, as we require all git repositories
     *   in any combination in a MultScm setup to be explicity named differently!
     *      * There are two repositories named 'test-repo' (in same git config)
     * @throws java.lang.Exception
     */
    @Test
    public void checkMultiScmWithTwoGitonfigurationAmbiguousName() throws Exception {
        Repository repo1_1 = TestUtilsFactory.createValidRepository("test-repo1_1");
        Repository repo1_2 = TestUtilsFactory.createValidRepository("test-repo1_2");
        Repository repo2 = TestUtilsFactory.createValidRepository("test-repo2");

        List<GitSCMExtension> gitSCMExtensions = new ArrayList<>();
        gitSCMExtensions.add(new PruneStaleBranch());
        gitSCMExtensions.add(new CleanCheckout());

        List<UserRemoteConfig> config1 = Arrays.asList(
                new UserRemoteConfig("file://" + repo1_1.getDirectory().getAbsolutePath(), "test-repo", null, null),
                new UserRemoteConfig("file://" + repo1_2.getDirectory().getAbsolutePath(), "test-repo", null, null)
        );

        SCM gitSCM1 = new GitSCM(config1,
                Collections.singletonList(new BranchSpec("*/ready/**")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null, gitSCMExtensions);

        List<UserRemoteConfig> config2 = Arrays.asList(
                new UserRemoteConfig("file://" + repo2.getDirectory().getAbsolutePath(), "test-repo2", null, null)
        );

        SCM gitSCM2 = new GitSCM(config2,
                Collections.singletonList(new BranchSpec("*/ready/**")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null, gitSCMExtensions);
        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPluginWithMultiSCM(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.SQUASH, Arrays.asList(gitSCM1, gitSCM2), "test-repo1");
        TestUtilsFactory.triggerProject(project);

        jenkinsRule.waitUntilNoActivityUpTo(60000);

        FreeStyleBuild build = project.getBuilds().getFirstBuild();

        //Show the log for the latest build
        String console = jenkinsRule.createWebClient().getPage(build, "console").asText();
        System.out.println("=====BUILD-LOG=====");
        System.out.println(console);
        System.out.println("=====BUILD-LOG=====");
        // Check the Git Plugin automatically renames the one 'test-repo' to 'test-repo1'
        // as the console will contain it as origin when writing about polling:
        assertTrue(console.contains("test-repo1/"));

        // Check our configuration check detects the problem and the job is failed.
        assertTrue(console.contains(UnsupportedConfigurationException.AMBIGUITY_IN_REMOTE_NAMES));
        assertTrue(build.getResult().isBetterOrEqualTo(Result.FAILURE));

        TestUtilsFactory.destroyRepo(repo1_1, repo1_2, repo2);
    }

    /**
     * Test scenario: Check that ambiguous name across MultiScm git
     * configuration is detected
     *
     * * Using two MultiScm configurations
     *      * MultiScm configuration 1 - two git repositories (same name)
     *          * 'repo1_1' with 'Name': 'test-repo'
     *          * 'repo1_2' with 'Name': 'test-repo1'
     *      * MultiScm configuration 2 - one git repositories
     *          * 'repo2' with 'Name': 'test-repo'
     * * Using Pretested Integration plugin configured:
     *      * 'Integration branch': 'master'
     *      * 'Integration repository': 'test-repo1'
     *
     * Expected results:
     * * The Pretested Integration Plugin will never try to integrate as the
     *   configuration check will fail the job, as we require all git repositories
     *   in any combination in a MultScm setup to be explicity named differently!
     *      * There are two repositories named 'test-repo1' (across MultiScm)
     * @throws java.lang.Exception
     */
    @Test
    public void checkMultiScmWithTwoGitConfigurationAmbiguousName2() throws Exception {
        Repository repo1_1 = TestUtilsFactory.createValidRepository("test-repo1_1");
        Repository repo1_2 = TestUtilsFactory.createValidRepository("test-repo1_2");
        Repository repo2 = TestUtilsFactory.createValidRepository("test-repo2");

        List<GitSCMExtension> gitSCMExtensions = new ArrayList<>();
        gitSCMExtensions.add(new PruneStaleBranch());
        gitSCMExtensions.add(new CleanCheckout());

        List<UserRemoteConfig> config1 = Arrays.asList(
                new UserRemoteConfig("file://" + repo1_1.getDirectory().getAbsolutePath(), "test-repo", null, null),
                new UserRemoteConfig("file://" + repo1_2.getDirectory().getAbsolutePath(), "test-repo1", null, null)
        );

        SCM gitSCM1 = new GitSCM(config1,
                Collections.singletonList(new BranchSpec("*/ready/**")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null, gitSCMExtensions);

        List<UserRemoteConfig> config2 = Arrays.asList(
                new UserRemoteConfig("file://" + repo2.getDirectory().getAbsolutePath(), "test-repo", null, null)
        );

        SCM gitSCM2 = new GitSCM(config2,
                Collections.singletonList(new BranchSpec("*/ready/**")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null, gitSCMExtensions);
        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPluginWithMultiSCM(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.SQUASH, Arrays.asList(gitSCM1, gitSCM2), "test-repo1");
        TestUtilsFactory.triggerProject(project);

        jenkinsRule.waitUntilNoActivityUpTo(60000);

        FreeStyleBuild build = project.getBuilds().getFirstBuild();

        // We expect only one build, as it will never run because we check configuration
        //Show the log for the latest build
        String console = jenkinsRule.createWebClient().getPage(build, "console").asText();
        System.out.println("=====BUILD-LOG=====");
        System.out.println(console);
        System.out.println("=====BUILD-LOG=====");
        assertTrue(console.contains(UnsupportedConfigurationException.AMBIGUITY_IN_REMOTE_NAMES));
        assertTrue(build.getResult().isBetterOrEqualTo(Result.FAILURE));
        TestUtilsFactory.destroyRepo(repo1_1, repo1_2, repo2);
    }

    /**
     * Multiple SCM Plugin
     *
     * Test that we operate using a default configuration, with two repositories
     * in a single Git Plugin configuration.
     *
     * Pretested integration:
     *  - 'Integration branch' : master (default)
     *  - 'Repository name' : origin (default)
     *  - 'Strategy' : Squashed commit
     *
     * Multiple SCM: 
     *   - GitSCM:
     *      - 'Name' : (default)
     *   - GitSCM:
     *      - 'Name' : (default)
     *
     * Workflow
     *  - Create two repositories each containing a 'ready' branch.
     *  - The build is triggered.
     *
     * Results
     *  - We fail. The user has not specified a repository name for all git
     *    repositories
     *
     * @throws Exception
     */
    @Test
    public void failImproperlyConfiguredMultiSCMTwoSeperateGitRepos() throws Exception {
        Repository repo1 = TestUtilsFactory.createValidRepository("test-repo");
        Repository repo2 = TestUtilsFactory.createValidRepository("test-repo2");

        List<GitSCMExtension> gitSCMExtensions = new ArrayList<>();
        gitSCMExtensions.add(new PruneStaleBranch());
        gitSCMExtensions.add(new CleanCheckout());

        SCM gitSCM2 = new GitSCM(Collections.singletonList(new UserRemoteConfig("file://" + repo1.getDirectory().getAbsolutePath(), null, null, null)),
                Collections.singletonList(new BranchSpec("origin/ready/**")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null, gitSCMExtensions);

        SCM gitSCM3 = new GitSCM(Collections.singletonList(new UserRemoteConfig("file://" + repo2.getDirectory().getAbsolutePath(), null, null, null)),
                Collections.singletonList(new BranchSpec("origin/ready/**")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null, gitSCMExtensions);

        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPluginWithMultiSCM(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.SQUASH, Arrays.asList(gitSCM2, gitSCM3), "origin");

        TestUtilsFactory.triggerProject(project);

        jenkinsRule.waitUntilNoActivityUpTo(60000);

        TestUtilsFactory.destroyRepo(repo1, repo2);

        Iterator<FreeStyleBuild> builds = project.getBuilds().iterator();
        while (builds.hasNext()) {
            FreeStyleBuild b = builds.next();
            String text = jenkinsRule.createWebClient().getPage(b, "console").asText();
            System.out.println("=====BUILD-LOG=====");
            System.out.println(text);
            System.out.println("=====BUILD-LOG=====");
            assertTrue(b.getResult().isBetterOrEqualTo(Result.FAILURE));
        }
    }
}
