package org.jenkinsci.plugins.pretestedintegration.scm.git;

import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import java.io.IOException;
import java.util.logging.Logger;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

public class FindCommitAuthorCallback extends RepositoryListenerAwareCallback<String> {

    public final ObjectId id;

    public FindCommitAuthorCallback(TaskListener listener, final ObjectId id) {
        super(listener);
        this.id = id;
    }

    @Override
    public String invoke(Repository repo, VirtualChannel channel) throws IOException, InterruptedException {
        RevWalk walk = new RevWalk(repo);
        RevCommit commit = walk.parseCommit(id);
        walk.dispose();
        return commit.getAuthorIdent().toExternalString();
    }
}
