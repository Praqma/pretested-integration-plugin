package org.jenkinsci.plugins.pretestedintegration;

import hudson.*;
import hudson.model.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import jenkins.model.Jenkins;
import org.jenkinsci.plugins.pretestedintegration.exceptions.*;

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

    public void handleIntegrationExceptions(Run run, TaskListener listener, Exception e) throws IOException, InterruptedException {
        if ( e instanceof NothingToDoException ) {
            run.setResult(Result.NOT_BUILT);
            String logMessage = LOG_PREFIX + String.format("%s - setUp() - NothingToDoException - %s", LOG_PREFIX, e.getMessage());
            listener.getLogger().println(logMessage);
            LOGGER.log(Level.SEVERE, logMessage, e);
            throw new AbortException(e.getMessage());
        }
        if ( e instanceof IntegrationFailedException  ) {
            String logMessage = String.format(
                    "%s - setUp() - %s%n%s",
                    LOG_PREFIX,
                    e.getClass().getSimpleName(),
                    e.getMessage());
            listener.getLogger().println(logMessage);
            LOGGER.log(Level.SEVERE, logMessage, e);
            run.setResult(Result.FAILURE);
            throw new AbortException(e.getMessage());
        }
        if ( e instanceof UnsupportedConfigurationException ||
                e instanceof IntegrationUnknownFailureException ||
                e instanceof EstablishingWorkspaceFailedException ) {
            run.setResult(Result.FAILURE);
            String logMessage = String.format("%s - Unforeseen error preparing preparing for integration. %n%s", LOG_PREFIX, e.getMessage());
            LOGGER.log(Level.SEVERE, logMessage, e);
            listener.getLogger().println(logMessage);
            e.printStackTrace(listener.getLogger());
            throw new AbortException(e.getMessage());
        }

        // Any other exceptions (expected: IOException | InterruptedException)
        run.setResult(Result.FAILURE);
        String logMessage = String.format("%s - Unexpected error. %n%s", LOG_PREFIX, e.getMessage());
        LOGGER.log(Level.SEVERE, logMessage, e);
        listener.getLogger().println(logMessage);
        e.printStackTrace(listener.getLogger());
        throw new AbortException(e.getMessage());
    }


    /**
     * Pushes changes to the integration integrationBranch.
     *
     * @param build    The Build
     * @param listener The BuildListener
     * @throws PushFailedException Used in case of not being able to push
     */
    public void pushToIntegrationBranch(AbstractBuild<?, ?> build, BuildListener listener) throws PushFailedException {
    }

    /**
     * Deletes the integrated integrationBranch.
     *
     * @param build The Abstractbuild
     * @param listener The BuildListener
     * @throws BranchDeletionFailedException Used in case remote branch deletion fails
     * @throws InterruptedException An foreseen issue
     */
    public void deleteIntegratedBranch(AbstractBuild<?, ?> build, TaskListener listener) throws BranchDeletionFailedException, IOException, InterruptedException {
    }

    /**
     * Make sure the SCM is checked out on the given integrationBranch.
     *
     * @param build    The Build
     * @param launcher The Launcher
     * @param listener The BuildListener
     * @param branch   The integrationBranch to check out
     * @throws EstablishingWorkspaceFailedException Used in case the branch cannot be checked out
     */
    public abstract void ensureBranch(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, String branch) throws EstablishingWorkspaceFailedException;

    /**
     * Called after the build has run. If the build was successful, the
     * changes should be committed, otherwise the workspace is reset.
     *
     * @param build    The Build
     * @param launcher The Launcher
     * @param listener The BuildListener
     * @throws IOException A repository could not be reached.
     */
    public abstract void handlePostBuild(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IOException;

    /**
     * Determines if we should prepare a workspace for integration. If not we
     * throw a NothingToDoException
     *
     * @param build    The Build
     * @param listener The BuildListener
     * @throws NothingToDoException Used in no git SCM data found
     * @throws UnsupportedConfigurationException Used in case of ambiguous git data of the remote repo
     * @throws InterruptedException An unforeseen issue
     */
    public void isApplicable(AbstractBuild<?, ?> build, BuildListener listener) throws NothingToDoException, UnsupportedConfigurationException, IOException, InterruptedException { }

    /**
     * Integrates the commit into the integration integrationBranch.
     * Uses the selected IntegrationStrategy.
     *
     * @param build    The Build
     * @param launcher The Launcher
     * @param listener The BuildListener
     * @throws NothingToDoException Used in case the submitted commit is behind the integration
     * @throws IntegrationFailedException Used in case the merge/integration fails or cannot count commits
     * @throws UnsupportedConfigurationException UnsupportedConfigurationException
     * @throws IntegrationUnknownFailureException An unforeseen issue
     */
    protected void mergeChanges(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws NothingToDoException, IntegrationFailedException, IntegrationUnknownFailureException, UnsupportedConfigurationException {
        integrationStrategy.integrate(build, launcher, listener, this);
    }

    /**
     * Called after the SCM plugin has updated the workspace with remote changes.
     * Afterwards, the workspace must be ready to perform builds and tests.
     * The integration integrationBranch must be checked out, and the given commit must be merged in.
     *
     * @param build    The Build
     * @param launcher The Launcher
     * @param listener The BuildListener
     * @throws IntegrationFailedException Used in case the merge/integration fails or cannot count commits
     * @throws EstablishingWorkspaceFailedException EstablishingWorkspaceFailedException
     * @throws NothingToDoException Used in case the submitted commit is behind the integration
     * @throws IntegrationUnknownFailureException An unforeseen issue
     * @throws UnsupportedConfigurationException Mismatch in job configuration
     */
    public void prepareWorkspace(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws EstablishingWorkspaceFailedException, NothingToDoException, IntegrationFailedException, IntegrationUnknownFailureException,UnsupportedConfigurationException {
        mergeChanges(build, launcher, listener);
    }

    /**
     * Updates the description of the Jenkins build.
     *
     * @param build    The Build
     * @param launcher The Launcher
     * @param listener The BuildListener
     * @throws NothingToDoException Used in case the submitted commit is behind the integration
     * @throws UnsupportedConfigurationException Mismatch in job configuration
     * @throws InterruptedException An unforeseen issue
     */
    public void updateBuildDescription(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws NothingToDoException, UnsupportedConfigurationException, IOException, InterruptedException {
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
        return Jenkins.getActiveInstance().<AbstractSCMBridge, SCMBridgeDescriptor<AbstractSCMBridge>>getDescriptorList(AbstractSCMBridge.class);
    }

    /**
     * @return all the Integration Strategy Descriptors
     */
    public static List<IntegrationStrategyDescriptor<?>> getBehaviours() {
        List<IntegrationStrategyDescriptor<?>> behaviours = new ArrayList<>();
        for (IntegrationStrategyDescriptor<?> behaviour : IntegrationStrategy.all()) {
            behaviours.add(behaviour);
        }
        return behaviours;
    }

    /**
    * {@inheritDoc}
    */
    @Override
    public Descriptor<AbstractSCMBridge> getDescriptor() {
        return (SCMBridgeDescriptor<?>) Jenkins.getActiveInstance().getDescriptorOrDie(getClass());
    }

    /**
     * @return all the SCM Bridge Descriptors
     */
    public static List<SCMBridgeDescriptor<?>> getDescriptors() {
        List<SCMBridgeDescriptor<?>> descriptors = new ArrayList<>();
        for (SCMBridgeDescriptor<?> descriptor : all()) {
            descriptors.add(descriptor);
        }
        return descriptors;
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
