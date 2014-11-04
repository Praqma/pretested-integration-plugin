/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jenkinsci.plugins.pretestedintegration.scm.git;

import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import java.io.IOException;
import org.eclipse.jgit.lib.Repository;
import org.jenkinsci.plugins.gitclient.RepositoryCallback;

/**
 *
 * @author Mads
 */
public abstract class RepositoryListenerAwareCallback<T> implements RepositoryCallback<T> {
    
    private static final Logger logger = Logger
			.getLogger(RepositoryListenerAwareCallback.class.getName());// Generated code DONT TOUCH! Bookmark: 3ca61d8e671737b5ead8aaccd31875c4

	public final TaskListener listener;
    
    public RepositoryListenerAwareCallback(TaskListener listener) {
        this.listener = listener;
    }

    public abstract T invoke(Repository repo, VirtualChannel channel) throws IOException, InterruptedException;
    
}
