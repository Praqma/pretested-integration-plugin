package org.jenkinsci.plugins.pretestedintegration.scm.git;

import org.junit.Assert;
import org.junit.Test;

public class GitUtilsTest {
    @Test
    public void CanRemoveRemoteFromBranchName() {
        final String remoteBranch_1 = "origin/ready/dev_myname";
        final String expectedResult_1 = "ready/dev_myname";

        final String remoteBranch_2 = "origin/ready-devename";
        final String expectedResult_2 = "ready-devename";

        final String remoteBranch_3 = "myremote/feature-branch";
        final String expectedResult_3 = "feature-branch";

        final String result_1 = GitUtils.removeRemoteFromBranchName(remoteBranch_1);
        final String result_2 = GitUtils.removeRemoteFromBranchName(remoteBranch_2);
        final String result_3 = GitUtils.removeRemoteFromBranchName(remoteBranch_3);

        Assert.assertEquals("Remote should be removed from the branch name", expectedResult_1, result_1);
        Assert.assertEquals("Remote should be removed from the branch name", expectedResult_2, result_2);
        Assert.assertEquals("Remote should be removed from the branch name", expectedResult_3, result_3);
    }

    @Test(expected = IllegalArgumentException.class)
    public void ShouldThrowExceptionIfProvidedBranchNameIsAnEmptyString() {
        final String inputBranchName = "";

        GitUtils.removeRemoteFromBranchName(inputBranchName);
    }

    @Test(expected = IllegalArgumentException.class)
    public void ShouldThrowExceptionIfProvidedBranchNameIsNull() {
        final String inputBranchName = null;

        GitUtils.removeRemoteFromBranchName(inputBranchName);
    }
}
