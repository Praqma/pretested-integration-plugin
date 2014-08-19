package org.jenkinsci.plugins.pretestedintegration.scm.git;

import org.junit.Assert;
import org.junit.Test;

public class GitUtilsTest {
    @Test
    public void CanRemoveRemoteFromBranchName() {
        final String remoteName = "origin";
        final String expectedResult = "ready/feature1";
        final String inputBranchName = remoteName + "/" + expectedResult;

        String result = GitUtils.removeRemoteFromBranchName(inputBranchName);

        Assert.assertEquals("Remote should be removed from the branch name", expectedResult, result);
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
