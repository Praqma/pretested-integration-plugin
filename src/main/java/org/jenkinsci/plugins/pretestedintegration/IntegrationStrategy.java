package org.jenkinsci.plugins.pretestedintegration;

import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.Launcher;
import hudson.model.*;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.pretestedintegration.exceptions.*;

/**
 * Abstract class representing a strategy to apply when merging pretested commits into the integration integrationBranch.
 */
public abstract class IntegrationStrategy implements Describable<IntegrationStrategy>, ExtensionPoint {

    /**
     * Integrates the commits into the integration integrationBranch.
     *
     * @param build The Build
     * @param launcher The Launcher
     * @param listener The BuildListener
     * @param bridge The SCM Bridge
     * @throws IntegrationFailedException When integration fails
     * @throws NothingToDoException The triggered branch/commit is already a part of the integration branch
     * @throws UnsupportedConfigurationException when part of the configuration isn't supported
     * @throws IntegrationUnknownFailureException An unforeseen failure
     */
    public abstract void integrate(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, AbstractSCMBridge bridge) throws IntegrationFailedException, IntegrationUnknownFailureException, NothingToDoException, UnsupportedConfigurationException;

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
