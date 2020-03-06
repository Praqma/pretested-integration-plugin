package org.jenkinsci.plugins.pretestedintegration.scm.git;

import hudson.remoting.VirtualChannel;
import java.io.IOException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Callback to find the message of a given Git commit
 */
public class FindCommitMessageCallback extends RepositoryListenerAwareCallback<String> {

    /**
     *
     */
    private static final long serialVersionUID = 1504512748198377240L;
    /**
     * The commit Id
     */
    public final ObjectId id;

    /**
     * Constructor for FindCommitMessageCallback
     * @param id The Commit id of the commit of which to find the author.
     */
    public FindCommitMessageCallback(final ObjectId id) {
        this.id = id;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String invoke(Repository repo, VirtualChannel channel) throws IOException, InterruptedException {
        try(RevWalk walk = new RevWalk(repo)) {
            RevCommit commit = walk.parseCommit(id);
            return commit.getFullMessage();
        }
    }
}
