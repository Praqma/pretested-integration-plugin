package org.jenkinsci.plugins.pretestedintegration.scm.git;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.util.Set;

public class GitUtils {

    /**
     * Iterates the list of remotes from the provided repository
     * and removes the first remote from the provided branch name that matches
     *
     * @param repository the repository that will be used to compare the remotes from
     * @param branchName the branch name that needs remote removed from it
     * @return If the provided branch name is "origin/ready/feat_1" the method will
     * match the remote from the repository, remove it and return "ready/feat_1"
     */
    public static String removeRemoteFromBranchName(Repository repository, String branchName) {
        if (repository == null) throw new IllegalArgumentException("[Repository repository] parameter is null");
        if (branchName == null) throw new IllegalArgumentException("[String branchName] parameter is null");

        Config storedConfig = repository.getConfig();
        Set<String> remotes = storedConfig.getSubsections("remote");

        for (String remote : remotes) {
            if (branchName.startsWith(remote))
                branchName = branchName.substring(remote.length()+1, branchName.length());
        }

        return branchName;
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
