package org.jenkinsci.plugins.pretestedintegration.integration.scm.git;

import antlr.ANTLRException;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.model.queue.QueueTaskFuture;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import static junit.framework.Assert.assertTrue;
import static junit.framework.TestCase.assertEquals;
import static org.jenkinsci.plugins.pretestedintegration.integration.scm.git.FreeStyleProjectFactory.STRATEGY_TYPE;
import org.jenkinsci.plugins.pretestedintegration.exceptions.UnsupportedConfigurationException;

/**
 * Created by andrius on 9/5/14.
 */
public class AccumulatedCommitStrategyIT {
    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    private final File GIT_DIR = new File("test-repo/.git");
    private final File GIT_PARENT_DIR = GIT_DIR.getParentFile().getAbsoluteFile();
    private final String README_FILE_PATH = GIT_PARENT_DIR.getPath().concat("/" + "readme");

    private final String AUTHER_NAME = "john Doe";
    private final String AUTHER_EMAIL = "Joh@praqma.net";

    final String FEATURE_BRANCH_NAME = "ready/feature_1";

    private Repository repository;
    private Git git;

    private String readmeFileContents_fromDevBranch;

    @After
    public void tearDown() throws Exception {
        repository.close();
        if (GIT_PARENT_DIR.exists())
            FileUtils.deleteDirectory(GIT_PARENT_DIR);
    }

    public void createValidRepository() throws IOException, GitAPIException {
        if (GIT_PARENT_DIR.exists())
            FileUtils.deleteDirectory(GIT_PARENT_DIR);

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
    public void canMergeAFeatureBranchUsingAccumulatedStrategy() throws IOException, ANTLRException, InterruptedException, GitAPIException {
        createValidRepository();

        git.checkout().setName(FEATURE_BRANCH_NAME).call();
        final int COMMIT_COUNT_ON_FEATURE_BEFORE_EXECUTION = countCommits();
        git.checkout().setName("master").call();

        FreeStyleProject project = FreeStyleProjectFactory.configurePretestedIntegrationPlugin(jenkinsRule.createFreeStyleProject(), FreeStyleProjectFactory.STRATEGY_TYPE.ACCUMULATED);

        assertEquals(1, jenkinsRule.jenkins.getQueue().getItems().length);

        QueueTaskFuture<Queue.Executable> future = jenkinsRule.jenkins.getQueue().getItems()[0].getFuture();

        do {
            Thread.sleep(1000);
        } while (!future.isDone());

        int nextBuildNumber = project.getNextBuildNumber();
        FreeStyleBuild build = project.getBuildByNumber(nextBuildNumber - 1);

        Result result = build.getResult();

        assertTrue(result.isCompleteBuild());
        assertTrue(result.isBetterOrEqualTo(Result.SUCCESS));

        String readmeFileContents = FileUtils.readFileToString(new File(README_FILE_PATH));
        assertEquals(readmeFileContents_fromDevBranch, readmeFileContents);

        final int COMMIT_COUNT_ON_MASTER_AFTER_EXECUTION = countCommits();
        assertTrue(COMMIT_COUNT_ON_MASTER_AFTER_EXECUTION == COMMIT_COUNT_ON_FEATURE_BEFORE_EXECUTION + 1);
    }

    @Test
    public void ShouldFailWithAMergeConflictPresent() throws Exception {
        createRepositoryWithMergeConflict();

        FreeStyleProject project = FreeStyleProjectFactory.configurePretestedIntegrationPlugin(jenkinsRule.createFreeStyleProject(), STRATEGY_TYPE.ACCUMULATED);

        assertEquals(1, jenkinsRule.jenkins.getQueue().getItems().length);

        QueueTaskFuture<Queue.Executable> future = jenkinsRule.jenkins.getQueue().getItems()[0].getFuture();

        do {
            Thread.sleep(1000);
        } while (!future.isDone());

        int nextBuildNumber = project.getNextBuildNumber();
        FreeStyleBuild build = project.getBuildByNumber(nextBuildNumber - 1);

        Result result = build.getResult();

        assertTrue(result.isCompleteBuild());
        assertTrue(result.isWorseOrEqualTo(Result.FAILURE));
    }
    
    @Test(expected = UnsupportedConfigurationException.class)
    public void failWhenRepNameIsBlankAndGitHasMoreThanOneRepo() throws Exception {
        createValidRepository();
        FreeStyleProject project = FreeStyleProjectFactory.configurePretestedIntegrationPlugin(jenkinsRule.createFreeStyleProject(), STRATEGY_TYPE.ACCUMULATED);
        
    }
            
}
