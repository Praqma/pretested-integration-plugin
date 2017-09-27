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
import hudson.plugins.git.util.BuildData;
import hudson.scm.SCM;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import org.eclipse.jgit.lib.Repository;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * <h3>Test that examines the behaviour and the data produced by 'Multiple SCM
 * plugin'</h3>
 * <p>
 * Created this test to check out the behaviour of how the GitSCM interacts with
 * 'Multiple SCM plugin'.</p>
 *
 * <p>
 * We see that every time we create one build we get two build actions. Each
 * pointing to the same revision.</p>
 *
 * <p>
 * This test was created to check the behaviour when running MultiSCM plugin
 * with git.</p>
 *
 */
public class MultipleScmIT {

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @Ignore //We do not support multiSCM in the following versions so we just ignore the test
    @Test
    public void verifyOrderOfBuildDataObjects() throws Exception {
        Repository repo1 = TestUtilsFactory.createRepoWithoutBranches("multi-scm-1");
        Repository repo2 = TestUtilsFactory.createRepoWithoutBranches("multi-scm-2");

        List<GitSCMExtension> gitSCMExtensions = new ArrayList<>();
        gitSCMExtensions.add(new PruneStaleBranch());
        gitSCMExtensions.add(new CleanCheckout());

        SCM gitSCM1 = new GitSCM(Collections.singletonList(new UserRemoteConfig("file://" + repo1.getDirectory().getAbsolutePath(), "repo1", null, null)),
                Collections.singletonList(new BranchSpec("*/ready/**")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null, gitSCMExtensions);

        SCM gitSCM2 = new GitSCM(Collections.singletonList(new UserRemoteConfig("file://" + repo2.getDirectory().getAbsolutePath(), "repo2", null, null)),
                Collections.singletonList(new BranchSpec("*/ready/**")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null, gitSCMExtensions);

        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPluginWithMultiSCM(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.SQUASH, Arrays.asList(gitSCM1, gitSCM2), "repo1");

        new UniqueBranchGenerator(repo1, "repo1-commit1", "repo-1-commit2").usingBranch("ready/feature_1").build();

        TestUtilsFactory.triggerProject(project);

        jenkinsRule.waitUntilNoActivityUpTo(60000);

        FreeStyleBuild build1 = project.getLastBuild();

        assertTrue("We should always get the most recent one here and 2 build data objects", build1.getActions(BuildData.class).size() == 2);

        System.out.println("==build1 actions==");
        for (BuildData action : build1.getActions(BuildData.class)) {
            System.out.println(action.lastBuild.revision.getBranches().iterator().next().getName());
        }

        String consoleB1 = jenkinsRule.createWebClient().getPage(build1, "console").asText();
        System.out.println("===CONSOLE===");
        System.out.println(consoleB1);

        assertTrue("The action we get in this case MUST match", build1.getAction(BuildData.class).lastBuild.revision.getBranches().iterator().next().getName().startsWith("repo1/"));

        new UniqueBranchGenerator(repo2, "repo2-commit1", "repo-2-commit2").usingBranch("ready/feature_2").build();

        TestUtilsFactory.triggerProject(project);

        jenkinsRule.waitUntilNoActivityUpTo(60000);

        FreeStyleBuild build2 = project.getLastBuild();

        System.out.println("==build2 actions==");
        for (BuildData action : build2.getActions(BuildData.class)) {
            System.out.println(action.lastBuild.revision.getBranches().iterator().next().getName());
        }

        String consoleB2 = jenkinsRule.createWebClient().getPage(build2, "console").asText();
        System.out.println("===CONSOLE===");
        System.out.println(consoleB2);

        assertTrue("We should always get the most recent one here and 2 build data objects", build2.getActions(BuildData.class).size() == 2);
        assertTrue("The action we get in this case MUST match", build2.getAction(BuildData.class).lastBuild.revision.getBranches().iterator().next().getName().startsWith("repo2/"));

        /**
         * Now we try something different. Trigger both in same polling
         * interval. We get two new branches.
         *
         */
        new UniqueBranchGenerator(repo1, "repo1-commit11", "repo-1-commit22").usingBranch("ready/feature_2a").build();
        new UniqueBranchGenerator(repo2, "repo2-commit11", "repo-2-commit22").usingBranch("ready/feature_2b").build();

        TestUtilsFactory.triggerProject(project);

        jenkinsRule.waitUntilNoActivityUpTo(60000);

        FreeStyleBuild build3 = project.getLastBuild().getPreviousBuild();
        FreeStyleBuild build4 = project.getLastBuild();

        System.out.println("==build3 actions==");
        for (BuildData action : build3.getActions(BuildData.class)) {
            System.out.println(action.lastBuild.revision.getBranches().iterator().next().getName());
        }

        String consoleB3 = jenkinsRule.createWebClient().getPage(build3, "console").asText();
        System.out.println("===CONSOLE===");
        System.out.println(consoleB3);

        System.out.println("==build4 actions==");
        for (BuildData action : build4.getActions(BuildData.class)) {
            System.out.println(action.lastBuild.revision.getBranches().iterator().next().getName());
        }

        String consoleB4 = jenkinsRule.createWebClient().getPage(build4, "console").asText();
        System.out.println("===CONSOLE===");
        System.out.println(consoleB4);

        boolean assertBuild3 = false;
        boolean assertBuild4 = false;

        for (FreeStyleBuild b : Arrays.asList(build3, build4)) {
            String console = jenkinsRule.createWebClient().getPage(b, "console").asText();
            if (console.contains("push repo1 :ready/feature_2a")) {
                assertBuild3 = true;
                assertEquals("Unexpected build result.", b.getResult(), Result.SUCCESS);
            } else if (console.contains("Nothing to do. The reason is: No revision matches configuration in")) {
                assertBuild4 = true;
                assertEquals("Unexpected build result.", b.getResult(), Result.NOT_BUILT);
            }
        }

        TestUtilsFactory.destroyRepo(repo1, repo2);

        assertTrue(assertBuild3);
        assertTrue(assertBuild4);

    }
}
