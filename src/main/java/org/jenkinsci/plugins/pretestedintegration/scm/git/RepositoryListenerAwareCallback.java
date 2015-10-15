package org.jenkinsci.plugins.pretestedintegration.scm.git;

import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import java.io.IOException;
import org.eclipse.jgit.lib.Repository;
import org.jenkinsci.plugins.gitclient.RepositoryCallback;

public abstract class RepositoryListenerAwareCallback<T> implements RepositoryCallback<T> {

    public final TaskListener listener;

    public RepositoryListenerAwareCallback(TaskListener listener) {
        this.listener = listener;
    }

    @Override
    public abstract T invoke(Repository repo, VirtualChannel channel) throws IOException, InterruptedException;

}
