package org.jenkinsci.plugins.pretestedintegration;

import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.Launcher;
import hudson.model.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import jenkins.model.Jenkins;
import org.jenkinsci.plugins.pretestedintegration.exceptions.PushFailedException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.BranchDeletionFailedException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.EstablishingWorkspaceFailedException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.IntegrationFailedException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.NothingToDoException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.UnsupportedConfigurationException;

/**
 * Abstract class representing an SCM bridge.
 */
public abstract class AbstractSCMBridge implements Describable<AbstractSCMBridge>, ExtensionPoint {
    private static final Logger LOGGER = Logger.getLogger(AbstractSCMBridge.class.getName());


    /**
     * Information about the result of the integration (Unknown, Conflict, Build, Push).
     */
    protected String resultInfo = "Unknown";

    /**
     * The integration strategy.
     * This is the strategy applied to merge pretested commits into the integration integrationBranch.
     */
    public final IntegrationStrategy integrationStrategy;

    final static String LOG_PREFIX = "[PREINT] ";

    /**
     * Constructor for the SCM bridge.
     *
     * @param integrationStrategy The integration strategy to apply when merging commits.
     */
    public AbstractSCMBridge(IntegrationStrategy integrationStrategy) {
        this.integrationStrategy = integrationStrategy;
    }

    /**
     * Pushes changes to the integration integrationBranch.
     *
     * @param build    The Build
     * @param listener The BuildListener
     * @throws PushFailedException
     */
    public void pushToIntegrationBranch(AbstractBuild<?, ?> build, BuildListener listener) throws PushFailedException {
    }

    /**
     * Deletes the integrated integrationBranch.
     *
     * @param build    The Build
     * @param launcher The Launcher
     * @param listener The BuildListener
     * @throws BranchDeletionFailedException
     * @throws NothingToDoException
     * @throws UnsupportedConfigurationException
     */
    public void deleteIntegratedBranch(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws BranchDeletionFailedException, NothingToDoException, UnsupportedConfigurationException, IOException, InterruptedException {
    }

    /**
     * Make sure the SCM is checked out on the given integrationBranch.
     *
     * @param build    The Build
     * @param launcher The Launcher
     * @param listener The BuildListener
     * @param branch   The integrationBranch to check out
     * @throws EstablishingWorkspaceFailedException
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
     * @throws NothingToDoException
     * @throws UnsupportedConfigurationException
     */
    public void isApplicable(AbstractBuild<?, ?> build, BuildListener listener) throws NothingToDoException, UnsupportedConfigurationException, IOException, InterruptedException { }

    /**
     * Integrates the commit into the integration integrationBranch.
     * Uses the selected IntegrationStrategy.
     *
     * @param build    The Build
     * @param launcher The Launcher
     * @param listener The BuildListener
     * @throws NothingToDoException
     * @throws IntegrationFailedException
     * @throws UnsupportedConfigurationException
     */
    protected void mergeChanges(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws NothingToDoException, IntegrationFailedException, UnsupportedConfigurationException {
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
     * @throws IntegrationFailedException
     * @throws EstablishingWorkspaceFailedException
     * @throws NothingToDoException
     * @throws UnsupportedConfigurationException
     */
    public void prepareWorkspace(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws EstablishingWorkspaceFailedException, NothingToDoException, IntegrationFailedException, UnsupportedConfigurationException {
        mergeChanges(build, launcher, listener);
    }

    /**
     * Updates the description of the Jenkins build.
     *
     * @param build    The Build
     * @param launcher The Launcher
     * @param listener The BuildListener
     * @throws NothingToDoException
     * @throws UnsupportedConfigurationException
     */
    public void updateBuildDescription(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws NothingToDoException, UnsupportedConfigurationException, IOException, InterruptedException {
    }

    /**
     * Updates the description of the Jenkins build.
     *
     * @param run    The Build
     * @throws NothingToDoException
     * @throws UnsupportedConfigurationException
     */
    public void updateBuildDescription(Run<?, ?> run) throws NothingToDoException, UnsupportedConfigurationException, IOException {
    }

    /**
     *
     * @param tBranch the branch that triggered this build
     * @return a build description
     * @throws NothingToDoException
     * @throws UnsupportedConfigurationException
     * @throws IOException
     * @throws InterruptedException
     */
    public String createBuildDescription(String tBranch) throws NothingToDoException, UnsupportedConfigurationException, IOException, InterruptedException{
       return "";
    }

    /**
     * Validates the configuration of the Jenkins Job.
     * Throws an exception when the configuration is invalid.
     *
     * @param project The Project
     * @throws UnsupportedConfigurationException
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
        return (SCMBridgeDescriptor<?>) Jenkins.getInstance().getDescriptorOrDie(getClass());
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
     * @return The Integration Branch name
     */
/*    public String getIntegrationBranch() {
        return integrationBranch;
    }
    */
    /**
     * @return The information of the result of the Phlow
     */
    public String getResultInfo() {
        return resultInfo;
    }

    /**
     * @return The information of the result of the Phlow
     */
    public void setResultInfo(String resultInfo) {
        this.resultInfo = resultInfo;
    }

    /**
     * @return The Integration Branch name as variable expanded if possible - otherwise return integrationBranch
     */
/*    public String getExpandedIntegrationBranch(EnvVars environment) {
        return  environment.expand(getIntegrationBranch());
    }
*/

    /**
     * @param environment The environment to expand the integrationBranch in
     * @return The Integration Branch name, expanded using given EnvVars.
     */

    /***
     * @return The required result
     */
    public Result getRequiredResult() {
        return Result.SUCCESS;
    }
}
