package org.jenkinsci.plugins.pretestedintegration.scm.git;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

public class GitUtilsTest {
    final File GIT_DIR = new File("test-repo/.git");
    final File GIT_PARENT_DIR = GIT_DIR.getParentFile().getAbsoluteFile();

    final String FILE_NAME = "readme";
    final String COMMIT_MESSAGE_1 = "commit message 1";
    final String COMMIT_MESSAGE_2 = "commit message 2";

    final String REMOTE_NAME_1 = "myOrigin";
    final String REMOTE_NAME_2 = "my/remote";

    Repository repository;
    RevCommit headCommit = null;

    @Before
    public void setUp() throws IOException {
        FileRepositoryBuilder builder = new FileRepositoryBuilder();

        repository = builder.setGitDir(GIT_DIR.getAbsoluteFile())
                .readEnvironment() // scan environment GIT_* variables
                .findGitDir() // scan up the file system tree
                .build();

        if (!repository.isBare() && repository.getBranch() == null) {
            repository.create();
        }

        StoredConfig config = repository.getConfig();
        config.setString("remote", REMOTE_NAME_1, "url", "https://github.com/centic9/jgit-cookbook.git");
        config.setString("remote", REMOTE_NAME_2, "url", "https://github.com/centic9/jgit-cookbook.git");
        config.save();

        Git git = new Git(repository);

        File readme = new File(GIT_PARENT_DIR.getPath().concat("/" + FILE_NAME));
        if (!readme.exists())
            FileUtils.writeStringToFile(readme, "sample text\n");

        // first commit
        try {
            git.add().addFilepattern(readme.getName()).call();
            git.commit().setMessage(COMMIT_MESSAGE_1).call();
        } catch (GitAPIException e) {
            e.printStackTrace();
        }

        FileUtils.writeStringToFile(readme, "changed sample text\n");

        // second/head commit
        try {
            git.add().addFilepattern(readme.getName()).call();
            headCommit = git.commit().setMessage(COMMIT_MESSAGE_2).call();
        } catch (GitAPIException e) {
            e.printStackTrace();
        }
    }

    @After
    public void tearDown() throws IOException {
        repository.close();
        FileUtils.deleteDirectory(GIT_PARENT_DIR);
    }

    @Test
    public void CanGetCommitMessageUsingSHA1WithAValidSHA1() throws IOException {
        final String EXPECTED_COMMIT_MESSAGE = COMMIT_MESSAGE_2;

        String commitMessage = GitUtils.getCommitMessageUsingSHA1(repository, headCommit.getId());

        Assert.assertEquals(EXPECTED_COMMIT_MESSAGE, commitMessage);
    }

    @Test(expected = IllegalArgumentException.class)
    public void ShouldThrowExceptionWhenGettingCommitMessageWithANullRepositoryAsParameter() {
        Repository nullRepository = null;

        try {
            GitUtils.getCommitMessageUsingSHA1(nullRepository, headCommit.getId());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void ShouldThrowExceptionWhenGettingCommitMessageWithANullSHA1AsParameter() {
       ObjectId SHA = null;

        try {
            GitUtils.getCommitMessageUsingSHA1(repository, SHA);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Test
    public void CanRemoveRemoteFromBranchName() {
        final String remoteBranch_1 = REMOTE_NAME_1 + "/master";
        final String expectedResult_1 = "master";

        final String remoteBranch_2 = REMOTE_NAME_2 + "/master";
        final String expectedResult_2 = "master";

        final String result_1 = GitUtils.removeRemoteFromBranchName(repository, remoteBranch_1);
        final String result_2 = GitUtils.removeRemoteFromBranchName(repository, remoteBranch_2);

        Assert.assertEquals("Remote should be removed from the branch name", expectedResult_1, result_1);
        Assert.assertEquals("Remote should be removed from the branch name", expectedResult_2, result_2);
    }

    @Test
    public void ShouldReturnSameBranchNameIfNoRemoteMatches() {
        final String remoteBranch = "nonExistingRemote/master";
        final String expectedResult = remoteBranch;

        final String result = GitUtils.removeRemoteFromBranchName(repository, remoteBranch);

        Assert.assertEquals(expectedResult, result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void ShouldThrowExceptionIfProvidedRepositoryIsNull() {
        String inputBranchName = "";
        Repository nullRepository = null;

        GitUtils.removeRemoteFromBranchName(nullRepository, inputBranchName);
    }

    @Test(expected = IllegalArgumentException.class)
    public void ShouldThrowExceptionIfProvidedBranchNameIsNull() {
        final String inputBranchName = null;

        GitUtils.removeRemoteFromBranchName(repository, inputBranchName);
    }
}
