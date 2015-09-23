package org.jenkinsci.plugins.pretestedintegration.integration.scm.git;

import hudson.model.TaskListener;
import java.io.File;
import static junit.framework.TestCase.assertEquals;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.jenkinsci.plugins.pretestedintegration.scm.git.GetCommitCountFromBranchCallback;
import org.junit.After;
import org.junit.Test;

public class GetCommitCountFromBranchCallbackIT {

    private final String FOLDER_PREFIX = "CommitCount_";
    private File dir;
    
    @After
    public void tearDown() throws Exception {
        TestUtilsFactory.destroyDirectory(dir);
    }

    @Test
    public void counts_two_commits() throws Exception {
        dir = new File(FOLDER_PREFIX + "count2");
        Git git = Git.init().setDirectory(dir).call();
        File testFile = new File(dir + "/file");

        // First commit to master
        FileUtils.writeStringToFile(testFile, "master commit 1");
        git.add().addFilepattern("file").call();
        git.commit().setMessage("master commit 1").call();

        // Create a branch
        git.checkout().setCreateBranch(true).setName("branch").call();
        // First commit to branch
        FileUtils.writeStringToFile(testFile, "branch commit 1", true);
        git.add().addFilepattern("file").call();
        git.commit().setMessage("branch commit 1").call();
        // Second commit to branch
        FileUtils.writeStringToFile(testFile, "branch commit 2", true);
        git.add().addFilepattern("file").call();
        ObjectId startCommit = git.commit().setMessage("branch commit 2").call();

        // Counts two commits
        GetCommitCountFromBranchCallback callback = new GetCommitCountFromBranchCallback(TaskListener.NULL, startCommit, "master");
        assertEquals("Commit count did not match expectations.", new Integer(2), callback.invoke(git.getRepository(), null));

        // Second commit to master
        FileUtils.writeStringToFile(testFile, "master commit 2");
        git.add().addFilepattern("file").call();
        git.commit().setMessage("master commit 2").call();
        
        // STILL counts two commits
        callback = new GetCommitCountFromBranchCallback(TaskListener.NULL, startCommit, "master");
        assertEquals("Commit count did not match expectations.", new Integer(2), callback.invoke(git.getRepository(), null));
    }
}
