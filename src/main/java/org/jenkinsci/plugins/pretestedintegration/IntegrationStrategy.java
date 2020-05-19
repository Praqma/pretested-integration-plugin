package org.jenkinsci.plugins.pretestedintegration;

import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.*;
import jenkins.model.Jenkins;

/**
 * Abstract class representing a strategy to apply when merging pretested commits into the integration integrationBranch.
 */
public abstract class IntegrationStrategy implements Describable<IntegrationStrategy>, ExtensionPoint {

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
