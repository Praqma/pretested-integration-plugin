package org.jenkinsci.plugins.pretestedintegration.scm.git;

/**
 * Hold common git error or user messages, used several times in different
 * classes and reusable for testing later.
 */
public class GitMessages {

    /**
     * Message for merge strategies to show when they don't find a match between
     * remote branches and relevant SCM change.
     *
     * @param branchName
     * @return
     */
    public static String noRelevantSCMchange(String branchName) {
        return String.format("There is no relevant SCM change to integrate where integrationBranch matches the 'Integration repository'. Either integrationBranch (%s) is deleted or already integrated, or the SCM change is not related to the integration repository.", branchName);
    }
}
