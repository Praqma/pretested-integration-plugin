/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jenkinsci.plugins.pretestedintegration.scm.git;

import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import java.io.IOException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 *
 * @author Mads
 * 
 * Used in con
 */
public class FindCommitMessageCallback extends RepositoryListenerAwareCallback<String> {
    
    public final ObjectId id;
    
    public FindCommitMessageCallback(TaskListener listener, final ObjectId id) {
        super(listener);
        this.id = id;                
    }

    @Override
    public String invoke(Repository repo, VirtualChannel channel) throws IOException, InterruptedException {        
        RevWalk walk = new RevWalk(repo);
        RevCommit commit = walk.parseCommit(id);
        walk.dispose();
        return commit.getFullMessage();
    }    
}
