/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
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

    private static final Logger logger = Logger.getLogger(FindCommitAuthorCallback.class.getName());// Generated code DONT TOUCH! Bookmark: 3ca61d8e671737b5ead8aaccd31875c4

    public final ObjectId id;

    public FindCommitAuthorCallback(TaskListener listener, final ObjectId id) {
        super(listener);
        this.id = id;
    }

    @Override
    public String invoke(Repository repo, VirtualChannel channel) throws IOException, InterruptedException {
        logger.entering("FindCommitAuthorCallback", "invoke", new Object[]{channel, repo});// Generated code DONT TOUCH! Bookmark: 3e9f1bb124a68aa51ae943d0e765a528
        RevWalk walk = new RevWalk(repo);
        RevCommit commit = walk.parseCommit(id);
        walk.dispose();
        logger.exiting("FindCommitAuthorCallback", "end");// Generated code DONT TOUCH! Bookmark: 2412143d613a394b72a1b1da928ce975
        return commit.getAuthorIdent().toExternalString();
    }
}
