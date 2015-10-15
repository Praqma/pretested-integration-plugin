package org.jenkinsci.plugins.pretestedintegration.integration.scm.git;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.util.RunList;
import java.util.Iterator;
import static junit.framework.Assert.assertTrue;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Tests for checking commit messages can be integrated even though the author
 * have been using double quotes or other special characters in the commit
 * message.
 *
 * Problem is the command line call the plugin does and the risk of not escaping
 * all characters correctly.
 */
public class CommitMessagesWithSpecialCharsIT extends StaticGitRepositoryTestBase {

    /**
     * Tests commit message with double quotes in commit message can be
     * integrated using squashed strategy and a repository created on Linux. See
     * detailed description of test in class documentation.
     *
     * Uses the StaticGitRepositoryTestBase and the setUp method there, so a
     * bareRepository and gitRepo is already available.
     *
     * @throws Exception
     */
    @Test
    public void commitMessagesWithDoubleQuotesSquashedLinux() throws Exception {
        /**
         * ********************************************************************
         * Run test with Jenkins job trying to integrate the feature branch
         * ********************************************************************
         */
        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.SQUASH, bareRepository);
        TestUtilsFactory.triggerProject(project);
        jenkinsRule.waitUntilNoActivityUpTo(60000);

        RunList<FreeStyleBuild> builds = project.getBuilds();
        for (FreeStyleBuild b : builds) {
            assertTrue("Could not match console output for pretty printing", TestUtilsFactory.printAndReturnConsoleOfBuild(b, String.format("%s", b.getNumber()), jenkinsRule));
            Result result = b.getResult();
            assertNotNull("Build result was null.", result);
            assertTrue("Build was not a success - that is expected in this test", result.isBetterOrEqualTo(Result.SUCCESS));
        }

        /**
         * ********************************************************************
         * Verify integration in different aspect like commit message content
         * and actual commits
         * ********************************************************************
         */
        // Verify number of commits - first count on master after integration
        gitrepo.checkout().setName("master").call();
        gitrepo.checkout().setName("master").setUpstreamMode(SetupUpstreamMode.TRACK).call();
        gitrepo.pull().call();
        int commitsOnMasterAfterIntegration = TestUtilsFactory.countCommitsOnBranch(gitrepo, "master");

        // Squash commit ends on master, and there is one to start with:
        assertEquals(2, commitsOnMasterAfterIntegration);

        // This will "walk" over the log, but we only take the first entry
        // which will be the integration commit
        RevWalk walk = new RevWalk(gitrepo.getRepository());
        Iterable<RevCommit> log = gitrepo.log().call();
        Iterator<RevCommit> i = log.iterator();
        RevCommit commit = walk.parseCommit(i.next());

        String commitFullMessage = commit.getFullMessage();
        gitrepo.close();

