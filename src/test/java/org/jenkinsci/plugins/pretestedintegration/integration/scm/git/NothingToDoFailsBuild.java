package org.jenkinsci.plugins.pretestedintegration.integration.scm.git;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import org.eclipse.jgit.lib.Repository;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static junit.framework.Assert.assertTrue;

/**
 * Arguably when there's NothingToDo the build result should be 'NOT_BUILT'.
 * After an internal discussion we decided that we'd stick to 'FAILED' since:
 * A) If there's nothing to do, why was the job triggered? Something might've gone wrong.
 * B) We eventually want to get rid of the NothingToDo path or at least rewrite how it's handled.
 */
public class NothingToDoFailsBuild {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    private Repository repository;

    @After
    public void tearDown() throws Exception{
        TestUtilsFactory.destroyRepo(repository);
    }

    @Test
    public void squash_nothingToDoFailsBuild() throws Exception {
        repository = TestUtilsFactory.createRepoWithoutBranches("squash_nothingToDoFailsBuild");
        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkins, TestUtilsFactory.STRATEGY_TYPE.SQUASH, repository);
        FreeStyleBuild build = project.scheduleBuild2(0).get();
        String console = jenkins.createWebClient().getPage(build, "console").asText();
        System.out.println(console);
        jenkins.assertBuildStatus(Result.FAILURE, build);
        assertTrue(console.contains("Nothing to do"));
    }

    @Test
    public void accumulated_nothingToDoFailsBuild() throws Exception {
        repository = TestUtilsFactory.createRepoWithoutBranches("accumulated_nothingToDoFailsBuild");
        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkins, TestUtilsFactory.STRATEGY_TYPE.SQUASH, repository);
        FreeStyleBuild build = project.scheduleBuild2(0).get();
        String console = jenkins.createWebClient().getPage(build, "console").asText();
        System.out.println(console);
        jenkins.assertBuildStatus(Result.FAILURE, build);
        assertTrue(console.contains("Nothing to do"));
    }
}
