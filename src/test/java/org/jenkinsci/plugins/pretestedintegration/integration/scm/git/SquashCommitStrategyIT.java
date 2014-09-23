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
import junit.framework.TestCase;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.jenkinsci.plugins.pretestedintegration.PretestedIntegrationBuildWrapper;
import org.jenkinsci.plugins.pretestedintegration.PretestedIntegrationPostCheckout;
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

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;
import static junit.framework.TestCase.assertEquals;

/**
 * Created by andrius on 9/2/14.
 */
public class SquashCommitStrategyIT {
    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    private final File GIT_DIR = new File("test-repo/.git");
    private final File GIT_PARENT_DIR = GIT_DIR.getParentFile().getAbsoluteFile();
    private final String README_FILE_PATH = GIT_PARENT_DIR.getPath().concat("/" + "readme");

    private final String AUTHER_NAME = "john Doe";
    private final String AUTHER_EMAIL = "Joh@praqma.net";

    private Repository repository;
    private Git git;

    private String readmeFileContents_fromDevBranch;

    @After
    public void tearDown() throws Exception {
        repository.close();
        if (GIT_PARENT_DIR.exists())
            FileUtils.deleteDirectory(GIT_PARENT_DIR);
    }

    public void createValidRepositoryWith2FeatureBranches() throws IOException, GitAPIException {
        if (GIT_PARENT_DIR.exists())
            FileUtils.deleteDirectory(GIT_PARENT_DIR);

        final String FEATURE_BRANCH_1_NAME = "ready/feature_1";
        final String FEATURE_BRANCH_2_NAME = "ready/feature_2";

        FileRepositoryBuilder builder = new FileRepositoryBuilder();

        repository = builder.setGitDir(GIT_DIR.getAbsoluteFile())
                .readEnvironment() // scan environment GIT_* variables
                .findGitDir() // scan up the file system tree
                .build();

        if (!repository.isBare() && repository.getBranch() == null) {
            repository.create();
        }

        git = new Git(repository);

        File readme = new File(README_FILE_PATH);
        if (!readme.exists())
            FileUtils.writeStringToFile(readme, "sample text\n");

        git.add().addFilepattern(readme.getName()).call();
        git.commit().setMessage("commit message 1").call();

        FileUtils.writeStringToFile(readme, "changed sample text\n");

        git.add().addFilepattern(readme.getName()).call();
        git.commit().setMessage("commit message 2").call();

        CreateBranchCommand createBranchCommand = git.branchCreate();
        createBranchCommand.setName(FEATURE_BRANCH_1_NAME);
        createBranchCommand.call();

        git.checkout().setName(FEATURE_BRANCH_1_NAME).call();

        FileUtils.writeStringToFile(readme, "FEATURE_1 branch commit 1\n", true);

        git.add().addFilepattern(readme.getName()).call();
        CommitCommand commitCommand = git.commit();
        commitCommand.setMessage("feature 1 commit 1");
        commitCommand.setAuthor(AUTHER_NAME, AUTHER_EMAIL);
        commitCommand.call();

        FileUtils.writeStringToFile(readme, "FEATURE_1 branch commit 2\n", true);

        git.add().addFilepattern(readme.getName()).call();
        commitCommand = git.commit();
        commitCommand.setMessage("feature 1 commit 2");
        commitCommand.setAuthor(AUTHER_NAME, AUTHER_EMAIL);
        commitCommand.call();

        git.checkout().setName("master").call();

        createBranchCommand = git.branchCreate();
        createBranchCommand.setName(FEATURE_BRANCH_2_NAME);
        createBranchCommand.call();

        git.checkout().setName(FEATURE_BRANCH_2_NAME).call();

        String readmeContents = FileUtils.readFileToString(readme);
        FileUtils.writeStringToFile(readme, "FEATURE_2 branch commit 1\n\n" + readmeContents);

        git.add().addFilepattern(readme.getName()).call();
        commitCommand = git.commit();
        commitCommand.setMessage("feature 1 commit 1");
        commitCommand.setAuthor(AUTHER_NAME, AUTHER_EMAIL);
        commitCommand.call();

        FileUtils.writeStringToFile(readme, "FEATURE_2 branch commit 2\n\n" + readmeContents);

        git.add().addFilepattern(readme.getName()).call();
        commitCommand = git.commit();
        commitCommand.setMessage("feature 2 commit 2");
        commitCommand.setAuthor(AUTHER_NAME, AUTHER_EMAIL);
        commitCommand.call();

        git.checkout().setName("master").call();

        readmeFileContents_fromDevBranch = FileUtils.readFileToString(new File(README_FILE_PATH));
    }

