package org.jenkinsci.plugins.pretestedintegration.scm.git;

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
     *
     */
    private static final long serialVersionUID = 1L;
    /**
     * The commit Id
     */
    public final ObjectId id;

    /**
     * Constructor for FindCommitAuthorCallback
     * @param id The Commit id of the commit of which to find the author.
     */
    public FindCommitAuthorCallback(final ObjectId id) {
        this.id = id;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String invoke(Repository repository, VirtualChannel channel) throws IOException, InterruptedException {        
        try(RevWalk rw = new RevWalk(repository)) {
            RevCommit commit; = rw.parseCommit(id);
            return commit.getAuthorIdent().toExternalString();
        }
        
    }
}
