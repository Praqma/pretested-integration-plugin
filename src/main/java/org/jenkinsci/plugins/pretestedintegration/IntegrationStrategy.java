package org.jenkinsci.plugins.pretestedintegration;

import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.pretestedintegration.exceptions.IntegrationFailedException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.NothingToDoException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.UnsupportedConfigurationException;

public abstract class IntegrationStrategy implements Describable<IntegrationStrategy>, ExtensionPoint {

    public abstract void integrate(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, AbstractSCMBridge bridge) throws IntegrationFailedException, NothingToDoException, UnsupportedConfigurationException;

    @Override
    public Descriptor<IntegrationStrategy> getDescriptor() {
        return (IntegrationStrategyDescriptor<?>) Jenkins.getInstance().getDescriptorOrDie(getClass());
    }

    public static DescriptorExtensionList<IntegrationStrategy, IntegrationStrategyDescriptor<IntegrationStrategy>> all() {
        return Jenkins.getInstance().<IntegrationStrategy, IntegrationStrategyDescriptor<IntegrationStrategy>>getDescriptorList(IntegrationStrategy.class);
    }
}
