package org.jenkinsci.plugins.pretestedintegration.unit;

import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import java.io.IOException;
import org.jenkinsci.plugins.pretestedintegration.AbstractSCMBridge;
import org.jenkinsci.plugins.pretestedintegration.exceptions.EstablishingWorkspaceFailedException;
import org.jenkinsci.plugins.pretestedintegration.IntegrationStrategy;

public class DummyBridge extends AbstractSCMBridge {

    public DummyBridge(IntegrationStrategy behaves) {
        super(behaves,false);
    }

    @Override
    public void ensureBranch(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, String branch) throws EstablishingWorkspaceFailedException {

    }

    @Override
    public void handlePostBuild(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IOException {

    }

    @Override
    protected String getIntegrationBranch() {
        return "master";
    }

}
