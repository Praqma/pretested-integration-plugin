package org.jenkinsci.plugins.pretestedintegration.integration.scm.git;

import antlr.ANTLRException;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.SubmoduleConfig;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.impl.CleanCheckout;
import hudson.plugins.git.extensions.impl.PruneStaleBranch;
import hudson.triggers.SCMTrigger;
import hudson.util.RunList;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.jenkinsci.plugins.pretestedintegration.IntegrationStrategy;
import org.jenkinsci.plugins.pretestedintegration.PretestedIntegrationBuildWrapper;
import org.jenkinsci.plugins.pretestedintegration.PretestedIntegrationPostCheckout;
import org.jenkinsci.plugins.pretestedintegration.scm.git.AccumulatedCommitStrategy;
import org.jenkinsci.plugins.pretestedintegration.scm.git.GitBridge;
import org.jenkinsci.plugins.pretestedintegration.scm.git.SquashCommitStrategy;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static junit.framework.Assert.assertTrue;

/**
 * Created by andrius on 9/23/14.
 */
public class UsageScenarios {
    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    private Repository repository1;
    private Repository repository2;
    private Git git1;
    private Git git2;

    @After
    public void tearDown() throws Exception {
        repository1.close();
        repository2.close();

        if (repository1.getDirectory().exists())
            FileUtils.deleteDirectory(repository1.getDirectory().getParentFile());
        if (repository2.getDirectory().exists())
            FileUtils.deleteDirectory(repository2.getDirectory().getParentFile());
    }

    public void createValidRepositories() throws IOException, GitAPIException {
        File GitRepo1 = new File("test-repo1/.git");
        File GitRepo2 = new File("test-repo2/.git");

        if (GitRepo1.getAbsoluteFile().exists())
            FileUtils.deleteDirectory(GitRepo1.getParentFile());
        if (GitRepo2.getAbsoluteFile().exists())
            FileUtils.deleteDirectory(GitRepo2.getParentFile());

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

        git1 = new Git(repository1);
        git2 = new Git(repository2);

        File testRepo1Readme = new File(repository1.getDirectory().getParent().concat("/" + "readme1"));
        FileUtils.writeStringToFile(testRepo1Readme, "sample text test repo 1\n");

        git1.add().addFilepattern(testRepo1Readme.getName()).call();
        git1.commit().setMessage("commit message 1").call();

        FileUtils.writeStringToFile(testRepo1Readme, "changed sample text repo 1\n", true);

        git1.add().addFilepattern(testRepo1Readme.getName()).call();
        git1.commit().setMessage("commit message 2").call();

        CreateBranchCommand createBranchCommand = git1.branchCreate();
        createBranchCommand.setName("ready/bue-dev");
        createBranchCommand.call();

        CheckoutCommand checkout = git1.checkout();
        checkout.setName("ready/bue-dev");
        checkout.call();

        FileUtils.writeStringToFile(testRepo1Readme, "some test repo 1 commit 1\n", true);
        git1.add().addFilepattern(testRepo1Readme.getName()).call();
        git1.commit().setMessage("commit message 1 branch: ready/bue-dev").call();

        FileUtils.writeStringToFile(testRepo1Readme, "some test repo 1 commit 2\n", true);
        git1.add().addFilepattern(testRepo1Readme.getName()).call();
        git1.commit().setMessage("commit message 2 branch: ready/bue-dev").call();

        createBranchCommand = git1.branchCreate();
        createBranchCommand.setName("team-frontend/dev");
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
        createBranchCommand.setName("feature/team-dev");
        createBranchCommand.call();

        checkout = git2.checkout();
        checkout.setName("feature/team-dev");
        checkout.call();

        FileUtils.writeStringToFile(testRepo2Readme, "some test repo 2 commit 1\n");
        git2.add().addFilepattern(testRepo2Readme.getName()).call();
        git2.commit().setMessage("commit message 1 branch: feature/team-dev").call();

        FileUtils.writeStringToFile(testRepo2Readme, "some test repo 2 commit 2\n");
        git2.add().addFilepattern(testRepo2Readme.getName()).call();
        git2.commit().setMessage("commit message 2 branch: feature/team-dev").call();

        createBranchCommand = git2.branchCreate();
        createBranchCommand.setName("ready/bue-dev");
        createBranchCommand.call();

        checkout = git2.checkout();
        checkout.setName("master");
        checkout.call();
    }