        System.out.println("************************************************************************");
        System.out.println("* Integration commit message (between the ---):");
        System.out.println("---");
        System.out.println(commitFullMessage);
        System.out.println("---");
        System.out.println("************************************************************************");
        assertTrue("The integration commit message was not a squashed commit. Didn't find the text 'Squashed commit of the following:'", commitFullMessage.contains("Squashed commit of the following:"));
        assertTrue("The integration commit message, doesn't contain the SHA from the git log from the first of the included commits", commitFullMessage.contains("commit 57807c99bea0fa0f929bd32973ff9651f7c0fb04"));
        assertTrue("The integration commit message, doesn't contain lines from the original commits as expected.", commitFullMessage.contains("This is a commit message with double quotes, eg. \"test quotes\"."));
        assertTrue("The integration commit message, didn't contain expected date string from the git log from the first of the included commits", commitFullMessage.contains("Tue Mar 31 16:13:30 2015 +0200"));
        assertTrue("The integration commit message, didn't contain expected author string from the git log from the first of the included commits", commitFullMessage.contains("Author: Praqma Support <support@praqma.net>"));
        assertFalse("The last commit on integration branch contain 'accumulated' but is not an accumulated commit", commitFullMessage.contains("ccumulated"));
        System.out.println(String.format("***** phew... last verification (asserts) passed :-)"));
    }

    /**
     * Tests commit message with double quotes in commit message can be
     * integrated using accumulated strategy and a repository created on Linux.
     * See detailed description of test in class documentation.
     *
     * Uses the StaticGitRepositoryTestBase and the setUp method there, so a
     * bareRepository and gitRepo is already available.
     *
     * @throws Exception
     */
    @Test
    public void commitMessagesWithDoubleQuotesAccumulatedLinux() throws Exception {
        /**
         * ********************************************************************
         * Run test with Jenkins job trying to integrate the feature branch
         * ********************************************************************
         */
        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.ACCUMULATED, bareRepository);
        TestUtilsFactory.triggerProject(project);
        jenkinsRule.waitUntilNoActivityUpTo(60000);

        RunList<FreeStyleBuild> builds = project.getBuilds();
        for (FreeStyleBuild b : builds) {
            assertTrue("Could not match console output for pretty printing", TestUtilsFactory.printAndReturnConsoleOfBuild(b, String.format("%s", b.getNumber()), jenkinsRule));
            Result result = b.getResult();
            assertNotNull("Build result was null.", result);
            assertTrue("Build was not a success - that is expected in this test", result.isBetterOrEqualTo(Result.SUCCESS));
        }

        /**
         * ********************************************************************
         * Verify integration in different aspect like commit message content
         * and actual commits
         * ********************************************************************
         */
        // Verify number of commits - first count on master after integration
        gitrepo.checkout().setName("master").call();
        gitrepo.checkout().setName("master").setUpstreamMode(SetupUpstreamMode.TRACK).call();
        gitrepo.pull().call();
        int commitsOnMasterAfterIntegration = TestUtilsFactory.countCommitsOnBranch(gitrepo, "master");
        // All commits end on master, and there is a merge commit
        assertEquals(4, commitsOnMasterAfterIntegration);

        // This will "walk" over the log, but we only take the first entry
        // which will be the integration commit
        RevWalk walk = new RevWalk(gitrepo.getRepository());
        Iterable<RevCommit> log = gitrepo.log().call();
        Iterator<RevCommit> i = log.iterator();
        RevCommit commit = walk.parseCommit(i.next());

        String commitFullMessage = commit.getFullMessage();
        gitrepo.close();

        System.out.println("************************************************************************");
        System.out.println("* Integration commit message (between the ---):");
        System.out.println("---");
        System.out.println(commitFullMessage);
        System.out.println("---");
        System.out.println("************************************************************************");
        assertTrue("The integration commit message was not an accumulated commit. Didn't find the text 'Accumulated commit'", commitFullMessage.contains("Accumulated commit of the following from branch 'origin/ready/JENKINS-27662_doublequotes':"));
        assertTrue("The integration commit message doesn't contain the SHA from the git log from the first of the included commits", commitFullMessage.contains("commit 57807c99bea0fa0f929bd32973ff9651f7c0fb04"));
        assertTrue("The integration commit message doesn't contain lines from the original commits as expected.", commitFullMessage.contains("This is a commit message with double quotes, eg. 'test quotes'."));
        assertTrue("The integration commit message, didn't contain expected date string from the git log from the first of the included commits", commitFullMessage.contains("Tue Mar 31 16:13:30 2015 +0200"));
        assertTrue("The integration commit message, didn't contain expected author string from the git log from the first of the included commits", commitFullMessage.contains("Author: Praqma Support <support@praqma.net>"));
        assertFalse("The last commit on integration branch contain 'squash' but is not a squash commit", commitFullMessage.contains("squash"));
        System.out.println(String.format("***** phew... last verification (asserts) passed :-)"));
    }

    /**
     * Tests commit message with double quotes in commit message can be
     * integrated using squashed strategy and a repository created on Windows.
     * See detailed description of test in class documentation.
     *
     * Uses the StaticGitRepositoryTestBase and the setUp method there, so a
     * bareRepository and gitRepo is already available.
     *
     * @throws Exception
     */
    @Test
    public void commitMessagesWithDoubleQuotesSquashedWindows() throws Exception {

        /**
         * ********************************************************************
         * Run test with Jenkins job trying to integrate the feature branch
         * ********************************************************************
         */
        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.SQUASH, bareRepository);
        TestUtilsFactory.triggerProject(project);
        jenkinsRule.waitUntilNoActivityUpTo(60000);

        RunList<FreeStyleBuild> builds = project.getBuilds();
        for (FreeStyleBuild b : builds) {
            assertTrue("Could not match console output for pretty printing", TestUtilsFactory.printAndReturnConsoleOfBuild(b, String.format("%s", b.getNumber()), jenkinsRule));
            Result result = b.getResult();
            assertNotNull("Build result was null.", result);
            assertTrue("Build was not a success - that is expected in this test", result.isBetterOrEqualTo(Result.SUCCESS));
        }

        /**
         * ********************************************************************
         * Verify integration in different aspect like commit message content
         * and actual commits
         * ********************************************************************
         */
        // Verify number of commits - first count on master after integration
        gitrepo.checkout().setName("master").call();
        gitrepo.checkout().setName("master").setUpstreamMode(SetupUpstreamMode.TRACK).call();
        gitrepo.pull().call();
        int commitsOnMasterAfterIntegration = TestUtilsFactory.countCommitsOnBranch(gitrepo, "master");

        // All commits end on master, and there is a merge commit
        assertEquals(2, commitsOnMasterAfterIntegration);

        // This will "walk" over the log, but we only take the first entry
        // which will be the integration commit
        RevWalk walk = new RevWalk(gitrepo.getRepository());
        Iterable<RevCommit> log = gitrepo.log().call();
        Iterator<RevCommit> i = log.iterator();
        RevCommit commit = walk.parseCommit(i.next());

        String commitFullMessage = commit.getFullMessage();
        gitrepo.close();

        System.out.println("************************************************************************");
        System.out.println("* Integration commit message (between the ---):");
        System.out.println("---");
        System.out.println(commitFullMessage);
        System.out.println("---");
        System.out.println("************************************************************************");
        assertTrue("The integration commit message was not a squashed commit. Didn't find the text 'Squashed commit of the following:'", commitFullMessage.contains("Squashed commit of the following:"));
        assertTrue("The integration commit message, doesn't contain the SHA from the git log from the first of the included commits", commitFullMessage.contains("commit fa90cb266c4f737a09ad4a3308ec4fb5b898b9d6"));
        assertTrue("The integration commit message, doesn't contain lines from the original commits as expected.", commitFullMessage.contains("This is a commit message with double quotes (commit made on Windows), eg. \"test quotes\"."));
        assertTrue("The integration commit message, didn't contain expected date string from the git log from the first of the included commits", commitFullMessage.contains("Tue Mar 31 16:08:22 2015 +0200"));
        assertTrue("The integration commit message, didn't contain expected author string from the git log from the first of the included commits", commitFullMessage.contains("Author: Praqma Support <support@praqma.net>"));
        assertFalse("The last commit on integration branch contain 'accumulated' but is not an accumulated commit", commitFullMessage.contains("ccumulated"));
        System.out.println(String.format("***** phew... last verification (asserts) passed :-)"));
    }

    /**
     * Tests commit message with double quotes in commit message can be
     * integrated using accumulated strategy and a repository created on
     * Windows. See detailed description of test in class documentation.
     *
     * Uses the StaticGitRepositoryTestBase and the setUp method there, so a
     * bareRepository and gitRepo is already available.
     *
     * @throws Exception
     */
    @Test
    public void commitMessagesWithDoubleQuotesAccumulatedWindows() throws Exception {

        /**
         * ********************************************************************
         * Run test with Jenkins job trying to integrate the feature branch
         * ********************************************************************
         */
        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.ACCUMULATED, bareRepository);
        TestUtilsFactory.triggerProject(project);
        jenkinsRule.waitUntilNoActivityUpTo(60000);

        RunList<FreeStyleBuild> builds = project.getBuilds();
        for (FreeStyleBuild b : builds) {
            assertTrue("Could not match console output for pretty printing", TestUtilsFactory.printAndReturnConsoleOfBuild(b, String.format("%s", b.getNumber()), jenkinsRule));
            Result result = b.getResult();
            assertNotNull("Build result was null.", result);
            assertTrue("Build was not a success - that is expected in this test", result.isBetterOrEqualTo(Result.SUCCESS));
        }

        /**
         * ********************************************************************
         * Verify integration in different aspect like commit message content
         * and actual commits
         * ********************************************************************
         */
        // Verify number of commits - first count on master after integration
        gitrepo.checkout().setName("master").call();
        gitrepo.checkout().setName("master").setUpstreamMode(SetupUpstreamMode.TRACK).call();
        gitrepo.pull().call();
        int commitsOnMasterAfterIntegration = TestUtilsFactory.countCommitsOnBranch(gitrepo, "master");
        // All commits end on master, and there is a merge commit
        assertEquals(4, commitsOnMasterAfterIntegration);

        // This will "walk" over the log, but we only take the first entry
        // which will be the integration commit
        RevWalk walk = new RevWalk(gitrepo.getRepository());
        Iterable<RevCommit> log = gitrepo.log().call();
        Iterator<RevCommit> i = log.iterator();
        RevCommit commit = walk.parseCommit(i.next());

        String commitFullMessage = commit.getFullMessage();
        gitrepo.close();

        System.out.println("************************************************************************");
        System.out.println("* Integration commit message (between the ---):");
        System.out.println("---");
        System.out.println(commitFullMessage);
        System.out.println("---");
        System.out.println("************************************************************************");
        assertTrue("The integration commit message was not an accumulated commit. Didn't find the text 'Accumulated commit'", commitFullMessage.contains("Accumulated commit of the following from branch 'origin/ready/JENKINS-27662_doublequotes':"));
        assertTrue("The integration commit message doesn't contain the SHA from the git log from the first of the included commits", commitFullMessage.contains("commit fa90cb266c4f737a09ad4a3308ec4fb5b898b9d6"));
        assertTrue("The integration commit message doesn't contain lines from the original commits as expected.", commitFullMessage.contains("This is a commit message with double quotes (commit made on Windows), eg. 'test quotes'."));
        assertTrue("The integration commit message, didn't contain expected date string from the git log from the first of the included commits", commitFullMessage.contains("Tue Mar 31 16:08:22 2015 +0200"));
        assertTrue("The integration commit message, didn't contain expected author string from the git log from the first of the included commits", commitFullMessage.contains("Author: Praqma Support <support@praqma.net>"));
        assertFalse("The last commit on integration branch contain 'squash' but is not a squash commit", commitFullMessage.contains("squash"));
        System.out.println(String.format("***** phew... last verification (asserts) passed :-)"));
    }

    /**
     * Tests commit message with double quotes, created in a script using single
     * quotes can be integrated using accumulated strategy. Repository created
     * on Windows. See detailed description of test in class documentation.
     *
     * Uses the StaticGitRepositoryTestBase and the setUp method there, so a
     * bareRepository and gitRepo is already available.
     *
     * @throws Exception
     */
    @Test
    public void commitMessagesWithDoubleQuotesSingleQuotesMadeWindowsAccumulated() throws Exception {

        /**
         * ********************************************************************
         * Run test with Jenkins job trying to integrate the feature branch
         * ********************************************************************
         */
        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.ACCUMULATED, bareRepository);
        TestUtilsFactory.triggerProject(project);
        jenkinsRule.waitUntilNoActivityUpTo(60000);

        RunList<FreeStyleBuild> builds = project.getBuilds();
        for (FreeStyleBuild b : builds) {
            assertTrue("Could not match console output for pretty printing", TestUtilsFactory.printAndReturnConsoleOfBuild(b, String.format("%s", b.getNumber()), jenkinsRule));
            Result result = b.getResult();
            assertNotNull("Build result was null.", result);
            assertTrue("Build was not a success - that is expected in this test", result.isBetterOrEqualTo(Result.SUCCESS));
        }

        /**
         * ********************************************************************
         * Verify integration in different aspect like commit message content
         * and actual commits
         * ********************************************************************
         */
        // Verify number of commits - first count on master after integration
        gitrepo.checkout().setName("master").call();
        gitrepo.checkout().setName("master").setUpstreamMode(SetupUpstreamMode.TRACK).call();
        gitrepo.pull().call();
        int commitsOnMasterAfterIntegration = TestUtilsFactory.countCommitsOnBranch(gitrepo, "master");

        // All commits end on master, and there is a merge commit
        assertEquals(4, commitsOnMasterAfterIntegration);

        // This will "walk" over the log, but we only take the first entry
        // which will be the integration commit
        RevWalk walk = new RevWalk(gitrepo.getRepository());
        Iterable<RevCommit> log = gitrepo.log().call();
        Iterator<RevCommit> i = log.iterator();
        RevCommit commit = walk.parseCommit(i.next());

        String commitFullMessage = commit.getFullMessage();
        gitrepo.close();

        System.out.println("************************************************************************");
        System.out.println("* Integration commit message (between the ---):");
        System.out.println("---");
        System.out.println(commitFullMessage);
        System.out.println("---");
        System.out.println("************************************************************************");
        assertTrue("The integration commit message was not an accumulated commit. Didn't find the text 'Accumulated commit'", commitFullMessage.contains("Accumulated commit of the following from branch 'origin/ready/JENKINS-27662_doublequotes':"));
        assertTrue("The integration commit message, doesn't contain the SHA from the git log from the first of the included commits", commitFullMessage.contains("commit a094a8a6e8157b386b651d61997de25cd95af5eb"));
        assertTrue("The integration commit message, doesn't contain lines from the original commits as expected.", commitFullMessage.contains("This is a commit message with double quotes (commit made on Windows), and =, eg. 'test quotes'."));
        assertTrue("The integration commit message, didn't contain expected date string from the git log from the first of the included commits", commitFullMessage.contains("Wed Jun 3 09:14:28 2015 +0200"));
        assertTrue("The integration commit message, didn't contain expected author string from the git log from the first of the included commits", commitFullMessage.contains("Author: Praqma Support <support@praqma.net>"));
        assertFalse("The last commit on integration branch contain 'squash' but is not a squash commit", commitFullMessage.contains("squash"));
        System.out.println(String.format("***** phew... last verification (asserts) passed :-)"));
    }

    /**
     * Tests commit message with double quotes, created in a script using single
     * quotes can be integrated using squash strategy. Repository created on
     * Windows. See detailed description of test in class documentation.
     *
     * Uses the StaticGitRepositoryTestBase and the setUp method there, so a
     * bareRepository and gitRepo is already available.
     *
     * @throws Exception
     */
    @Test
    public void commitMessagesWithDoubleQuotesSingleQuotesMadeWindowsSquashed() throws Exception {

        /**
         * ********************************************************************
         * Run test with Jenkins job trying to integrate the feature branch
         * ********************************************************************
         */
        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.SQUASH, bareRepository);
        TestUtilsFactory.triggerProject(project);
        jenkinsRule.waitUntilNoActivityUpTo(60000);

        RunList<FreeStyleBuild> builds = project.getBuilds();
        for (FreeStyleBuild b : builds) {
            assertTrue("Could not match console output for pretty printing", TestUtilsFactory.printAndReturnConsoleOfBuild(b, String.format("%s", b.getNumber()), jenkinsRule));
            Result result = b.getResult();
            assertNotNull("Build result was null.", result);
            assertTrue("Build was not a success - that is expected in this test", result.isBetterOrEqualTo(Result.SUCCESS));
        }

        /**
         * ********************************************************************
         * Verify integration in different aspect like commit message content
         * and actual commits
         * ********************************************************************
         */
        // Verify number of commits - first count on master after integration
        gitrepo.checkout().setName("master").call();
        gitrepo.checkout().setName("master").setUpstreamMode(SetupUpstreamMode.TRACK).call();
        gitrepo.pull().call();
        int commitsOnMasterAfterIntegration = TestUtilsFactory.countCommitsOnBranch(gitrepo, "master");

        // All commits end on master, and there is a merge commit
        assertEquals(2, commitsOnMasterAfterIntegration);

        // This will "walk" over the log, but we only take the first entry
        // which will be the integration commit
        RevWalk walk = new RevWalk(gitrepo.getRepository());
        Iterable<RevCommit> log = gitrepo.log().call();
        Iterator<RevCommit> i = log.iterator();
        RevCommit commit = walk.parseCommit(i.next());

        String commitFullMessage = commit.getFullMessage();
        gitrepo.close();

        System.out.println("************************************************************************");
        System.out.println("* Integration commit message (between the ---):");
        System.out.println("---");
        System.out.println(commitFullMessage);
        System.out.println("---");
        System.out.println("************************************************************************");
        assertTrue("The integration commit message was not a squashed commit. Didn't find the text 'Squashed commit of the following:'", commitFullMessage.contains("Squashed commit of the following:"));
        assertTrue("The integration commit message, doesn't contain the SHA from the git log from the first of the included commits", commitFullMessage.contains("commit a094a8a6e8157b386b651d61997de25cd95af5eb"));
        assertTrue("The integration commit message, doesn't contain lines from the original commits as expected.", commitFullMessage.contains("This is a commit message with double quotes (commit made on Windows), and =, eg. \"test quotes\"."));
        assertTrue("The integration commit message, didn't contain expected date string from the git log from the first of the included commits", commitFullMessage.contains("Wed Jun 3 09:14:28 2015 +0200"));
        assertTrue("The integration commit message, didn't contain expected author string from the git log from the first of the included commits", commitFullMessage.contains("Author: Praqma Support <support@praqma.net>"));
        assertFalse("The last commit on integration branch contain 'accumulated' but is not an accumulated commit", commitFullMessage.contains("ccumulated"));
        System.out.println(String.format("***** phew... last verification (asserts) passed :-)"));
    }

    /**
     * Tests commit message with double quotes, created in a repository we have
     * been supplied with for testing by a customer. Test is using accumulated
     * strategy. Repository created on Windows. See detailed description of test
     * in class documentation.
     *
     * Uses the StaticGitRepositoryTestBase and the setUp method there, so a
     * bareRepository and gitRepo is already available.
     *
     * @throws Exception
     */
    @Ignore("Wait for Bue's input on changing double quotes to single quotes during FF merges.")
    @Test
    public void commitMessagesWithDoubleQuotesSingleQuotesMadeWindowsAccumulated_customerSuppliedRepo() throws Exception {

        /**
         * ********************************************************************
         * Run test with Jenkins job trying to integrate the feature branch
         * ********************************************************************
         */
        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.ACCUMULATED, bareRepository);
        TestUtilsFactory.triggerProject(project);
        jenkinsRule.waitUntilNoActivityUpTo(60000);

        RunList<FreeStyleBuild> builds = project.getBuilds();
        for (FreeStyleBuild b : builds) {
            assertTrue("Could not match console output for pretty printing", TestUtilsFactory.printAndReturnConsoleOfBuild(b, String.format("%s", b.getNumber()), jenkinsRule));
            Result result = b.getResult();
            assertNotNull("Build result was null.", result);
            assertTrue("Build was not a success - that is expected in this test", result.isBetterOrEqualTo(Result.SUCCESS));
        }

        /**
         * ********************************************************************
         * Verify integration in different aspect like commit message content
         * and actual commits
         * ********************************************************************
         */
        // Verify number of commits - first count on master after integration
        gitrepo.checkout().setName("master").call();
        gitrepo.checkout().setName("master").setUpstreamMode(SetupUpstreamMode.TRACK).call();
        gitrepo.pull().call();
        int commitsOnMasterAfterIntegration = TestUtilsFactory.countCommitsOnBranch(gitrepo, "master");

        // Two commits already exists, then add the accumulated commit:
        assertEquals(3, commitsOnMasterAfterIntegration);

        // This will "walk" over the log, but we only take the first entry
        // which will be the integration commit
        RevWalk walk = new RevWalk(gitrepo.getRepository());
        Iterable<RevCommit> log = gitrepo.log().call();
        Iterator<RevCommit> i = log.iterator();
        RevCommit commit = walk.parseCommit(i.next());

        String commitFullMessage = commit.getFullMessage();
        gitrepo.close();

        System.out.println("************************************************************************");
        System.out.println("* Integration commit message (between the ---):");
        System.out.println("---");
        System.out.println(commitFullMessage);
        System.out.println("---");
        System.out.println("************************************************************************");
        assertTrue("The integration commit message was not an accumulated commit. Didn't find the text 'Accumulated commit'", commitFullMessage.contains("Accumulated commit of the following from branch 'origin/ready/JENKINS-28640'"));
        assertTrue("The integration commit message, doesn't contain the SHA from the git log from the first of the included commits", commitFullMessage.contains("commit 036ac2b7c896313bb799eb88ce89d7156b19f9e3"));
        assertTrue("The integration commit message, didn't contain expected date string from the git log from the first of the included commits", commitFullMessage.contains("Mon Jul 6 13:15:47 2015 +0200"));
        assertTrue("The integration commit message, didn't contain expected author string from the git log from the first of the included commits", commitFullMessage.contains("Author: Test Author <test@atmel.com>"));
        assertTrue("The integration commit message, doesn't contain lines from the original commits as expected.", commitFullMessage.contains(" AVRSV-6716 'program flash from RAM' option is disable under tools options in properties window and Memories tab in Device programming dialog"));
        assertFalse("The last commit on integration branch contain 'squash' but is not a squash commit", commitFullMessage.contains("squash"));
        System.out.println(String.format("***** phew... last verification (asserts) passed :-)"));
    }

    /**
     * Tests commit message with double quotes, created in a repository we have
     * been supplied with for testing by a customer. Test is using squashed
     * strategy. Repository created on Windows. See detailed description of test
     * in class documentation.
     *
     * Uses the StaticGitRepositoryTestBase and the setUp method there, so a
     * bareRepository and gitRepo is already available.
     *
     * @throws Exception
     */
    @Test
    @Ignore // The repository have an empty commit, that can't be squashed with our ordinary git commands. See https://issues.jenkins-ci.org/browse/JENKINS-29371
    public void commitMessagesWithDoubleQuotesSingleQuotesMadeWindowsSquashed_customerSuppliedRepo() throws Exception {

        /**
         * ********************************************************************
         * Run test with Jenkins job trying to integrate the feature branch
         * ********************************************************************
         */
        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.SQUASH, bareRepository);
        TestUtilsFactory.triggerProject(project);
        jenkinsRule.waitUntilNoActivityUpTo(60000);

        RunList<FreeStyleBuild> builds = project.getBuilds();
        for (FreeStyleBuild b : builds) {
            assertTrue("Could not match console output for pretty printing", TestUtilsFactory.printAndReturnConsoleOfBuild(b, String.format("%s", b.getNumber()), jenkinsRule));
            Result result = b.getResult();
            assertNotNull("Build result was null.", result);
            assertTrue("Build was not a success - that is expected in this test", result.isBetterOrEqualTo(Result.SUCCESS));
        }

        /**
         * ********************************************************************
         * Verify integration in different aspect like commit message content
         * and actual commits
         * ********************************************************************
         */
        // Verify number of commits - first count on master after integration
        gitrepo.checkout().setName("master").call();
        gitrepo.checkout().setName("master").setUpstreamMode(SetupUpstreamMode.TRACK).call();
        gitrepo.pull().call();
        int commitsOnMasterAfterIntegration = TestUtilsFactory.countCommitsOnBranch(gitrepo, "master");

        // Two commits already exists, then add the accumulated commit:
        assertEquals(2, commitsOnMasterAfterIntegration);

        // This will "walk" over the log, but we only take the first entry
        // which will be the integration commit
        RevWalk walk = new RevWalk(gitrepo.getRepository());
        Iterable<RevCommit> log = gitrepo.log().call();
        Iterator<RevCommit> i = log.iterator();
        RevCommit commit = walk.parseCommit(i.next());

        String commitFullMessage = commit.getFullMessage();
        gitrepo.close();

        System.out.println("************************************************************************");
        System.out.println("* Integration commit message (between the ---):");
        System.out.println("---");
        System.out.println(commitFullMessage);
        System.out.println("---");
        System.out.println("************************************************************************");
        assertTrue("The integration commit message was not a squashed commit. Didn't find the text 'Squashed commit of the following:'", commitFullMessage.contains("Squashed commit of the following:"));
        assertTrue("The integration commit message, doesn't contain the SHA from the git log from the first of the included commits", commitFullMessage.contains("commit 036ac2b7c896313bb799eb88ce89d7156b19f9e3"));
        assertTrue("The integration commit message, didn't contain expected date string from the git log from the first of the included commits", commitFullMessage.contains("Mon Jul 6 13:15:47 2015 +0200"));
        assertTrue("The integration commit message, didn't contain expected author string from the git log from the first of the included commits", commitFullMessage.contains("Author: Test Author <test@atmel.com>"));
        assertTrue("The integration commit message, doesn't contain lines from the original commits as expected.", commitFullMessage.contains(" AVRSV-6716 'program flash from RAM' option is disable under tools options in properties window and Memories tab in Device programming dialog"));
        assertFalse("The last commit on integration branch contain 'accumulated' but is not an accumulated commit", commitFullMessage.contains("ccumulated"));
        System.out.println(String.format("***** phew... last verification (asserts) passed :-)"));
    }
}
