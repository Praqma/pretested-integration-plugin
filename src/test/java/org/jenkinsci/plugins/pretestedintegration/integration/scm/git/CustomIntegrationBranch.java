package org.jenkinsci.plugins.pretestedintegration.integration.scm.git;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.plugins.git.UserRemoteConfig;
import hudson.util.RunList;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static junit.framework.Assert.assertTrue;
import junit.framework.TestCase;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNotNull;
import org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.FetchResult;
import org.junit.Test;
import org.xml.sax.SAXException;

/**
 * Tests that we use the author of the last commit when integrating
 *
 * Repository we use for testing can be described as below. It have one initial
 * commit, and then two commits with two different authors on a branch. When the
 * branch is integrated we expect the author of the last commit to be author of
 * the integration commit. We also expect that the committer changes (it will be
 * the one running the test suites), so checking it is just changed. * a1b88be -
 * (HEAD, origin/ready/twoCommitsBranch, twoCommitsBranch) Added test commit log
 * file - second commit by 'Praqma Support Author Two' (0 seconds ago) <Praqma
 * Support Author Two>
 * * 0174ae0 - Added test commit log file - first commit by 'Praqma Support
 * Author One' (0 seconds ago) <Praqma Support Author One>
 * * d7743e8 - (origin/master, master) Initial commit - added README (0 seconds
 * ago) <Praqma Support>
 *
 */
public class CustomIntegrationBranch extends StaticGitRepositoryTestBase {

    
    /**
     * Tests that we use the author of the last commit when integrating - using
     * the Squash strategy
     *
     * For more details see class description above
     *
     * @throws Exception
     */
    @Test
    public void customIntegrationBranchSquashStrategy() throws Exception {
        /**
         * ********************************************************************
         * Run test with Jenkins job trying to integrate the feature branch
         * ********************************************************************
         */
        String integrationBranch = "customIntegrationBranch";
        //List<Ref> branchList = gitrepo.branchList().setListMode(ListBranchCommand.ListMode.ALL).call();
        //System.out.println(String.format("%n################# Branch lists ############################%n%s", branchList));

        
        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.SQUASH, bareRepository, true, integrationBranch);
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
        // Verification are done from the static git repository created for
        // this test. We know exactly what it contains.
        // - Custom integration branch is target for the delivery
        // - The content of the files are as expected
        // - Commit message is as expected
        

        // Checking out the resulting branch we integrated to.
        // This checkout will give a detached head, but it doesn't matter as we
        // just need to verify content of commit and repository
        // For some reason, might not understand JGit completely, we could 
        // checkout "master" but not "customIntegrationBranch", 
        // but "origin/customIntegrationBranch" works though.
        gitrepo.fetch().call();
        gitrepo.checkout().setName("origin/customIntegrationBranch").call();
       
        
        // This will "walk" over the log, but we only take the first entry
        // which will be the integration commit
        RevWalk walk = new RevWalk(gitrepo.getRepository());
        Iterable<RevCommit> log = gitrepo.log().call();
        Iterator<RevCommit> i = log.iterator();
        RevCommit commit = walk.parseCommit(i.next());

        /*
        --- git log graph: ---
        * c1449e0 - Wed, 3 Jun 205 14:03:46 +0200 (1 second ago) (HEAD, origin/ready/myDevelopmentBranch, myDevelopmentBranch)
        |  Added a second line from myDevelopmentBranch in test commit log file. - Praqma Support
        * 70353ce - Wed, 3 Jun 2015 14:03:45 +0200 (2 seconds ago)
        |  Added line from myDevelopmentBranch in test commit log file. - Praqma Support
        * c1b3225 - Wed, 3 Jun 2015 14:03:42 +0200 (5 seconds ago) (origin/customIntegrationBranch, customIntegrationBranch)
        |  Updated readme file on new custom integration branch - Praqma Support
        | * 31bc93a - Wed, 3 Jun 2015 14:03:44 +0200 (3 seconds ago) (origin/master, master)
        |/   Last line in readme, added from last commit on master. We integrate to another branch from here on. - Praqma Support
        * 2086dc9 - Wed, 3 Jun 2015 14:03:41 +0200 (6 seconds ago)
        |  Second commit on on master branch - updated README - Praqma Support
        * 8ae8f12 - Wed, 3 Jun 2015 14:03:40 +0200 (7 seconds ago)
           Initial commit on master branch - added README - Praqma Support

        --- git log: ---
        commit c1449e075f528974c63eef81109d0632eaada0c7
        Author: Praqma Support <support@praqma.net>
        Date:   Wed Jun 3 14:03:46 2015 +0200

            Added a second line from myDevelopmentBranch in test commit log file.

        commit 70353ce6771866f29c38b4460b3f74f9024f8ce2
        Author: Praqma Support <support@praqma.net>
        Date:   Wed Jun 3 14:03:45 2015 +0200

            Added line from myDevelopmentBranch in test commit log file.
        */
        String commitFullMessage = commit.getFullMessage();
        System.out.println("************************************************************************");
        System.out.println("* CustomIntegrationBranch.customIntegrationBranchAccumulatedStrategy test");
        System.out.println("* Integration commit message:");
        System.out.println(commitFullMessage);
        System.out.println("************************************************************************");
        assertTrue("The last commit on integration branch was not an accumulated commit. Didn't find the text 'Squashed commit'", commitFullMessage.contains("Squashed commit"));
        assertTrue("The squashed commit message, doesn't contain the SHA from the git log from the first of the included commits", commitFullMessage.contains("commit c1449e075f528974c63eef81109d0632eaada0c7"));
        assertTrue("The squashed commit message, didn't contain expected date string from the git log from the first of the included commits", commitFullMessage.contains("Wed Jun 3 14:03:46 2015 +0200"));
        assertTrue("The squashed commit message, didn't contain part of the original commit messages.", commitFullMessage.contains("Added a second line from myDevelopmentBranch in test commit log file."));
        assertTrue("The squashed commit message, doesn't contain the SHA from the git log from the second of the included commits", commitFullMessage.contains("commit 70353ce6771866f29c38b4460b3f74f9024f8ce2"));
        assertTrue("The squashed commit message, didn't contain expected date string from the git log from the seond of the included commits", commitFullMessage.contains("Wed Jun 3 14:03:46 2015 +0200"));
        assertTrue("The squashed commit message, didn't contain part of the original commit messages.", commitFullMessage.contains("Added a second line from myDevelopmentBranch in test commit log file."));

