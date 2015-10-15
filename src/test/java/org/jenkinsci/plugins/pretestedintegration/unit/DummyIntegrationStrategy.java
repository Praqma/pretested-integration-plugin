package org.jenkinsci.plugins.pretestedintegration.unit;

import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import org.jenkinsci.plugins.pretestedintegration.AbstractSCMBridge;
import org.jenkinsci.plugins.pretestedintegration.exceptions.IntegationFailedExeception;
import org.jenkinsci.plugins.pretestedintegration.exceptions.NothingToDoException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.UnsupportedConfigurationException;
import org.jenkinsci.plugins.pretestedintegration.IntegrationStrategy;

public class DummyIntegrationStrategy extends IntegrationStrategy {

    @Override
    public void integrate(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, AbstractSCMBridge bridge) throws IntegationFailedExeception, NothingToDoException, UnsupportedConfigurationException {

    }

}
