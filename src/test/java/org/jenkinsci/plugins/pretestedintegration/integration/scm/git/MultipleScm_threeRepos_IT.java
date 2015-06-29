/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jenkinsci.plugins.pretestedintegration.integration.scm.git;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.SubmoduleConfig;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.impl.CleanCheckout;
import hudson.plugins.git.extensions.impl.PruneStaleBranch;
import hudson.plugins.git.extensions.impl.RelativeTargetDirectory;
import hudson.scm.PollingResult;
import hudson.scm.SCM;
import hudson.triggers.SCMTrigger;
import hudson.util.RunList;
import hudson.util.StreamTaskListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import org.eclipse.jgit.lib.Repository;
import org.jenkinsci.plugins.pretestedintegration.scm.git.GitMessages;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.xml.sax.SAXException;

/**
 * The following test scenarios are written to cover reported JENKINS-25960 issue: 
 * https://issues.jenkins-ci.org/browse/JENKINS-25960
 * When using a multiple scm setup, the order of the SCM sections mattered when
 * we tried to resolve the correct git executable (corresponds to which git SCM
 * we try to work on).
 * Problem was we always used the first found, thus if the integration repository
 * was not specified first the build found NOTHING TO DO.
 * 
 * Reproduce with the following scenario:
 *  * 3 repositories in a MultiScm configuration - all git repositories
 *  * First MultiSCM configuration:
 *      * repo1, named `repo1`
 *      * repo2*, named `repo2`
 *      * branch specifier `master`
 *      * Checkout to sub-directory: `repos`
 *      * Prune stale remote-tracking branches
 *      * Wipe out repository & force clone
 *  * Second MultiSCM configuration:
 *      * repo3, named: `repo3`
 *      * branch specifier `**\/ready/*`
 *      * Checkout to sub-directory: `repos`
 *      * Prune stale remote-tracking branches
 *      * Wipe out repository & force clone
 *  * Pretested Integration configuration:
 *      * Integration branch: `master`
 *      * Integration repository: `repo3`
 *      * Squashed strategy (stragey doesn't really matter in the context)
 * 
 * Observations:
         * Pushing a ready branch to `repo3`, sayes nothing to do:
            `Nothing to do the reason is: The branch name (repo3/ready/dev_865487921) 
             used by git, did not match a remote branch name. 
             You might already have integrated the branch`
         * MultiSCM and git SCM have some troubles in the beginning of a new job
            to find changes and build properly (have nothing to do with this plugin.
 * {@link twoMasterPlusOneReadyConfiguration} related to this specific setup.
   {@link oneReadyPlusTwoMasterConfiguration} show the other situation, where 
         integration repositori is configured first, thus it works.
 * @author Bue Petersen
 */
public class MultipleScm_threeRepos_IT {

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    private List<Repository> repositoriesToTearDown = new ArrayList<Repository>();

    // Common messages used several times
    // TODO: Move to a shared error message class.
    private final static String GIT_PLUGIN_VERIFY_REPO_AND_BRANCH_ERROR = "Couldn't find any revision to build. Verify the repository and branch configuration for this job.";

    @Before
    public void setup() throws Exception {
        repositoriesToTearDown.clear();
        listener = StreamTaskListener.fromStderr();

        singleThreadExecutor = Executors.newSingleThreadExecutor();
    }

    @After
    public void tearDown() throws Exception {
        singleThreadExecutor.shutdownNow();
        for( Repository r : repositoriesToTearDown ) {
            TestUtilsFactory.destroyRepo(r);
        }
    }

    // Copied from the git plugin tests in SCMTriggerTest.java
    // and used for the polling setup in the testcase.
    private ExecutorService singleThreadExecutor;
    protected TaskListener listener;

    // Copied from the git plugin tests in SCMTriggerTest.java
    // and used for the polling setup in the testcase.
    private Future<Void> triggerSCMTrigger(final SCMTrigger trigger) {
        if (trigger == null) {
            return null;
        }
        Callable<Void> callable = new Callable<Void>() {
            public Void call() throws Exception {
                trigger.run();
                return null;
            }
        };
        return singleThreadExecutor.submit(callable);
    }

