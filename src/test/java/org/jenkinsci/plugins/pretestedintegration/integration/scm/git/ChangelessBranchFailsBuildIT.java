package org.jenkinsci.plugins.pretestedintegration.integration.scm.git;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static junit.framework.TestCase.assertTrue;

/**
 * Currently, if you push a ready branch with no changes, the build fails because either it can't find the MERGE_MSG file, or it is empty.
 * Since the exceptions thrown by the API's do a very bad job at actually pointing out what went wrong, we've written these tests
 * to make sure that the user is presented with a text that make sure there is a chance to decipher the error.
 */
public class ChangelessBranchFailsBuildIT {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    private Repository repository;

    private final String res = "Unable to commit changes. There are two known reasons:";

    @After
    public void tearDown() throws Exception{
        TestUtilsFactory.destroyRepo(repository);
    }

    @Test
    public void squash_changelessBranchFailsBuild() throws Exception {
        repository = TestUtilsFactory.createRepoWithoutBranches("squash_changelessBranchFailsBuild");
        Git.open(repository.getDirectory()).branchCreate().setName("ready/no-changes").call();
        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkins, TestUtilsFactory.STRATEGY_TYPE.SQUASH, repository);
        FreeStyleBuild build = project.scheduleBuild2(0).get();
        String console = jenkins.createWebClient().getPage(build, "console").asText();
        System.out.println(console);
        jenkins.assertBuildStatus(Result.FAILURE, build);
        assertTrue(console.contains(res));
    }

    @Test
    public void accumulated_changelessBranchFailsBuild() throws Exception {
        repository = TestUtilsFactory.createRepoWithoutBranches("accumulated_changelessBranchFailsBuild");
        Git.open(repository.getDirectory()).branchCreate().setName("ready/no-changes").call();
        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkins, TestUtilsFactory.STRATEGY_TYPE.ACCUMULATED, repository);
        FreeStyleBuild build = project.scheduleBuild2(0).get();
        String console = jenkins.createWebClient().getPage(build, "console").asText();
        System.out.println(console);
        jenkins.assertBuildStatus(Result.FAILURE, build);
        assertTrue(console.contains(res));
    }
}
