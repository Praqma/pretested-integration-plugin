package org.jenkinsci.plugins.pretestedintegration.integration.scm.git;

import antlr.ANTLRException;
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.jenkinsci.plugins.pretestedintegration.IntegrationStrategy;
import org.jenkinsci.plugins.pretestedintegration.PretestedIntegrationPostCheckout;
import org.jenkinsci.plugins.pretestedintegration.scm.git.AccumulatedCommitStrategy;
import org.jenkinsci.plugins.pretestedintegration.scm.git.GitBridge;
import org.jenkinsci.plugins.pretestedintegration.scm.git.PretestedIntegrationAsGitPluginExt;
import org.jenkinsci.plugins.pretestedintegration.scm.git.SquashCommitStrategy;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.JenkinsRule;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

/**
 * <h3>Integration test for multiple branch heads on same commit</h3>
 * <p>
 * Test integration of commit with two branch heads:
 * https://trello.com/c/MFzaEMDz</p>
 * <p>
 * This test's purpose is to test the fact that we do not handle the deletion of
 * multiple branch heads pointing to the same commit. We only delete the first
 * one to be discovered.</p>
 */
@Bug(25512)
public class TwoBranchHeadsIT {

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    private Repository repository1;
    private Repository repository2;

    private static final String READY_BRANCH_1 = "ready/alpha-dev";
    private static final String READY_BRANCH_2 = "ready/my-dev";

    @After
    public void tearDown() throws Exception {
        repository1.close();
        repository2.close();

        if (repository1.getDirectory().exists()) {
            FileUtils.deleteDirectory(repository1.getDirectory().getParentFile());
        }
        if (repository2.getDirectory().exists()) {
            FileUtils.deleteDirectory(repository2.getDirectory().getParentFile());
        }
    }

