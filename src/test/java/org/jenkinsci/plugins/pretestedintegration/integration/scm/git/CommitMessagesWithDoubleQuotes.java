package org.jenkinsci.plugins.pretestedintegration.integration.scm.git;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.util.RunList;
import static junit.framework.TestCase.assertEquals;
import org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode;
import org.junit.Test;

/**
 * Tests for checking commit messages can be integrated even though the author
 * have been using double quotes in the commit message. The repository looks
 * like this:
 * -----------------------------------------------------------------------------
 * * 57807c9 - (HEAD, origin/ready/JENKINS-27662_doublequotes,
 * origin/dev/JENKINS-27662_doublequotes, dev/JENKINS-27662_doublequotes) This
 * is a commit message with double quotes, eg. "test quotes". * 4ca68de - Added
 * test commit log file * 116912c - (origin/master, master) Initial commit -
 * added README
 * -----------------------------------------------------------------------------
 *
 * The last commit on the ready-branch is using double quotes, and should be
 * able to integrate. Integrated commit message should look like this
 * (respectively squashed and accumulated):
 * -----------------------------------------------------------------------------
 * Squashed commit of the following:
 *
 * commit fa90cb266c4f737a09ad4a3308ec4fb5b898b9d6 Author: Praqma Support
 * <support@praqma.net>
 * Date: Tue Mar 31 16:08:22 2015 +0200
 *
 * This is a commit message with double quotes (commit made on Windows), eg.
 * "test quotes".
 *
 * commit 76cf8f90df56260e84b324002603010505e2ab2d Author: Praqma Support
 * <support@praqma.net>
 * Date: Tue Mar 31 16:08:22 2015 +0200
 *
 * Added test commit log file
 *
 * -----------------------------------------------------------------------------
 * Accumulated commit of the following from branch
 * 'origin/ready/JENKINS-27662_doublequotes':
 *
 * commit 57807c99bea0fa0f929bd32973ff9651f7c0fb04 Author: Praqma Support
 * <support@praqma.net>
 * Date: Tue Mar 31 04:13:30 2015 +0200
 *
 * This is a commit message with double quotes, eg. "test quotes".
 *
 * commit 4ca68de437d106b6600bef36d6887c8527275652 Author: Praqma Support
 * <support@praqma.net>
 * Date: Tue Mar 31 04:13:30 2015 +0200
 *
 * Added test commit log file
 *
 * -----------------------------------------------------------------------------
 *
 * There will be done four tests, each strategy - accumulated + squashed - and
 * on two almost identical repositories (created from same recipe) on Linux and
 * Windows.
 *
 * @author bue
 */