    // Copied from the git plugin tests in SCMTriggerTest.java
    // and used for the polling setup in the testcase.
    private FreeStyleBuild waitForBuildFinished(FreeStyleProject project, int expectedBuildNumber, long timeout)
            throws Exception {
        long endTime = System.currentTimeMillis() + timeout;
        while (System.currentTimeMillis() < endTime) {
            RunList<FreeStyleBuild> builds = project.getBuilds();
            for (FreeStyleBuild build : builds) {
                if (build.getNumber() == expectedBuildNumber) {
                    // build.getResult() can return intermediary result if in progress
                    if ((build.getResult() != null) && (build.isBuilding() !=  true)) {
                        return build;
                    }
                    break; //Wait until build finished
                }
            }
            Thread.sleep(10);
        }
        return null;
    }

    /**
     * Pretty prints console output from build log
     * @param build
     * @param buildname - descriptive build name  included in the output
     * @return
     * @throws IOException
     * @throws SAXException 
     */
    private String printAndReturnConsoleOfBuild(FreeStyleBuild build, String buildname) throws IOException, SAXException {
        String console = jenkinsRule.createWebClient().getPage(build, "console").asText();
        System.out.println(String.format("========== %s CONSOLE start ==========", buildname));
        System.out.println(console);
        System.out.println(String.format("========== %s CONSOLE end ==========", buildname));
        return console;
    }

    /**
     * Pretty prints polling log from build
     * @param build
     * @param buildname - descriptive build name included in the output
     * @return
     * @throws IOException
     * @throws SAXException 
     */
    private String printAndReturnPollingLogOfBuild(FreeStyleBuild build, String buildname) throws IOException, SAXException {
        String console = jenkinsRule.createWebClient().getPage(build, "pollingLog").asText();
        System.out.println(String.format("========== %s POLLING LOG start ==========", buildname));
        System.out.println(console);
        System.out.println(String.format("========== %s POLLING LOG end ==========", buildname));
        return console;
    }

