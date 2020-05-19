package org.jenkinsci.plugins.pretestedintegration;

import hudson.DescriptorExtensionList;
import hudson.EnvVars;
import hudson.ExtensionPoint;
import hudson.model.AbstractProject;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Result;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.pretestedintegration.exceptions.UnsupportedConfigurationException;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Abstract class representing an SCM bridge.
 */
public abstract class AbstractSCMBridge implements Describable<AbstractSCMBridge>, ExtensionPoint {
    private static final Logger LOGGER = Logger.getLogger(AbstractSCMBridge.class.getName());


    /**
     * Information about the result of the integration (Unknown, Conflict, Build, Push).
     * @return the integration branch name
     */
    protected abstract String getIntegrationBranch();

    /**
     * The integration strategy.
     * This is the strategy applied to merge pretested commits into the integration integrationBranch.
     */
    public final IntegrationStrategy integrationStrategy;

    protected final static String LOG_PREFIX = "[PREINT] ";

    /**
     * Constructor for the SCM bridge.
     *
     * @param integrationStrategy The integration strategy to apply when merging commits.
     */
    public AbstractSCMBridge(IntegrationStrategy integrationStrategy ) {
        this.integrationStrategy = integrationStrategy;
    }

    /**
     * Validates the configuration of the Jenkins Job.
     * Throws an exception when the configuration is invalid.
     *
     * @param project The Project
     * @throws UnsupportedConfigurationException Mismatch in job configuration
     */
    public void validateConfiguration(AbstractProject<?, ?> project) throws UnsupportedConfigurationException {
    }

        /**
         * @return all the SCM Bridge Descriptors
         */
    public static DescriptorExtensionList<AbstractSCMBridge, SCMBridgeDescriptor<AbstractSCMBridge>> all() {
        return Jenkins.getInstance().<AbstractSCMBridge, SCMBridgeDescriptor<AbstractSCMBridge>>getDescriptorList(AbstractSCMBridge.class);
    }

    /**
     * @return all the Integration Strategy Descriptors
     */
    public static List<IntegrationStrategyDescriptor<?>> getBehaviours() {
        return new ArrayList<>(IntegrationStrategy.all());
    }

    /**
    * {@inheritDoc}
    */
    @Override
    public Descriptor<AbstractSCMBridge> getDescriptor() {
        return (SCMBridgeDescriptor<?>) Jenkins.getInstance().getDescriptorOrDie(getClass());
    }

    /**
     * @return all the SCM Bridge Descriptors
     */
    public static List<SCMBridgeDescriptor<?>> getDescriptors() {
        return new ArrayList<>(all());
    }

    /**
     * @param environment environment
     * @return The Integration Branch name as variable expanded if possible - otherwise return integrationBranch
     */
    public String getExpandedIntegrationBranch(EnvVars environment) {
        return environment.expand(getIntegrationBranch());
    }

    /***
     * @return The required result
     */
    public static Result getRequiredResult() {
        return Result.SUCCESS;
    }
}
