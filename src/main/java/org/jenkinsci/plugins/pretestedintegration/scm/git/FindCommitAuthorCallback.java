package org.jenkinsci.plugins.pretestedintegration.scm.git;

import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import java.io.IOException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Callback used to find the author of a Git commit
 */
public class FindCommitAuthorCallback extends RepositoryListenerAwareCallback<String> {

    /**
     * The commit Id
     */
    public final ObjectId id;

    /**
     * Constructor for FindCommitAuthorCallback
     * @param listener The TaskListener
     * @param id The Commit id of the commit of which to find the author.
     */
    public FindCommitAuthorCallback(final ObjectId id) {
//        super(listener);
        this.id = id;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String invoke(Repository repository, VirtualChannel channel) throws IOException, InterruptedException {
        RevWalk walk = new RevWalk(repository);
        RevCommit commit = walk.parseCommit(id);
        walk.dispose();
        return commit.getAuthorIdent().toExternalString();
    }
}
