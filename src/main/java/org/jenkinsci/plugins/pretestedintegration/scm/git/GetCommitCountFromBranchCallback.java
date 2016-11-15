package org.jenkinsci.plugins.pretestedintegration.scm.git;

import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import java.io.IOException;
import java.util.Iterator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Callback used to count the commits between a given Commit and a Branch
 */
public class GetCommitCountFromBranchCallback extends RepositoryListenerAwareCallback<Integer> {

    /**
     * The commit Id of the starting point.
     */
    public final ObjectId startObjectId;

    /**
     * The integrationBranch name of the destination.
     */
    public final String targetBranchName;

    /**
     * Constructor for GetCommitCountFromBranchCallback
//     * @param listener The TaskListener
     * @param startObjectId The Id of the starting commit
     * @param targetBranchName The name of the destination integrationBranch
     */
    public GetCommitCountFromBranchCallback(final ObjectId startObjectId, final String targetBranchName) {
//        super(listener);
        this.startObjectId = startObjectId;
        this.targetBranchName = targetBranchName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Integer invoke(Repository repository, VirtualChannel channel) throws IOException, InterruptedException {
        RevWalk walker = new RevWalk(repository);
        RevCommit originCommit = walker.parseCommit(startObjectId);
        ObjectId targetId = repository.resolve(targetBranchName);
        RevCommit targetCommit = walker.parseCommit(targetId);

        walker.markStart(originCommit);
        walker.markUninteresting(targetCommit);

        int commitCount = 0;
        Iterator<RevCommit> iterator = walker.iterator();
        while (iterator.hasNext()) {
            iterator.next();
            commitCount++;
        }
        walker.dispose();

        return commitCount;
    }
}