    /**
     * The following test covers reported JENKINS-25960 issue: 
     * https://issues.jenkins-ci.org/browse/JENKINS-25960
     * When using a multiple scm setup, the order of the SCM sections mattered when
     * we tried to resolve the correct git executable (corresponds to which git SCM
     * we try to work on).
     * Problem was we always used the first found, thus if the integration repository
     * was not specified first the build found NOTHING TO DO.
     * 
     * <b>This test is the configuration that works</b> - where the integration repository
     * is configured as first SCM. Added as reference.
     * 
     * Reproduce with the following scenario:
     *  * 3 repositories in a MultiScm configuration - all git repositories
     *  * First MultiSCM configuration:
     *      * repo3, named: `repo3`
     *      * branch specifier **\/ready/*
     *      * Checkout to sub-directory: `repos`
     *      * Prune stale remote-tracking branches
     *      * Wipe out repository & force clone
     *  * Second MultiSCM configuration:             
     *      * repo1, named `repo1`
     *      * repo2*, named `repo2`
     *      * branch specifier `master`
     *      * Checkout to sub-directory: `repos`
     *      * Prune stale remote-tracking branches
     *      * Wipe out repository & force clone
     *  * Pretested Integration configuration:
     *      * Integration branch: `master`
     *      * Integration repository: `repo3`
     *      * Squashed strategy (stragey doesn't really matter in the context)
     * 
     *  Test work flow:
     *      * First we try to establish a working job setup, where repository
             builds. MultiSCM have issues in fiding changes in correct order to 
             start with in a new job configuration.
            * Then a ready branch is pushed to the integration repository
             and the plugin should integrate it. Several builds start, as MultiSCM
             is involved and some times find changes in the other repositories.
             This plugin do not have control over these flows.
     * @throws Exception 
     */
    @Test
    public void oneReadyPlusTwoMasterConfiguration() throws Exception {
        Repository repo1 = TestUtilsFactory.createRepoWithoutBranches("oneReadyPlusTwoMasterConfiguration-repo1");
        Repository repo2 = TestUtilsFactory.createRepoWithoutBranches("oneReadyPlusTwoMasterConfiguration-repo2");
        Repository repo3 = TestUtilsFactory.createRepoWithoutBranches("oneReadyPlusTwoMasterConfiguration-repo3");

        //reused several times
        String pollingLog, console;
        Result expectedResult;
        FreeStyleBuild build;
        PollingResult poll;

        // Add to tear down list, to tear them down after all tests have run
        // Could do it later, if only test is successful
        repositoriesToTearDown.add(repo1);
        repositoriesToTearDown.add(repo2);
        repositoriesToTearDown.add(repo3);

        List<GitSCMExtension> gitSCMExtensionsRepo1AndRepo2 = new ArrayList<GitSCMExtension>();
        gitSCMExtensionsRepo1AndRepo2.add(new PruneStaleBranch());
        gitSCMExtensionsRepo1AndRepo2.add(new CleanCheckout());
        gitSCMExtensionsRepo1AndRepo2.add(new RelativeTargetDirectory("repo1andrepo2"));

        List<GitSCMExtension> gitSCMExtensionsRepo3 = new ArrayList<GitSCMExtension>();
        gitSCMExtensionsRepo3.add(new PruneStaleBranch());
        gitSCMExtensionsRepo3.add(new CleanCheckout());
        gitSCMExtensionsRepo3.add(new RelativeTargetDirectory("repo3"));

        List<UserRemoteConfig> userRemoteConfigRepo1AndRepo2 = new ArrayList<UserRemoteConfig>();
        userRemoteConfigRepo1AndRepo2.add(new UserRemoteConfig("file://" + repo1.getDirectory().getAbsolutePath(), "repo1", null, null));
        userRemoteConfigRepo1AndRepo2.add(new UserRemoteConfig("file://" + repo2.getDirectory().getAbsolutePath(), "repo2", null, null));

        SCM gitSCMRepo1AndRepo2 = new GitSCM(userRemoteConfigRepo1AndRepo2,
                Collections.singletonList(new BranchSpec("master")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null, gitSCMExtensionsRepo1AndRepo2);

        SCM gitSCMRepo3 = new GitSCM(Collections.singletonList(new UserRemoteConfig("file://" + repo3.getDirectory().getAbsolutePath(), "repo3", null, null)),
                Collections.singletonList(new BranchSpec("**ready/**")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null, gitSCMExtensionsRepo3);

        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPluginWithMultiSCM(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.SQUASH, Arrays.asList(gitSCMRepo3, gitSCMRepo1AndRepo2), "repo3");

        project.getTriggers().clear();
        SCMTrigger scmTrigger = new SCMTrigger("", true); // empty, triggering manually below as we go
        project.addTrigger(scmTrigger);
        scmTrigger.start(project, true);

        //Speedup test - avoid waiting 1 minute
        triggerSCMTrigger(project.getTrigger(SCMTrigger.class));

        build = waitForBuildFinished(project, 1, 60000);
        assertNotNull(String.format("Job has not been triggered - expected bild1", build));

        poll = project.poll(listener);
        assertTrue("Polling did not findnew changes as expected", poll.hasChanges());

        pollingLog = printAndReturnPollingLogOfBuild(build, "build1");
        assertTrue("Polling log does not contain message about 'No existing build'", pollingLog.contains("No existing build. Scheduling a new one. Done."));

        console = printAndReturnConsoleOfBuild(build, "build1");
        expectedResult = Result.FAILURE; //reuse common lines below                

        assertTrue(String.format("Could not verify %s have expected result %s - the result was %s", build, expectedResult, build.getResult()),
                build.getResult().equals(expectedResult));
        assertTrue("Could not find error message from git plugin in console about FAILED build",
                console.contains(GIT_PLUGIN_VERIFY_REPO_AND_BRANCH_ERROR));

        new UniqueBranchGenerator(repo3, "repo3-commit1", "repo3-commit2").usingBranch("ready/repo3_feature_1").build();

        Boolean integratedRepo3 = false;
        Boolean notBuildOnOtherRepoChanges = false;
        Boolean verified = false;
        Integer counter = 1; // current number of finished builds is 1.
        while (!verified && (counter < 3)) { // expect only 2 jobs in this loop
            counter++;

            String buildname = String.format("build%s", counter);
            triggerSCMTrigger(project.getTrigger(SCMTrigger.class));
            build = waitForBuildFinished(project, counter, 120000);
            assertNotNull(String.format("Job has not been triggered - expected %s", build));

            console = printAndReturnConsoleOfBuild(build, buildname);
            pollingLog = printAndReturnPollingLogOfBuild(build, buildname);

            if (build.getResult().equals(Result.SUCCESS)) {
                // Tip: http://myregexp.com/
                //String pattern = "(.*) (Checking out Revision) ([a-f,0-9]+) (\\(repo[1|2]/master\\)) (First time build\\.) (.*)";
                //Checking out Revision 0ae5858942afa97b86770da779f91c80b39694e4 (repo3/ready/repo3_feature_1)
                String pattern1 = "(.*) (Checking out Revision) ([a-f,0-9]+) (\\(repo3/ready/repo3_feature_1\\)) (.*)";
                // Create a Pattern object
                Pattern p1 = Pattern.compile(pattern1);
                //Preparing to merge changes in commit 0ae5858942afa97b86770da779f91c80b39694e4 to integration branch master
                // [PREINT] Preparing to merge changes in commit a1b88be91358cf4cb184c645cfdb0920a765d872 on development branch origin/ready/twoCommitsBranch to integration branch master
                String pattern2 = "(.*) (Preparing to merge changes in commit) ([a-f,0-9]+) (on development branch repo3/ready/repo3_feature_1) (to integration branch master)(.*)";
                // Create a Pattern object
                Pattern p2 = Pattern.compile(pattern2);

                Matcher m1 = p1.matcher(console);
                Matcher m2 = p2.matcher(console);
                assertTrue(String.format("Didn't complete regexp match in build %s trying to integrate repo3", buildname), m1.find() && m2.find());
                System.out.println("Found values: " + m1.group(3) + " ?= " + m2.group(3));
                assertTrue("Revision found on branch head does not match revision being integrated", m1.group(3).equals(m2.group(3)));

                assertTrue("Could not find message in console about ready branch in repo beeing integrated",
                        console.contains("merge --squash repo3/ready/repo3_feature_1"));
                assertTrue("Integration of ready branch in repo started, but could not match push command in console.",
                        console.contains("push repo3 :ready/repo3_feature_1"));
                integratedRepo3 = true;

            } else if (build.getResult().equals(Result.NOT_BUILT)) {
                String msg = GitMessages.NoRelevantSCMchange("repo3/ready/repo3_feature_1");
                assertTrue("Error message related to detecting the wrong git scm was not found.",
                        console.contains(msg));
                notBuildOnOtherRepoChanges = true;
            } else {
                // all other build results
                assertTrue(String.format("Unexpected build result found: %s", build.getResult()), false);
            }
            verified = integratedRepo3 && notBuildOnOtherRepoChanges;
        }
        assertTrue("Seems like not all jobs on test was covered in the test", verified); // safety check if loop logic wrong
    }

    /**
     * The following test covers reported JENKINS-25960 issue: 
     * https://issues.jenkins-ci.org/browse/JENKINS-25960
     * When using a multiple scm setup, the order of the SCM sections mattered when
     * we tried to resolve the correct git executable (corresponds to which git SCM
     * we try to work on).
     * Problem was we always used the first found, thus if the integration repository
     * was not specified first the build found NOTHING TO DO.
     * 
     * <b>This test is the configuration that works</b> - where the integration repository
     * is configured as first SCM. Added as reference.
     * 
     * Reproduce with the following scenario:
     *  * 3 repositories in a MultiScm configuration - all git repositories
     *  * Second MultiSCM configuration:             
     *      * repo1, named `repo1`
     *      * repo2*, named `repo2`
     *      * branch specifier `master`
     *      * Checkout to sub-directory: `repos`
     *      * Prune stale remote-tracking branches
     *      * Wipe out repository & force clone
     *  * First MultiSCM configuration:
     *      * repo3, named: `repo3`
     *      * branch specifier **\/ready/*
     *      * Checkout to sub-directory: `repos`
     *      * Prune stale remote-tracking branches
     *      * Wipe out repository & force clone
     *  * Pretested Integration configuration:
     *      * Integration branch: `master`
     *      * Integration repository: `repo3`
     *      * Squashed strategy (stragey doesn't really matter in the context)
     * 
     *  Test work flow:
     *      * First we try to establish a working job setup, where repository
             builds. MultiSCM have issues in fiding changes in correct order to 
             start with in a new job configuration.
            * Then a ready branch is pushed to the integration repository
             and the plugin should integrate it. Several builds start, as MultiSCM
             is involved and some times find changes in the other repositories.
             This plugin do not have control over these flows.
     * @throws Exception 
     */
    @Test
    public void twoMasterPlusOneReadyConfiguration() throws Exception {
        Repository repo1 = TestUtilsFactory.createRepoWithoutBranches("twoMasterPlusOneReadyConfiguration-repo1");
        Repository repo2 = TestUtilsFactory.createRepoWithoutBranches("twoMasterPlusOneReadyConfiguration-repo2");
        Repository repo3 = TestUtilsFactory.createRepoWithoutBranches("twoMasterPlusOneReadyConfiguration-repo3");

        //reused several times
        String pollingLog, console;
        Result expectedResult;
        FreeStyleBuild build;
        PollingResult poll;

        // Add to tear down list, to tear them down after all tests have run
        // Could do it later, if only test is successful
        repositoriesToTearDown.add(repo1);
        repositoriesToTearDown.add(repo2);
        repositoriesToTearDown.add(repo3);

        List<GitSCMExtension> gitSCMExtensionsRepo1AndRepo2 = new ArrayList<GitSCMExtension>();
        gitSCMExtensionsRepo1AndRepo2.add(new PruneStaleBranch());
        gitSCMExtensionsRepo1AndRepo2.add(new CleanCheckout());
        gitSCMExtensionsRepo1AndRepo2.add(new RelativeTargetDirectory("repo1andrepo2"));

        List<GitSCMExtension> gitSCMExtensionsRepo3 = new ArrayList<GitSCMExtension>();
        gitSCMExtensionsRepo3.add(new PruneStaleBranch());
        gitSCMExtensionsRepo3.add(new CleanCheckout());
        gitSCMExtensionsRepo3.add(new RelativeTargetDirectory("repo3"));

        List<UserRemoteConfig> userRemoteConfigRepo1AndRepo2 = new ArrayList<UserRemoteConfig>();
        userRemoteConfigRepo1AndRepo2.add(new UserRemoteConfig("file://" + repo1.getDirectory().getAbsolutePath(), "repo1", null, null));
        userRemoteConfigRepo1AndRepo2.add(new UserRemoteConfig("file://" + repo2.getDirectory().getAbsolutePath(), "repo2", null, null));

        SCM gitSCMRepo1AndRepo2 = new GitSCM(userRemoteConfigRepo1AndRepo2,
                Collections.singletonList(new BranchSpec("master")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null, gitSCMExtensionsRepo1AndRepo2);

        SCM gitSCMRepo3 = new GitSCM(Collections.singletonList(new UserRemoteConfig("file://" + repo3.getDirectory().getAbsolutePath(), "repo3", null, null)),
                Collections.singletonList(new BranchSpec("**ready/**")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null, gitSCMExtensionsRepo3);

        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPluginWithMultiSCM(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.SQUASH, Arrays.asList(gitSCMRepo1AndRepo2, gitSCMRepo3), "repo3");

        project.getTriggers().clear();
        SCMTrigger scmTrigger = new SCMTrigger("", true); // empty, triggering manually below as we go
        project.addTrigger(scmTrigger);
        scmTrigger.start(project, true);

        //Speedup test - avoid waiting 1 minute
        triggerSCMTrigger(project.getTrigger(SCMTrigger.class));

        build = waitForBuildFinished(project, 1, 60000);
        assertNotNull(String.format("Job has not been triggered - expected bild1", build));

        poll = project.poll(listener);
        assertTrue("Polling did not findnew changes as expected", poll.hasChanges());

        pollingLog = printAndReturnPollingLogOfBuild(build, "build1");
        assertTrue("Polling log does not contain message about 'No existing build'", pollingLog.contains("No existing build. Scheduling a new one. Done."));

        console = printAndReturnConsoleOfBuild(build, "build1");
        expectedResult = Result.FAILURE; //reuse common lines below                

        assertTrue(String.format("Could not verify %s have expected result %s - the result was %s", build, expectedResult, build.getResult()),
                build.getResult().equals(expectedResult));
        assertTrue("Could not find error message from git plugin in console about FAILED build",
                console.contains(GIT_PLUGIN_VERIFY_REPO_AND_BRANCH_ERROR));

        new UniqueBranchGenerator(repo3, "repo3-commit1", "repo3-commit2").usingBranch("ready/repo3_feature_1").build();

        Boolean integratedRepo3 = false;
        Boolean failedOnOtherRepoChanges = false;
        Boolean verified = false;
        Integer counter = 1; // current number of finished builds is 1.
        while (!verified && (counter < 3)) { // expect only 2 jobs in this loop
            counter++;
            System.out.println(String.format("Verification of builds loop: %d: ", counter));

            String buildname = String.format("build%s", counter);
            triggerSCMTrigger(project.getTrigger(SCMTrigger.class));
            build = waitForBuildFinished(project, counter, 90000);
            assertNotNull(String.format("Job has not been triggered - expected %s", build));

            console = printAndReturnConsoleOfBuild(build, buildname);
            pollingLog = printAndReturnPollingLogOfBuild(build, buildname);

            if (build.getResult().equals(Result.SUCCESS)) {
                // Tip: http://myregexp.com/
                //String pattern = "(.*) (Checking out Revision) ([a-f,0-9]+) (\\(repo[1|2]/master\\)) (First time build\\.) (.*)";
                //Checking out Revision 0ae5858942afa97b86770da779f91c80b39694e4 (repo3/ready/repo3_feature_1)
                String pattern1 = "(.*) (Checking out Revision) ([a-f,0-9]+) (\\(repo3/ready/repo3_feature_1\\)) (.*)";
                // Create a Pattern object
                Pattern p1 = Pattern.compile(pattern1);
                // [PREINT] Preparing to merge changes in commit a1b88be91358cf4cb184c645cfdb0920a765d872 on development branch origin/ready/twoCommitsBranch to integration branch master
                String pattern2 = "(.*) (Preparing to merge changes in commit) ([a-f,0-9]+) (on development branch repo3/ready/repo3_feature_1) (to integration branch master)(.*)";
                // Create a Pattern object
                Pattern p2 = Pattern.compile(pattern2);

                Matcher m1 = p1.matcher(console);
                Matcher m2 = p2.matcher(console);
                assertTrue(String.format("Didn't complete regexp match in build %s trying to integrate repo3", buildname), m1.find() && m2.find());
                System.out.println("Found values: " + m1.group(3) + " ?= " + m2.group(3));
                assertTrue("Revision found on branch head does not match revision being integrated", m1.group(3).equals(m2.group(3)));

                assertTrue("Could not find message in console about ready branch in repo beeing integrated",
                        console.contains("merge --squash repo3/ready/repo3_feature_1"));
                assertTrue("Integration of ready branch in repo started, but could not match push command in console.",
                        console.contains("push repo3 :ready/repo3_feature_1"));
                System.out.println("Verified successful build");
                integratedRepo3 = true;
            } else if (build.getResult().equals(Result.FAILURE)) {
                // Failure is okay, if it is the git plugin causing it.
                assertTrue("Failed build only allowed if it is the git plugin.", console.contains("ERROR: Couldn't find any revision to build. Verify the repository and branch configuration for this job."));
                System.out.println("Verified failed build");
                failedOnOtherRepoChanges = true;
            } else if (build.getResult().equals(Result.NOT_BUILT)) { 
                // If we now also see this message, we have the situation and problem
                // reported in JENKINS-25960
                // and will fail with this message:
                if (console.contains("The branch name (repo3/ready/repo3_feature_1) contained in the git build data object, did not match a remote branch name")) {
                    assertTrue("This test fails due to JENKINS-25960 - when fixed it will not fail", false);
                } else {
                    assertTrue(String.format("Unexpected build result found: %s", build.getResult()), false);
                }
                
            } else {
                // all other build results
                assertTrue(String.format("Unexpected build result found: %s", build.getResult()), false);
            }
            verified = integratedRepo3 && failedOnOtherRepoChanges;
        }
        System.out.println("Verified both expected builds");
        assertTrue("Seems like not all jobs on test was covered in the test", verified); // safety check if loop logic wrong
    }
}
