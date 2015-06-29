package org.jenkinsci.plugins.pretestedintegration.integration.scm.git;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.util.RunList;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import junit.framework.TestCase;
import org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Test;

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
public class UseAuthorOfLastCommit extends StaticGitRepositoryTestBase {

    /**
     * Tests that we use the author of the last commit when integrating - using
     * the Squash strategy
     *
     * For more details see class description above
     *
     * @throws Exception
     */
    @Test
    public void authorOfLastCommitUsedIfMoreThanOneCommitSquashStrategy() throws Exception {
        /**
         * ********************************************************************
         * Run test with Jenkins job trying to integrate the feature branch
         * ********************************************************************
         */
        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.SQUASH, bareRepository);
        TestUtilsFactory.triggerProject(project);
        jenkinsRule.waitUntilNoActivityUpTo(120000);

        RunList<FreeStyleBuild> builds = project.getBuilds();
        for (FreeStyleBuild b : builds) {
            String console = jenkinsRule.createWebClient().getPage(b, "console").asXml();
            System.out.println("************************************************************************");
            System.out.println("* Relevant part of Jenkins console (captured with regexp): ");
            // the pattern we want to search for
            Pattern p = Pattern.compile("<link rel=\"stylesheet\" type=\"text/css\" href=\"/jenkins/descriptor/hudson.console.ExpandableDetailsNote/style.css\"/>"
                    + ".*<pre>(.*)</pre>.*</td>.*</tr>.*</tbody>.*</table>", Pattern.DOTALL);
            Matcher m = p.matcher(console);
            // if we find a match, get the group
            if (m.find()) {
                // get the matching group
                String capturedText = m.group(1);

                // print the group
                System.out.format("'%s'\n", capturedText);
            } else {
                System.out.format("Didn't match any relevant part of the console");
            }
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

        RevWalk walk = new RevWalk(gitrepo.getRepository());

        Iterable<RevCommit> logs = gitrepo.log().call();
        Iterator<RevCommit> i = logs.iterator();

        // just look at first commit
        RevCommit commit = walk.parseCommit(i.next());

        System.out.println(String.format("%n******************* Listing and verifying first commit (latest) in repository *******************"));
        System.out.println(String.format("%n***** Commit message (start):*****"));
        System.out.println(String.format("%s", commit.getFullMessage()));
        System.out.println(String.format("***** Commit message (end) *****"));
        System.out.println(String.format("* Author is:      %s", commit.getAuthorIdent().toExternalString()));
        System.out.println(String.format("* Committer is:   %s", commit.getCommitterIdent().toExternalString()));
        TestCase.assertTrue("Author name did not match expected 'Praqma Support Author Two'", commit.getAuthorIdent().getName().equals("Praqma Support Author Two"));
        TestCase.assertTrue("Author email did not match expected 'support@praqma.net'", commit.getAuthorIdent().getEmailAddress().equals("support@praqma.net"));
        TestCase.assertFalse("Committer name was not different from original committer 'Praqma Support Author Two' as we would expect", commit.getCommitterIdent().getName().equals("Praqma Support Author Two"));
        TestCase.assertFalse("Committer email was not different from original committer 'Praqma Support Author Two' as we would expect", commit.getCommitterIdent().getEmailAddress().equals("support@praqma.net"));

        gitrepo.close(); // closing before asserting below
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
    public void authorOfLastCommitUsedIfMoreThanOneCommitAccumulatedStrategy() throws Exception {
        /**
         * ********************************************************************
         * Run test with Jenkins job trying to integrate the feature branch
         * ********************************************************************
         */
        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.ACCUMULATED, bareRepository);
        TestUtilsFactory.triggerProject(project);
        jenkinsRule.waitUntilNoActivityUpTo(120000);

        RunList<FreeStyleBuild> builds = project.getBuilds();
        for (FreeStyleBuild b : builds) {
            String console = jenkinsRule.createWebClient().getPage(b, "console").asXml();
            System.out.println("************************************************************************");
            System.out.println("* Relevant part of Jenkins console (captured with regexp): ");
            // the pattern we want to search for
            Pattern p = Pattern.compile("<link rel=\"stylesheet\" type=\"text/css\" href=\"/jenkins/descriptor/hudson.console.ExpandableDetailsNote/style.css\"/>"
                    + ".*<pre>(.*)</pre>.*</td>.*</tr>.*</tbody>.*</table>", Pattern.DOTALL);
            Matcher m = p.matcher(console);
            // if we find a match, get the group
            if (m.find()) {
                // get the matching group
                String capturedText = m.group(1);

                // print the group
                System.out.format("'%s'\n", capturedText);
            } else {
                System.out.format("Didn't match any relevant part of the console");
            }
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

        RevWalk walk = new RevWalk(gitrepo.getRepository());

        Iterable<RevCommit> logs = gitrepo.log().call();
        Iterator<RevCommit> i = logs.iterator();

        // just look at first commit
        RevCommit commit = walk.parseCommit(i.next());

        System.out.println(String.format("%n******************* Listing and verifying first commit (latest) in repository *******************"));
        System.out.println(String.format("%n***** Commit message (start):*****"));
        System.out.println(String.format("%s", commit.getFullMessage()));
        System.out.println(String.format("***** Commit message (end) *****"));
        System.out.println(String.format("* Author is:      %s", commit.getAuthorIdent().toExternalString()));
        System.out.println(String.format("* Committer is:   %s", commit.getCommitterIdent().toExternalString()));
        TestCase.assertTrue("Author name did not match expected 'Praqma Support Author Two'", commit.getAuthorIdent().getName().equals("Praqma Support Author Two"));
        TestCase.assertTrue("Author email did not match expected 'support@praqma.net'", commit.getAuthorIdent().getEmailAddress().equals("support@praqma.net"));
        TestCase.assertFalse("Committer name was not different from original committer 'Praqma Support Author Two' as we would expect", commit.getCommitterIdent().getName().equals("Praqma Support Author Two"));
        TestCase.assertFalse("Committer email was not different from original committer 'Praqma Support Author Two' as we would expect", commit.getCommitterIdent().getEmailAddress().equals("support@praqma.net"));

        gitrepo.close(); // closing before asserting below
    }
}