        // Verify that the collection and gathering of accumulated commit message doesn't collect much information
        // like commits from other branches:
        assertFalse("The squashed commit message, contain commit message parts from wrong commit (first commit on the integration branch)", commitFullMessage.contains("Updated readme file on new custom integration branch"));
        assertFalse("The squashed commit message, contain commit message parts from wrong commit (newest commit on master branch)", commitFullMessage.contains("Last line in readme, added from last commit on master. We integrate to another branch from here on."));
        assertFalse("The squashed commit message, contain commit message parts from wrong commit (second commit on master branch)", commitFullMessage.contains("Second commit on on master branch - updated README"));
        
        File testCommitFile = new File(gitrepo.getRepository().getWorkTree(),"testCommit.log");
        File readmeFile = new File(gitrepo.getRepository().getWorkTree(),"README.md");
        assertTrue("The test commit file on the integration branch didn't contain the expected line - did integration go well?", TestUtilsFactory.checkForLineInFile(testCommitFile, "Used for adding lines to easily commit something during tests.\\n"));
        assertTrue("The readme file on the integration branch, didn't contain the lines added on the first commit before integrating development branch - what wen't wrong?", TestUtilsFactory.checkForLineInFile(readmeFile, "Added a custom integration branch based on master branch"));
        assertFalse("The readme file on the integration branch contain lines from the master branch - that is wrong", TestUtilsFactory.checkForLineInFile(readmeFile, "Last line in readme file"));
    }

    /**
     * Tests that we use the author of the last commit when integrating - using
     * the Accumulated strategy
     *
     * For more details see class description above
     *
     * @throws Exception
     */
    @Test
    public void customIntegrationBranchAccumulatedStrategy() throws Exception {
        /**
         * ********************************************************************
         * Run test with Jenkins job trying to integrate the feature branch
         * ********************************************************************
         */
        String integrationBranch = "customIntegrationBranch";
        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.ACCUMULATED, bareRepository, true, integrationBranch);
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
         * and actual commits.
         * Verification is done in a clone of the bare repository in which
         * the Jenkins job delivers changes to.
         * 'gitrepo' object is created by the setUp method, but need to be 
         * updated with latest changes from the bare repository.
         * ********************************************************************
         */
        // Verification are done from the static git repository created for
        // this test. We know exactly what it contains.
        // - Custom integration branch is target for the delivery
        // - The content of the files are as expected
        // - Commit message is as expected
        

        // Checking out the resulting branch we integrated to.
        // This checkout will give a detached head, but it doesn't matter as we
        // just need to verify content of commit and repository
        // For some reason, might not understand JGit completely, we could 
        // checkout "master" but not "customIntegrationBranch", 
        // but "origin/customIntegrationBranch" works though.
        gitrepo.fetch().call();
        gitrepo.checkout().setName("origin/customIntegrationBranch").call();
       
        
        // This will "walk" over the log, but we only take the first entry
        // which will be the integration commit
        RevWalk walk = new RevWalk(gitrepo.getRepository());
        Iterable<RevCommit> log = gitrepo.log().call();
        Iterator<RevCommit> i = log.iterator();
        RevCommit commit = walk.parseCommit(i.next());

        /*
        --- git log graph: ---
        * c1449e0 - Wed, 3 Jun 205 14:03:46 +0200 (1 second ago) (HEAD, origin/ready/myDevelopmentBranch, myDevelopmentBranch)
        |  Added a second line from myDevelopmentBranch in test commit log file. - Praqma Support
        * 70353ce - Wed, 3 Jun 2015 14:03:45 +0200 (2 seconds ago)
        |  Added line from myDevelopmentBranch in test commit log file. - Praqma Support
        * c1b3225 - Wed, 3 Jun 2015 14:03:42 +0200 (5 seconds ago) (origin/customIntegrationBranch, customIntegrationBranch)
        |  Updated readme file on new custom integration branch - Praqma Support
        | * 31bc93a - Wed, 3 Jun 2015 14:03:44 +0200 (3 seconds ago) (origin/master, master)
        |/   Last line in readme, added from last commit on master. We integrate to another branch from here on. - Praqma Support
        * 2086dc9 - Wed, 3 Jun 2015 14:03:41 +0200 (6 seconds ago)
        |  Second commit on on master branch - updated README - Praqma Support
        * 8ae8f12 - Wed, 3 Jun 2015 14:03:40 +0200 (7 seconds ago)
           Initial commit on master branch - added README - Praqma Support

        --- git log: ---
        commit c1449e075f528974c63eef81109d0632eaada0c7
        Author: Praqma Support <support@praqma.net>
        Date:   Wed Jun 3 14:03:46 2015 +0200

            Added a second line from myDevelopmentBranch in test commit log file.

        commit 70353ce6771866f29c38b4460b3f74f9024f8ce2
        Author: Praqma Support <support@praqma.net>
        Date:   Wed Jun 3 14:03:45 2015 +0200

            Added line from myDevelopmentBranch in test commit log file.
        */
        String commitFullMessage = commit.getFullMessage();
        System.out.println("************************************************************************");
        System.out.println("* CustomIntegrationBranch.customIntegrationBranchAccumulatedStrategy test");
        System.out.println("* Integration commit message:");
        System.out.println(commitFullMessage);
        System.out.println("************************************************************************");
        assertTrue("The last commit on integration branch was not an accumulated commit. Didn't find the text 'Accumulated commit'", commitFullMessage.contains("Accumulated commit"));
        assertTrue("The accumulated commit message, doesn't contain the SHA from the git log from the first of the included commits", commitFullMessage.contains("commit c1449e075f528974c63eef81109d0632eaada0c7"));
        assertTrue("The accumulated commit message, didn't contain expected date string from the git log from the first of the included commits", commitFullMessage.contains("Wed Jun 3 14:03:46 2015 +0200"));
        assertTrue("The squashed commit message, didn't contain part of the original commit messages.", commitFullMessage.contains("Added a second line from myDevelopmentBranch in test commit log file."));
        assertTrue("The accumulated commit message, doesn't contain the SHA from the git log from the second of the included commits", commitFullMessage.contains("commit 70353ce6771866f29c38b4460b3f74f9024f8ce2"));
        assertTrue("The accumulated commit message, didn't contain expected date string from the git log from the seond of the included commits", commitFullMessage.contains("Wed Jun 3 14:03:46 2015 +0200"));
        assertTrue("The squashed commit message, didn't contain part of the original commit messages.", commitFullMessage.contains("Added a second line from myDevelopmentBranch in test commit log file."));

        // Verify that the collection and gathering of accumulated commit message doesn't collect much information
        // like commits from other branches:
        assertFalse("The accumulated commit message, contain commit message parts from wrong commit (first commit on the integration branch)", commitFullMessage.contains("Updated readme file on new custom integration branch"));
        assertFalse("The accumulated commit message, contain commit message parts from wrong commit (newest commit on master branch)", commitFullMessage.contains("Last line in readme, added from last commit on master. We integrate to another branch from here on."));
        assertFalse("The accumulated commit message, contain commit message parts from wrong commit (second commit on master branch)", commitFullMessage.contains("Second commit on on master branch - updated README"));
        
        File testCommitFile = new File(gitrepo.getRepository().getWorkTree(),"testCommit.log");
        File readmeFile = new File(gitrepo.getRepository().getWorkTree(),"README.md");
        assertTrue("The test commit file on the integration branch didn't contain the expected line - did integration go well?", TestUtilsFactory.checkForLineInFile(testCommitFile, "Used for adding lines to easily commit something during tests.\\n"));
        assertTrue("The readme file on the integration branch, didn't contain the lines added on the first commit before integrating development branch - what wen't wrong?", TestUtilsFactory.checkForLineInFile(readmeFile, "Added a custom integration branch based on master branch"));
        assertFalse("The readme file on the integration branch contain lines from the master branch - that is wrong", TestUtilsFactory.checkForLineInFile(readmeFile, "Last line in readme file"));
    }
}
