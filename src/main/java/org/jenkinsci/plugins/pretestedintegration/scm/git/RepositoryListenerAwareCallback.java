package org.jenkinsci.plugins.pretestedintegration.scm.git;

import hudson.remoting.VirtualChannel;
import java.io.IOException;
import org.eclipse.jgit.lib.Repository;
import org.jenkinsci.plugins.gitclient.RepositoryCallback;

/**
 * RepositoryCallback that keeps track of the TaskListener
 * @param <T> Return type of the Callback
 */
public abstract class RepositoryListenerAwareCallback<T> implements RepositoryCallback<T> {

    /**
     * The TaskListener for use in invoke
     */
//    public final TaskListener listener;

    /**
     * Constructor for a RepositoryListenerAwareCallback
     */
    public RepositoryListenerAwareCallback() {}

    /**
     * {@inheritDoc }
     */
    @Override
    public abstract T invoke(Repository repo, VirtualChannel channel) throws IOException, InterruptedException;
}
