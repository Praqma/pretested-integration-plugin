package org.jenkinsci.plugins.pretestedintegration;

import hudson.DescriptorExtensionList;
import hudson.EnvVars;
import hudson.ExtensionPoint;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Result;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.pretestedintegration.exceptions.CommitFailedException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.BranchDeletionFailedException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.EstablishingWorkspaceFailedException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.IntegrationFailedException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.NothingToDoException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.UnsupportedConfigurationException;

/**
 * Abstract class representing an SCM bridge.
 */
public abstract class AbstractSCMBridge implements Describable<AbstractSCMBridge>, ExtensionPoint {

    /**
     * The integration branch. This is the branch into which pretested commits
     * will be merged.
     */
    protected String branch;

    /**
     * The integration strategy. This is the strategy applied to merge pretested
     * commits into the integration branch.
     */
    public IntegrationStrategy integrationStrategy;

    final static String LOG_PREFIX = "[PREINT] ";

    /**
     * Constructor for the SCM bridge.
     *
     * @param integrationStrategy The integration strategy to apply when merging
     * commits.
     */
    public AbstractSCMBridge(IntegrationStrategy integrationStrategy) {
        this.integrationStrategy = integrationStrategy;
    }

    /**
     * Pushes changes to the integration branch.
     *
     * @param build The Build
     * @param launcher The Launcher
     * @param listener The BuildListener
     * @throws CommitFailedException when the commit fails
     */
    public void commit(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws CommitFailedException {
    }

    /**
     * Deletes the integrated branch.
     *
     * @param build The Build
     * @param launcher The Launcher
     * @param listener The BuildListener
     * @throws BranchDeletionFailedException when branch can not be deleted
     * @throws NothingToDoException when it is an empty merge
     * @throws UnsupportedConfigurationException when the configuration is not
     * supported
     */
    public void deleteIntegratedBranch(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws BranchDeletionFailedException, NothingToDoException, UnsupportedConfigurationException {
    }

    /**
     * Make sure the SCM is checked out on the given branch.
     *
     * @param build The Build
     * @param launcher The Launcher
     * @param listener The BuildListener
     * @param branch The branch to check out
     * @throws EstablishingWorkspaceFailedException when integration branch can
     * not be checked out
     */
    public abstract void ensureBranch(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, String branch) throws EstablishingWorkspaceFailedException;

    /**
     * Called after the build has run. If the build was successful, the changes
     * should be committed, otherwise the workspace is reset.
     *
     * @param build The Build
     * @param launcher The Launcher
     * @param listener The BuildListener
     *
     * @throws IOException IOexception A repository could not be reached.
     */
    public abstract void handlePostBuild(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IOException;

    /**
     * Determines if we should prepare a workspace for integration. If not we
     * throw a NothingToDoException
     *
     * @param build The Build
     * @param listener The BuildListener
     * @throws NothingToDoException when it is an empty merge
     * @throws UnsupportedConfigurationException when the configuration is not
     * supported
     */
    public void isApplicable(AbstractBuild<?, ?> build, BuildListener listener) throws NothingToDoException, UnsupportedConfigurationException {
    }

    /**
     * Integrates the commit into the integration branch. Uses the selected
     * IntegrationStrategy.
     *
     * @param build The Build
     * @param launcher The Launcher
     * @param listener The BuildListener
     * @throws NothingToDoException when it is an empty merge
     * @throws IntegrationFailedException when the integration failed (merge
     * conflict)
     * @throws UnsupportedConfigurationException when the configuration is not
     * supported
     */
    protected void mergeChanges(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws NothingToDoException, IntegrationFailedException, UnsupportedConfigurationException {
        integrationStrategy.integrate(build, launcher, listener, this);
    }

    /**
     * Called after the SCM plugin has updated the workspace with remote
     * changes. Afterwards, the workspace must be ready to perform builds and
     * tests. The integration branch must be checked out, and the given commit
     * must be merged in.
     *
     * @param build The Build
     * @param launcher The Launcher
     * @param listener The BuildListener
     * @throws IntegrationFailedException when the integration failed (merge
     * conflict)
     * @throws EstablishingWorkspaceFailedException when workspace can not be
     * created
     * @throws NothingToDoException when it is an empty merge
     * @throws UnsupportedConfigurationException when the configuration is not
     * supported
     */
    public void prepareWorkspace(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws EstablishingWorkspaceFailedException, NothingToDoException, IntegrationFailedException, UnsupportedConfigurationException {
        mergeChanges(build, launcher, listener);
    }

    /**
     * Updates the description of the Jenkins build.
     *
     * @param build The Build
     * @param launcher The Launcher
     * @param listener The BuildListener
     * @throws NothingToDoException when it is an empty merge
     * @throws UnsupportedConfigurationException when the configuration is not
     * supported
     */
    public void updateBuildDescription(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws NothingToDoException, UnsupportedConfigurationException {
    }

    /**
     * Validates the configuration of the Jenkins Job. Throws an exception when
     * the configuration is invalid.
     *
     * @param project The Project
     * @throws UnsupportedConfigurationException when the configuration is not
     * supported
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
    public String getBranch() {
        return branch;
    }

    /**
     * @param environment The environment to expand the branch in
     * @return The Integration Branch name, expanded using given EnvVars.
     */
    public String getExpandedBranch(EnvVars environment) {
        return environment.expand(branch);
    }

    /**
     * *
     * @return The required result
     */
    public Result getRequiredResult() {
        return Result.SUCCESS;
    }

    public IntegrationStrategy getIntegrationStrategy() {
        return integrationStrategy;
    }

    public void setIntegrationStrategy(IntegrationStrategy integrationStrategy) {
        this.integrationStrategy = integrationStrategy;
    }

}
