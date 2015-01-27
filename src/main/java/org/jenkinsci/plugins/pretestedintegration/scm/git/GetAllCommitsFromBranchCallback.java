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
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 *
 * @author andrius
 */
public class GetAllCommitsFromBranchCallback extends RepositoryListenerAwareCallback<String> {
    public final ObjectId id;
    
    public GetAllCommitsFromBranchCallback(TaskListener listener, final ObjectId id) {
        super(listener);
        this.id = id;                
    }

    @Override
    public String invoke(Repository repo, VirtualChannel channel) throws IOException, InterruptedException {        
        StringBuilder sb = new StringBuilder();
        RevWalk walk = new RevWalk(repo);
        
        RevCommit commit = walk.parseCommit(id);

        walk.markStart(commit);
        for (RevCommit rev : walk) {
            sb.append(rev.getName());
            sb.append("\n");
            sb.append(rev.getFullMessage());
            sb.append("\n");
            sb.append("\n");
        }
        
        walk.dispose();
        
        return sb.toString();
    }    
}