    private FreeStyleProject configurePretestedIntegrationPlugin(IntegrationStrategy integrationStrategy, String repositoryUrl) throws IOException, ANTLRException, InterruptedException {
        FreeStyleProject project = jenkinsRule.createFreeStyleProject();

        GitBridge gitBridge = new GitBridge(integrationStrategy, "master");

        project.getBuildWrappersList().add(new PretestedIntegrationBuildWrapper(gitBridge));
        project.getPublishersList().add(new PretestedIntegrationPostCheckout());

        List<UserRemoteConfig> repoList = new ArrayList<UserRemoteConfig>();

        repoList.add(new UserRemoteConfig(repositoryUrl, null, null, null));

        List<GitSCMExtension> gitSCMExtensions = new ArrayList<GitSCMExtension>();
        gitSCMExtensions.add(new PruneStaleBranch());
        gitSCMExtensions.add(new CleanCheckout());

        GitSCM gitSCM = new GitSCM(repoList,
                Collections.singletonList(new BranchSpec("origin/ready/**")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null, gitSCMExtensions);

        project.setScm(gitSCM);

        SCMTrigger scmTrigger = new SCMTrigger("@daily", true);
        project.addTrigger(scmTrigger);

        scmTrigger.start(project, true);
        scmTrigger.new Runner().run();

        Thread.sleep(1000);

        return project;
    }

    private int countCommits(Repository repository) {
        Git git = new Git(repository);
        int commitCount = 0;

        try {
            Iterator<RevCommit> iterator = git.log().call().iterator();
            for ( ; iterator.hasNext() ; ++commitCount ) iterator.next();
        } catch (GitAPIException e) {
            e.printStackTrace();
        }

        return commitCount;
    }

    @Test
    public void runSquashCommitStrategyOnRepository1() throws Exception {
        createValidRepositories();

        String repo1Url = "file://" + repository1.getDirectory().getAbsolutePath();
        FreeStyleProject project = configurePretestedIntegrationPlugin(new SquashCommitStrategy(), repo1Url);

        jenkinsRule.waitUntilNoActivityUpTo(60000);

        FreeStyleBuild build = project.getBuildByNumber(project.getNextBuildNumber() -1 );

        System.out.println("------------------------------------");
        System.out.println(FileUtils.readFileToString(build.getLogFile()));
        System.out.println("Commit count: " + countCommits(repository1));
        System.out.println("------------------------------------");

        Result result = build.getResult();
        assertTrue(result.isCompleteBuild());
        assertTrue(result.isBetterOrEqualTo(Result.SUCCESS));
    }

    @Test
    public void runSquashCommitStrategyOnRepository2() throws Exception {
        createValidRepositories();

        String repo2Url = "file://" + repository2.getDirectory().getAbsolutePath();
        FreeStyleProject project = configurePretestedIntegrationPlugin(new SquashCommitStrategy(), repo2Url);

        jenkinsRule.waitUntilNoActivityUpTo(60000);

        FreeStyleBuild build = project.getBuildByNumber(project.getNextBuildNumber() -1 );

        System.out.println("------------------------------------");
        System.out.println(FileUtils.readFileToString(build.getLogFile()));
        System.out.println("Commit count: " + countCommits(repository2));
        System.out.println("------------------------------------");

        Result result = build.getResult();
        assertTrue(result.isCompleteBuild());
        assertTrue(result.isBetterOrEqualTo(Result.SUCCESS));
    }

    @Test
    public void runAccumulatedCommitStrategyOnRepository1() throws Exception {
        createValidRepositories();

        String repo1Url = "file://" + repository1.getDirectory().getAbsolutePath();
        FreeStyleProject project = configurePretestedIntegrationPlugin(new AccumulatedCommitStrategy(), repo1Url);

        jenkinsRule.waitUntilNoActivityUpTo(60000);

        FreeStyleBuild build = project.getBuildByNumber(project.getNextBuildNumber() -1 );

        System.out.println("------------------------------------");
        System.out.println(FileUtils.readFileToString(build.getLogFile()));
        System.out.println("Commit count: " + countCommits(repository1));
        System.out.println("------------------------------------");

        Result result = build.getResult();
        assertTrue(result.isCompleteBuild());
        assertTrue(result.isBetterOrEqualTo(Result.SUCCESS));
    }

    @Test
    public void runAccumulatedCommitStrategyOnRepository2() throws Exception {
        createValidRepositories();

        String repo2Url = "file://" + repository2.getDirectory().getAbsolutePath();
        FreeStyleProject project = configurePretestedIntegrationPlugin(new AccumulatedCommitStrategy(), repo2Url);

        jenkinsRule.waitUntilNoActivityUpTo(60000);

        FreeStyleBuild build = project.getBuildByNumber(project.getNextBuildNumber() -1 );

        System.out.println("------------------------------------");
        System.out.println(FileUtils.readFileToString(build.getLogFile()));
        System.out.println("Commit count: " + countCommits(repository2));
        System.out.println("------------------------------------");

        Result result = build.getResult();
        assertTrue(result.isCompleteBuild());
        assertTrue(result.isBetterOrEqualTo(Result.SUCCESS));
    }
}
