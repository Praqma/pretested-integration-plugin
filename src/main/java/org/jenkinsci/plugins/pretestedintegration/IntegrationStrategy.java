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

/**
 * Abstract class representing a strategy to apply when merging pretested commits into the integration branch.
 */
public abstract class IntegrationStrategy implements Describable<IntegrationStrategy>, ExtensionPoint {

    /**
     * Integrates the commits into the integration branch.
     *
     * @param build The Build
     * @param launcher The Launcher
     * @param listener The BuildListener
     * @param bridge The SCM Bridge
     * @throws IntegrationFailedException when the integration failed (merge conflict) when integration fails
     * @throws NothingToDoException when it is an empty merge when there's nothing to do
     * @throws UnsupportedConfigurationException when the configuration is not supported when part of the configuration isn't supported
     */
    public abstract void integrate(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, AbstractSCMBridge bridge) throws IntegrationFailedException, NothingToDoException, UnsupportedConfigurationException;

    /**
    * {@inheritDoc}
    */
    @Override
    public Descriptor<IntegrationStrategy> getDescriptor() {
        return (IntegrationStrategyDescriptor<?>) Jenkins.getInstance().getDescriptorOrDie(getClass());
    }

    /**
     * @return All Integration Strategy descriptors
     */
    public static DescriptorExtensionList<IntegrationStrategy, IntegrationStrategyDescriptor<IntegrationStrategy>> all() {
        return Jenkins.getInstance().<IntegrationStrategy, IntegrationStrategyDescriptor<IntegrationStrategy>>getDescriptorList(IntegrationStrategy.class);
    }
}