    public void createValidRepositories() throws IOException, GitAPIException {
        File GitRepo1 = new File(String.format("test-repo-%s-1/.git", this.getClass().getName()));
        File GitRepo2 = new File(String.format("test-repo-%s-2/.git", this.getClass().getName()));

        if (GitRepo1.getAbsoluteFile().exists()) {
            FileUtils.deleteDirectory(GitRepo1.getParentFile());
        }
        if (GitRepo2.getAbsoluteFile().exists()) {
            FileUtils.deleteDirectory(GitRepo2.getParentFile());
        }

        FileRepositoryBuilder builder = new FileRepositoryBuilder();

        repository1 = builder.setGitDir(GitRepo1.getAbsoluteFile())
                .readEnvironment() // scan environment GIT_* variables
                .findGitDir() // scan up the file system tree
                .build();

        builder = new FileRepositoryBuilder();

        repository2 = builder.setGitDir(GitRepo2.getAbsoluteFile())
                .readEnvironment() // scan environment GIT_* variables
                .findGitDir() // scan up the file system tree
                .build();

        if (!repository1.isBare() && repository1.getBranch() == null) {
            repository1.create();
        }
        if (!repository2.isBare() && repository2.getBranch() == null) {
            repository2.create();
        }

        Git git1 = new Git(repository1);
        Git git2 = new Git(repository2);

        File testRepo1Readme = new File(repository1.getDirectory().getParent().concat("/" + "readme1"));
        FileUtils.writeStringToFile(testRepo1Readme, "sample text test repo 1\n");

        git1.add().addFilepattern(testRepo1Readme.getName()).call();
        git1.commit().setMessage("commit message 1").call();

        FileUtils.writeStringToFile(testRepo1Readme, "changed sample text repo 1\n", true);

        git1.add().addFilepattern(testRepo1Readme.getName()).call();
        git1.commit().setMessage("commit message 2").call();

        CreateBranchCommand createBranchCommand = git1.branchCreate();
        createBranchCommand.setName(READY_BRANCH_1);
        createBranchCommand.call();

        CheckoutCommand checkout = git1.checkout();
        checkout.setName(READY_BRANCH_1);
        checkout.call();

        FileUtils.writeStringToFile(testRepo1Readme, "some test repo 1 commit 1\n", true);
        git1.add().addFilepattern(testRepo1Readme.getName()).call();
        git1.commit().setMessage(String.format("commit message 1 branch: %s", READY_BRANCH_1)).call();

        FileUtils.writeStringToFile(testRepo1Readme, "some test repo 1 commit 2\n", true);
        git1.add().addFilepattern(testRepo1Readme.getName()).call();
        git1.commit().setMessage(String.format("commit message 2 branch: %s", READY_BRANCH_1)).call();

        createBranchCommand = git1.branchCreate();
        createBranchCommand.setName(READY_BRANCH_2);
        createBranchCommand.call();

        checkout = git1.checkout();
        checkout.setName("master");
        checkout.call();

        File testRepo2Readme = new File(repository2.getDirectory().getParent().concat("/" + "readme2"));
        FileUtils.writeStringToFile(testRepo1Readme, "sample text test repo 2\n");

        git2.add().addFilepattern(testRepo2Readme.getName()).call();
        git2.commit().setMessage("commit message 2").call();

        FileUtils.writeStringToFile(testRepo1Readme, "changed sample text repo 2\n");

        git2.add().addFilepattern(testRepo2Readme.getName()).call();
        git2.commit().setMessage("commit message 2").call();

        createBranchCommand = git2.branchCreate();
        createBranchCommand.setName(READY_BRANCH_2);
        createBranchCommand.call();

        checkout = git2.checkout();
        checkout.setName(READY_BRANCH_2);
        checkout.call();

        FileUtils.writeStringToFile(testRepo2Readme, "some test repo 2 commit 1\n");
        git2.add().addFilepattern(testRepo2Readme.getName()).call();
        git2.commit().setMessage(String.format("commit message 1 branch: %s", READY_BRANCH_2)).call();

        FileUtils.writeStringToFile(testRepo2Readme, "some test repo 2 commit 2\n");
        git2.add().addFilepattern(testRepo2Readme.getName()).call();
        git2.commit().setMessage(String.format("commit message 2 branch: %s", READY_BRANCH_2)).call();

        createBranchCommand = git2.branchCreate();
        createBranchCommand.setName(READY_BRANCH_1);
        createBranchCommand.call();

        checkout = git2.checkout();
        checkout.setName("master");
        checkout.call();

    }