public class CommitMessagesWithDoubleQuotes extends StaticGitRepositoryTestBase {

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
         *********************************************************************
         */
        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.SQUASH, bareRepository);
        TestUtilsFactory.triggerProject(project);
        jenkinsRule.waitUntilNoActivityUpTo(60000);

        RunList<FreeStyleBuild> builds = project.getBuilds();
        for (FreeStyleBuild b : builds) {
            String console = jenkinsRule.createWebClient().getPage(b, "console").asText();
            System.out.println(console);
        }

        /**
         * ********************************************************************
         * Verify integration in different aspect like commit message content
         * and actual commits
         *********************************************************************
         */
        // Verify number of commits - first count on master after integration
        gitrepo.checkout().setName("master").call();
        gitrepo.checkout().setName("master").setUpstreamMode(SetupUpstreamMode.TRACK).call();
        gitrepo.pull().call();
        int commitsOnMasterAfterIntegration = TestUtilsFactory.countCommitsOnBranch(gitrepo, "master");
        gitrepo.close();

        // Squash commit ends on master, and there is one to start with:
        assertEquals(2, commitsOnMasterAfterIntegration);

        // using our little build result validator, see which string the head
        // commit on master contains. Head commit will in this case be the
        // new accumulated commit.
        // These values are hard-coded and comes from the static git repository
        // used in this test.
        try (BuildResultValidator buildResultValidator = new BuildResultValidator(builds.getLastBuild(), bareRepository)) {
            buildResultValidator.hasHeadCommitContents(
                    String.format("Squashed commit of the following:"),
                    String.format("commit 57807c99bea0fa0f929bd32973ff9651f7c0fb04"),
                    String.format("This is a commit message with double quotes, eg. \"test quotes\".")
            ).retain().validate();
        }
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
         *********************************************************************
         */
        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.ACCUMULATED, bareRepository);
        TestUtilsFactory.triggerProject(project);
        jenkinsRule.waitUntilNoActivityUpTo(60000);

        RunList<FreeStyleBuild> builds = project.getBuilds();
        for (FreeStyleBuild b : builds) {
            String console = jenkinsRule.createWebClient().getPage(b, "console").asText();
            System.out.println(console);
        }

        /**
         * ********************************************************************
         * Verify integration in different aspect like commit message content
         * and actual commits
         *********************************************************************
         */
        // Verify number of commits - first count on master after integration
        gitrepo.checkout().setName("master").call();
        gitrepo.checkout().setName("master").setUpstreamMode(SetupUpstreamMode.TRACK).call();
        gitrepo.pull().call();
        int commitsOnMasterAfterIntegration = TestUtilsFactory.countCommitsOnBranch(gitrepo, "master");
        gitrepo.close();

        /*        
         After the accumulated commit of the two commits 7933d8b, 4451bda on
         branch ready/myfeature it will look like this:
         ----------------------------------------------------------------------------
         *   49939e9 - (HEAD, origin/master, master) Accumulated commit of the following from branch 'origin/ready/myfeature': <John Doe>
         |\  
         | * 4451bda - (origin/ready/myfeature, ready/myfeature) Updated infofile file again on branch ready/myfeature <john Doe>
         | * 7933d8b - Updated infofile file on branch ready/myfeature <john Doe>
         |/  
         * 7770f99 - Updated infofile file on branch master <john Doe>
         * bb407fc - Readme file created on branch master <john Doe>
         ----------------------------------------------------------------------------
         */
        // All commits end on master, and there is a merge commit
        assertEquals(4, commitsOnMasterAfterIntegration);

        // using our little build result validator, see which string the head
        // commit on master contains. Head commit will in this case be the
        // new accumulated commit.
        try (BuildResultValidator buildResultValidator = new BuildResultValidator(builds.getLastBuild(), bareRepository)) {
            buildResultValidator.hasHeadCommitContents(
                    String.format("Accumulated commit of the following from branch 'origin/ready/JENKINS-27662_doublequotes':"),
                    String.format("commit 57807c99bea0fa0f929bd32973ff9651f7c0fb04"),
                    String.format("This is a commit message with double quotes, eg. \"test quotes\".")
            ).retain().validate();
        }
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
         *********************************************************************
         */
        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.SQUASH, bareRepository);
        TestUtilsFactory.triggerProject(project);
        jenkinsRule.waitUntilNoActivityUpTo(60000);

        RunList<FreeStyleBuild> builds = project.getBuilds();
        for (FreeStyleBuild b : builds) {
            String console = jenkinsRule.createWebClient().getPage(b, "console").asText();
            System.out.println(console);
        }

        /**
         * ********************************************************************
         * Verify integration in different aspect like commit message content
         * and actual commits
         *********************************************************************
         */
        // Verify number of commits - first count on master after integration
        gitrepo.checkout().setName("master").call();
        gitrepo.checkout().setName("master").setUpstreamMode(SetupUpstreamMode.TRACK).call();
        gitrepo.pull().call();
        int commitsOnMasterAfterIntegration = TestUtilsFactory.countCommitsOnBranch(gitrepo, "master");
        gitrepo.close();

        /*        
         After the accumulated commit of the two commits 7933d8b, 4451bda on
         branch ready/myfeature it will look like this:
         ----------------------------------------------------------------------------
         *   49939e9 - (HEAD, origin/master, master) Accumulated commit of the following from branch 'origin/ready/myfeature': <John Doe>
         |\  
         | * 4451bda - (origin/ready/myfeature, ready/myfeature) Updated infofile file again on branch ready/myfeature <john Doe>
         | * 7933d8b - Updated infofile file on branch ready/myfeature <john Doe>
         |/  
         * 7770f99 - Updated infofile file on branch master <john Doe>
         * bb407fc - Readme file created on branch master <john Doe>
         ----------------------------------------------------------------------------
         */
        // All commits end on master, and there is a merge commit
        assertEquals(2, commitsOnMasterAfterIntegration);

        // using our little build result validator, see which string the head
        // commit on master contains. Head commit will in this case be the
        // new accumulated commit.
        try (BuildResultValidator buildResultValidator = new BuildResultValidator(builds.getLastBuild(), bareRepository)) {
            buildResultValidator.hasHeadCommitContents(
                    String.format("Squashed commit of the following:"),
                    String.format("commit fa90cb266c4f737a09ad4a3308ec4fb5b898b9d6"),
                    String.format("This is a commit message with double quotes (commit made on Windows), eg. \"test quotes\".")
            ).retain().validate();
        }
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
         *********************************************************************
         */
        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.ACCUMULATED, bareRepository);
        TestUtilsFactory.triggerProject(project);
        jenkinsRule.waitUntilNoActivityUpTo(60000);

        RunList<FreeStyleBuild> builds = project.getBuilds();
        for (FreeStyleBuild b : builds) {
            String console = jenkinsRule.createWebClient().getPage(b, "console").asText();
            System.out.println(console);
        }

        /**
         * ********************************************************************
         * Verify integration in different aspect like commit message content
         * and actual commits
         *********************************************************************
         */
        // Verify number of commits - first count on master after integration
        gitrepo.checkout().setName("master").call();
        gitrepo.checkout().setName("master").setUpstreamMode(SetupUpstreamMode.TRACK).call();
        gitrepo.pull().call();
        int commitsOnMasterAfterIntegration = TestUtilsFactory.countCommitsOnBranch(gitrepo, "master");
        gitrepo.close();

        /*        
         After the accumulated commit of the two commits 7933d8b, 4451bda on
         branch ready/myfeature it will look like this:
         ----------------------------------------------------------------------------
         *   49939e9 - (HEAD, origin/master, master) Accumulated commit of the following from branch 'origin/ready/myfeature': <John Doe>
         |\  
         | * 4451bda - (origin/ready/myfeature, ready/myfeature) Updated infofile file again on branch ready/myfeature <john Doe>
         | * 7933d8b - Updated infofile file on branch ready/myfeature <john Doe>
         |/  
         * 7770f99 - Updated infofile file on branch master <john Doe>
         * bb407fc - Readme file created on branch master <john Doe>
         ----------------------------------------------------------------------------
         */
        // All commits end on master, and there is a merge commit
        assertEquals(4, commitsOnMasterAfterIntegration);

        // using our little build result validator, see which string the head
        // commit on master contains. Head commit will in this case be the
        // new accumulated commit.
        try (BuildResultValidator buildResultValidator = new BuildResultValidator(builds.getLastBuild(), bareRepository)) {
            buildResultValidator.hasHeadCommitContents(
                    String.format("Accumulated commit of the following from branch 'origin/ready/JENKINS-27662_doublequotes':"),
                    String.format("commit fa90cb266c4f737a09ad4a3308ec4fb5b898b9d6"),
                    String.format("This is a commit message with double quotes (commit made on Windows), eg. \"test quotes\".")
            ).retain().validate();
        }
    }
}