    public void createRepositoryWith2FeatureBranches1Valid1Invalid() throws IOException, GitAPIException {
        if (GIT_PARENT_DIR.exists())
            FileUtils.deleteDirectory(GIT_PARENT_DIR);

        final String FEATURE_BRANCH_1_NAME = "ready/feature_1";
        final String FEATURE_BRANCH_2_NAME = "ready/feature_2";

        FileRepositoryBuilder builder = new FileRepositoryBuilder();

        repository = builder.setGitDir(GIT_DIR.getAbsoluteFile())
                .readEnvironment() // scan environment GIT_* variables
                .findGitDir() // scan up the file system tree
                .build();

        if (!repository.isBare() && repository.getBranch() == null) {
            repository.create();
        }

        git = new Git(repository);

        File readme = new File(README_FILE_PATH);
        if (!readme.exists())
            FileUtils.writeStringToFile(readme, "sample text\n");

        git.add().addFilepattern(readme.getName()).call();
        git.commit().setMessage("commit message 1").call();

        FileUtils.writeStringToFile(readme, "changed sample text\n");

        git.add().addFilepattern(readme.getName()).call();
        git.commit().setMessage("commit message 2").call();

        CreateBranchCommand createBranchCommand = git.branchCreate();
        createBranchCommand.setName(FEATURE_BRANCH_1_NAME);
        createBranchCommand.call();

        git.checkout().setName(FEATURE_BRANCH_1_NAME).call();

        FileUtils.writeStringToFile(readme, "FEATURE_1 branch commit 1\n", true);

        git.add().addFilepattern(readme.getName()).call();
        CommitCommand commitCommand = git.commit();
        commitCommand.setMessage("feature 1 commit 1");
        commitCommand.setAuthor(AUTHER_NAME, AUTHER_EMAIL);
        commitCommand.call();

        FileUtils.writeStringToFile(readme, "FEATURE_1 branch commit 2\n", true);

        git.add().addFilepattern(readme.getName()).call();
        commitCommand = git.commit();
        commitCommand.setMessage("feature 1 commit 2");
        commitCommand.setAuthor(AUTHER_NAME, AUTHER_EMAIL);
        commitCommand.call();

        git.checkout().setName("master").call();

        createBranchCommand = git.branchCreate();
        createBranchCommand.setName(FEATURE_BRANCH_2_NAME);
        createBranchCommand.call();

        git.checkout().setName(FEATURE_BRANCH_2_NAME).call();

        FileUtils.writeStringToFile(readme, "FEATURE_2 branch commit 1\n\n", true);

        git.add().addFilepattern(readme.getName()).call();
        commitCommand = git.commit();
        commitCommand.setMessage("feature 1 commit 1");
        commitCommand.setAuthor(AUTHER_NAME, AUTHER_EMAIL);
        commitCommand.call();

        FileUtils.writeStringToFile(readme, "FEATURE_2 branch commit 2\n\n", true);

        git.add().addFilepattern(readme.getName()).call();
        commitCommand = git.commit();
        commitCommand.setMessage("feature 2 commit 2");
        commitCommand.setAuthor(AUTHER_NAME, AUTHER_EMAIL);
        commitCommand.call();

        git.checkout().setName("master").call();

        readmeFileContents_fromDevBranch = FileUtils.readFileToString(new File(README_FILE_PATH));
    }