    private FreeStyleProject configurePretestedIntegrationPlugin(IntegrationStrategy integrationStrategy, String repositoryUrl) throws IOException, ANTLRException, InterruptedException {
        FreeStyleProject project = jenkinsRule.createFreeStyleProject();

        List<UserRemoteConfig> repoList = new ArrayList<>();
        repoList.add(new UserRemoteConfig(repositoryUrl, null, null, null));

        List<GitSCMExtension> gitSCMExtensions = new ArrayList<>();
        gitSCMExtensions.add(new PretestedIntegrationAsGitPluginExt(integrationStrategy, "master", "origin"));
        gitSCMExtensions.add(new PruneStaleBranch());
        gitSCMExtensions.add(new CleanCheckout());

        GitSCM gitSCM = new GitSCM(repoList,
                Collections.singletonList(new BranchSpec("origin/ready/**")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null, gitSCMExtensions);

        project.setScm(gitSCM);
        project.getPublishersList().add(new PretestedIntegrationPostCheckout());
        return project;
    }

    @Test
    @Ignore
    public void runSquashCommitStrategyOnRepository1() throws Exception {
        createValidRepositories();

        String repo1Url = "file://" + repository1.getDirectory().getAbsolutePath();
        FreeStyleProject project = configurePretestedIntegrationPlugin(new SquashCommitStrategy(), repo1Url);
        TestUtilsFactory.triggerProject(project);

        jenkinsRule.waitUntilNoActivityUpTo(60000);

        FreeStyleBuild build = project.getBuildByNumber(project.getNextBuildNumber() - 1);

        Result result = build.getResult();

        //Show the log for the latest build
        String text = jenkinsRule.createWebClient().getPage(build, "console").asText();
        System.out.println("=====BUILD-LOG=====");
        System.out.println(text);
        System.out.println("=====BUILD-LOG=====");
        assertTrue(result.isBetterOrEqualTo(Result.SUCCESS));

        assertFalse(TestUtilsFactory.branchExists(repository1, READY_BRANCH_1));
        assertTrue(TestUtilsFactory.branchExists(repository1, READY_BRANCH_2));

    }

    @Test
    @Ignore
    public void runSquashCommitStrategyOnRepository2() throws Exception {
        createValidRepositories();

        String repo2Url = "file://" + repository2.getDirectory().getAbsolutePath();
        FreeStyleProject project = configurePretestedIntegrationPlugin(new SquashCommitStrategy(), repo2Url);
        TestUtilsFactory.triggerProject(project);

        jenkinsRule.waitUntilNoActivityUpTo(60000);

        FreeStyleBuild build = project.getBuildByNumber(project.getNextBuildNumber() - 1);

        Result result = build.getResult();

        //Show the log for the latest build
        String text = jenkinsRule.createWebClient().getPage(build, "console").asText();
        System.out.println("=====BUILD-LOG=====");
        System.out.println(text);
        System.out.println("=====BUILD-LOG=====");
        assertTrue(result.isBetterOrEqualTo(Result.SUCCESS));

        assertFalse(TestUtilsFactory.branchExists(repository2, READY_BRANCH_1));
        assertTrue(TestUtilsFactory.branchExists(repository2, READY_BRANCH_2));
    }

    @Test
    @Ignore
    public void runAccumulatedCommitStrategyOnRepository1() throws Exception {
        createValidRepositories();

        String repo1Url = "file://" + repository1.getDirectory().getAbsolutePath();
        FreeStyleProject project = configurePretestedIntegrationPlugin(new AccumulatedCommitStrategy(), repo1Url);
        TestUtilsFactory.triggerProject(project);

        jenkinsRule.waitUntilNoActivityUpTo(60000);

        FreeStyleBuild build = project.getBuildByNumber(project.getNextBuildNumber() - 1);

        Result result = build.getResult();

        //Show the log for the latest build
        String text = jenkinsRule.createWebClient().getPage(build, "console").asText();
        System.out.println("=====BUILD-LOG=====");
        System.out.println(text);
        System.out.println("=====BUILD-LOG=====");

        assertTrue(result.isBetterOrEqualTo(Result.SUCCESS));

        assertFalse(TestUtilsFactory.branchExists(repository1, READY_BRANCH_1));
        assertTrue(TestUtilsFactory.branchExists(repository1, READY_BRANCH_2));
    }

    @Test
    @Ignore
    public void runAccumulatedCommitStrategyOnRepository2() throws Exception {
        createValidRepositories();

        String repo2Url = "file://" + repository2.getDirectory().getAbsolutePath();
        FreeStyleProject project = configurePretestedIntegrationPlugin(new AccumulatedCommitStrategy(), repo2Url);
        TestUtilsFactory.triggerProject(project);

        jenkinsRule.waitUntilNoActivityUpTo(60000);

        FreeStyleBuild build = project.getBuildByNumber(project.getNextBuildNumber() - 1);

        Result result = build.getResult();

        //Show the log for the latest build
        String text = jenkinsRule.createWebClient().getPage(build, "console").asText();
        System.out.println("=====BUILD-LOG=====");
        System.out.println(text);
        System.out.println("=====BUILD-LOG=====");

        assertTrue(result.isCompleteBuild());
        assertTrue(result.isBetterOrEqualTo(Result.SUCCESS));

        assertFalse(TestUtilsFactory.branchExists(repository2, READY_BRANCH_1));
        assertTrue(TestUtilsFactory.branchExists(repository2, READY_BRANCH_2));
    }
}
