package org.jenkinsci.plugins.pretestedintegration.unit;

import org.jenkinsci.plugins.pretestedintegration.AbstractSCMBridge;
import org.jenkinsci.plugins.pretestedintegration.IntegrationStrategy;
import org.jenkinsci.plugins.pretestedintegration.exceptions.EstablishWorkspaceException;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;

public class DummyBridge extends AbstractSCMBridge {

    public DummyBridge(IntegrationStrategy behaves) {
        super(behaves);
    }

    @Override
    public void ensureBranch(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, String branch) throws EstablishWorkspaceException {

    }

}