    public void createValidRepository() throws IOException, GitAPIException {
        if (GIT_PARENT_DIR.exists())
            FileUtils.deleteDirectory(GIT_PARENT_DIR);

        final String FEATURE_BRANCH_NAME = "ready/feature_1";

        FileRepositoryBuilder builder = new FileRepositoryBuilder();

        repository = builder.setGitDir(GIT_DIR.getAbsoluteFile())
                .readEnvironment() // scan environment GIT_* variables
                .findGitDir() // scan up the file system tree
                .build();

        if (!repository.isBare() && repository.getBranch() == null) {
            repository.create();
        }

        git = new Git(repository);

        File readme = new File(README_FILE_PATH);
        if (!readme.exists())
            FileUtils.writeStringToFile(readme, "sample text\n");

        git.add().addFilepattern(readme.getName()).call();
        git.commit().setMessage("commit message 1").call();

        FileUtils.writeStringToFile(readme, "changed sample text\n");

        git.add().addFilepattern(readme.getName()).call();
        git.commit().setMessage("commit message 2").call();

        CreateBranchCommand createBranchCommand = git.branchCreate();
        createBranchCommand.setName(FEATURE_BRANCH_NAME);
        createBranchCommand.call();

        git.checkout().setName(FEATURE_BRANCH_NAME).call();

        FileUtils.writeStringToFile(readme, "FEATURE_1 branch commit 1\n");

        git.add().addFilepattern(readme.getName()).call();
        CommitCommand commitCommand = git.commit();
        commitCommand.setMessage("feature 1 commit 1");
        commitCommand.setAuthor(AUTHER_NAME, AUTHER_EMAIL);
        commitCommand.call();

        FileUtils.writeStringToFile(readme, "FEATURE_1 branch commit 2\n");

        git.add().addFilepattern(readme.getName()).call();
        commitCommand = git.commit();
        commitCommand.setMessage("feature 1 commit 2");
        commitCommand.setAuthor(AUTHER_NAME, AUTHER_EMAIL);
        commitCommand.call();

        git.checkout().setName("master").call();

        readmeFileContents_fromDevBranch = FileUtils.readFileToString(new File(README_FILE_PATH));
    }

    private void createRepositoryWithMergeConflict() throws IOException, GitAPIException {
        if (GIT_PARENT_DIR.exists())
            FileUtils.deleteDirectory(GIT_PARENT_DIR);

        final String FEATURE_BRANCH_NAME = "ready/feature_1";

        FileRepositoryBuilder builder = new FileRepositoryBuilder();

        repository = builder.setGitDir(GIT_DIR.getAbsoluteFile())
                .readEnvironment() // scan environment GIT_* variables
                .findGitDir() // scan up the file system tree
                .build();

        if (!repository.isBare() && repository.getBranch() == null) {
            repository.create();
        }

        git = new Git(repository);

        File readme = new File(README_FILE_PATH);
        if (!readme.exists())
            FileUtils.writeStringToFile(readme, "sample text\n");

        git.add().addFilepattern(readme.getName()).call();
        git.commit().setMessage("commit message 1").call();

        FileUtils.writeStringToFile(readme, "changed sample text\n");

        git.add().addFilepattern(readme.getName()).call();
        git.commit().setMessage("commit message 2").call();

        CreateBranchCommand createBranchCommand = git.branchCreate();
        createBranchCommand.setName(FEATURE_BRANCH_NAME);
        createBranchCommand.call();

        git.checkout().setName(FEATURE_BRANCH_NAME).call();

        FileUtils.writeStringToFile(readme, "FEATURE_1 branch commit 1\n");

        git.add().addFilepattern(readme.getName()).call();
        CommitCommand commitCommand = git.commit();
        commitCommand.setMessage("feature 1 commit 1");
        commitCommand.setAuthor(AUTHER_NAME, AUTHER_EMAIL);
        commitCommand.call();

        FileUtils.writeStringToFile(readme, "FEATURE_1 branch commit 2\n");

        git.add().addFilepattern(readme.getName()).call();
        commitCommand = git.commit();
        commitCommand.setMessage("feature 1 commit 2");
        commitCommand.setAuthor(AUTHER_NAME, AUTHER_EMAIL);
        commitCommand.call();

        git.checkout().setName("master").call();

        FileUtils.writeStringToFile(readme, "Merge conflict branch commit 2\n");

        git.add().addFilepattern(readme.getName()).call();
        commitCommand = git.commit();
        commitCommand.setMessage("merge conflict message 1");
        commitCommand.setAuthor(AUTHER_NAME, AUTHER_EMAIL);
        commitCommand.call();

        readmeFileContents_fromDevBranch = FileUtils.readFileToString(new File(README_FILE_PATH));
    }

