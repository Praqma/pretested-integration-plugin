package org.jenkinsci.plugins.pretestedintegration.scm.git;

import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import java.io.IOException;
import java.util.logging.Logger;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

public class FindCommitMessageCallback extends RepositoryListenerAwareCallback<String> {

    private static final Logger logger = Logger.getLogger(FindCommitMessageCallback.class.getName());// Generated code DONT TOUCH! Bookmark: 3ca61d8e671737b5ead8aaccd31875c4
    
    public final ObjectId id;

    public FindCommitMessageCallback(TaskListener listener, final ObjectId id) {
        super(listener);
        this.id = id;
    }

    @Override
    public String invoke(Repository repo, VirtualChannel channel) throws IOException, InterruptedException {
        logger.entering("FindCommitMessageCallback", "invoke", new Object[]{channel, repo});// Generated code DONT TOUCH! Bookmark: 3e9f1bb124a68aa51ae943d0e765a528
        RevWalk walk = new RevWalk(repo);
        RevCommit commit = walk.parseCommit(id);
        walk.dispose();
        logger.exiting("FindCommitMessageCallback", "invoke");// Generated code DONT TOUCH! Bookmark: 2412143d613a394b72a1b1da928ce975
        return commit.getFullMessage();
    }
}
