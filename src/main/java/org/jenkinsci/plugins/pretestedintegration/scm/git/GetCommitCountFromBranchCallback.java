package org.jenkinsci.plugins.pretestedintegration.scm.git;

import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import java.io.IOException;
import java.util.Iterator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

public class GetCommitCountFromBranchCallback extends RepositoryListenerAwareCallback<Integer> {

    public final ObjectId startObjectId;
    public final String targetBranchName;

    public GetCommitCountFromBranchCallback(TaskListener listener, final ObjectId startObjectId, final String targetBranchName) {
        super(listener);
        this.startObjectId = startObjectId;
        this.targetBranchName = targetBranchName;
    }

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