    private FreeStyleProject configurePretestedIntegrationPlugin() throws IOException, ANTLRException, InterruptedException {
        FreeStyleProject project = jenkinsRule.createFreeStyleProject();

        GitBridge gitBridge = new GitBridge(new SquashCommitStrategy(), "master");

        project.getBuildWrappersList().add(new PretestedIntegrationBuildWrapper(gitBridge));
        project.getPublishersList().add(new PretestedIntegrationPostCheckout());

        List<UserRemoteConfig> repoList = new ArrayList<UserRemoteConfig>();
        repoList.add(new UserRemoteConfig("file://" + GIT_DIR.getAbsolutePath(), null, null, null));

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

    private int countCommits() {
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
    public void canSquashMergeAFeatureBranch() throws Exception {
        createValidRepository();

        final int COMMIT_COUNT_BEFORE_EXECUTION = countCommits();

        FreeStyleProject project = configurePretestedIntegrationPlugin();

        jenkinsRule.waitUntilNoActivityUpTo(60000);

        int nextBuildNumber = project.getNextBuildNumber();
        FreeStyleBuild build = project.getBuildByNumber(nextBuildNumber - 1);

        Result result = build.getResult();

        assertTrue(result.isCompleteBuild());
        assertTrue(result.isBetterOrEqualTo(Result.SUCCESS));

        String readmeFileContents = FileUtils.readFileToString(new File(README_FILE_PATH));
        assertEquals(readmeFileContents_fromDevBranch, readmeFileContents);

        final int COMMIT_COUNT_AFTER_EXECUTION = countCommits();

        TestCase.assertTrue(COMMIT_COUNT_AFTER_EXECUTION == COMMIT_COUNT_BEFORE_EXECUTION + 1);
    }

    @Test
    public void ShouldFailWithAMergeConflictPresent() throws Exception {
        createRepositoryWithMergeConflict();

        final int COMMIT_COUNT_BEFORE_EXECUTION = countCommits();

        FreeStyleProject project = configurePretestedIntegrationPlugin();

        jenkinsRule.waitUntilNoActivityUpTo(60000);

        int nextBuildNumber = project.getNextBuildNumber();
        FreeStyleBuild build = project.getBuildByNumber(nextBuildNumber - 1);

        Result result = build.getResult();

        assertTrue(result.isCompleteBuild());
        assertTrue(result.isWorseOrEqualTo(Result.FAILURE));

        final int COMMIT_COUNT_AFTER_EXECUTION = countCommits();

        TestCase.assertTrue(COMMIT_COUNT_AFTER_EXECUTION == COMMIT_COUNT_BEFORE_EXECUTION);
    }

    @Test
    public void squashCommitStrategy_2FeatureBranchesBothValid_2BuildsAreTriggeredBothBranchesGetIntegrated() throws Exception {
        createValidRepositoryWith2FeatureBranches();

        final int COMMIT_COUNT_BEFORE_EXECUTION = countCommits();

        FreeStyleProject project = configurePretestedIntegrationPlugin();

        jenkinsRule.waitUntilNoActivityUpTo(60000);

        RunList<FreeStyleBuild> builds = project.getBuilds();

        assertEquals(2, project.getNextBuildNumber() - 1);

        for (FreeStyleBuild build : builds) {
            Result result = build.getResult();

            assertTrue(result.isCompleteBuild());
            assertTrue(result.isBetterOrEqualTo(Result.SUCCESS));
        }

        final int COMMIT_COUNT_AFTER_EXECUTION = countCommits();

        assertTrue(COMMIT_COUNT_AFTER_EXECUTION == COMMIT_COUNT_BEFORE_EXECUTION + 2);
    }

    @Test
    public void squashCommitStrategy_2FeatureBranches1ValidAnd1Invalid_2BuildsAreTriggeredValidBranchGetsIntegrated() throws Exception {
        createRepositoryWith2FeatureBranches1Valid1Invalid();

        final int COMMIT_COUNT_BEFORE_EXECUTION = countCommits();

        FreeStyleProject project = configurePretestedIntegrationPlugin();

        jenkinsRule.waitUntilNoActivityUpTo(60000);

        RunList<FreeStyleBuild> builds = project.getBuilds();

        assertEquals(2, project.getNextBuildNumber() - 1);

        Result result = builds.getFirstBuild().getResult();

        assertTrue(result.isCompleteBuild());
        assertTrue(result.isBetterOrEqualTo(Result.SUCCESS));

        FreeStyleBuild lastFailedBuild = project.getLastFailedBuild();
        assertNotNull(lastFailedBuild);

        final int COMMIT_COUNT_AFTER_EXECUTION = countCommits();

        assertTrue(COMMIT_COUNT_AFTER_EXECUTION == COMMIT_COUNT_BEFORE_EXECUTION + 1);
    }
}
