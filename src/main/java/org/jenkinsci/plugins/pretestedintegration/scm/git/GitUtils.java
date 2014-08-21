package org.jenkinsci.plugins.pretestedintegration.scm.git;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.jenkinsci.plugins.gitclient.GitClient;

import java.io.IOException;

public class GitUtils {

    /**
     * Removes the first section of the string.
     * This method is meant to remove the remote name from a remote branch.
     *
     * @param branchName the remote branch
     * @return If the provided parameter is "origin/ready/feat_1" the method will output "ready/feat_1"
     */
    public static String removeRemoteFromBranchName(String branchName) {
        if (branchName == null) throw new IllegalArgumentException("String branchName parameter is null");
        if (branchName.isEmpty()) throw new IllegalArgumentException("String branchName parameter is empty");

        String[] split = branchName.split("/");

        String branchNameWithNoRemote = "";
        for (int i = 1; i < split.length; i++) {
            branchNameWithNoRemote += split[i] + "/";
        }
        branchNameWithNoRemote = branchNameWithNoRemote.substring(0, branchNameWithNoRemote.length() - 1);

        return branchNameWithNoRemote;
    }

    /**
     * Gets the commit message using the provided SHA1
     *
     * @param repository repository that will be used to look for the specific commit
     * @param SHA1 this will identify the revision
     * @return Returns the full commit message
     */
    public static String getCommitMessageUsingSHA1(Repository repository, ObjectId SHA1) throws IOException {
        if (repository == null) throw new IllegalArgumentException("[Repository repository] parameter is null");
        if (SHA1 == null) throw new IllegalArgumentException("[ObjectId SHA1] parameter is null");

        RevWalk walk = new RevWalk(repository);
        RevCommit commit = walk.parseCommit(SHA1);

        walk.dispose();

        return commit.getFullMessage();
    }

}
