package org.jenkinsci.plugins.pretestedintegration;

import hudson.*;
import hudson.model.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import jenkins.model.Jenkins;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.pretestedintegration.exceptions.*;

/**
 * Abstract class representing an SCM bridge.
 */
public abstract class AbstractSCMBridge implements Describable<AbstractSCMBridge>, ExtensionPoint {
    private static final Logger LOGGER = Logger.getLogger(AbstractSCMBridge.class.getName());


    /**
     * Information about the result of the integration (Unknown, Conflict, Build, Push).
     */
//    protected String resultInfo = "Unknown";
    protected abstract String getIntegrationBranch();

    /**
     * The integration strategy.
     * This is the strategy applied to merge pretested commits into the integration integrationBranch.
     */
    public final IntegrationStrategy integrationStrategy;

    protected boolean integrationFailedStatusUnstable;
    protected Integer allowedNoCommits;



    protected final static String LOG_PREFIX = "[PREINT] ";

    /**
     * Constructor for the SCM bridge.
     *
     * @param integrationStrategy The integration strategy to apply when merging commits.
     */
    public AbstractSCMBridge(IntegrationStrategy integrationStrategy, boolean integrationFailedStatusUnstable ) {
        this.integrationStrategy = integrationStrategy;
        this.integrationFailedStatusUnstable = integrationFailedStatusUnstable;
    }

    public boolean getIntegrationFailedStatusUnstable() {
        return this.integrationFailedStatusUnstable;
    }

    public void setIntegrationFailedStatusUnstable( boolean integrationFailedStatusUnstable) {
        this.integrationFailedStatusUnstable = integrationFailedStatusUnstable;
    }

    public Integer getAllowedNoCommits() {
        return this.allowedNoCommits;
    }

    public void setAllowedNoCommits( Integer allowedNoCommits) {
        this.allowedNoCommits = allowedNoCommits;
    }

    public void handleIntegrationExceptions(Run run, TaskListener listener, Exception e) throws IOException, InterruptedException {
        if ( e instanceof NothingToDoException ) {
            run.setResult(Result.NOT_BUILT);
            String logMessage = LOG_PREFIX + String.format("%s - setUp() - NothingToDoException - %s", LOG_PREFIX, e.getMessage());
            listener.getLogger().println(logMessage);
            LOGGER.log(Level.SEVERE, logMessage, e);
            throw new AbortException(e.getMessage());
        }
        if ( e instanceof IntegrationFailedException ||
                e instanceof IntegrationAllowedNoCommitException ) {
            String logMessage = String.format("%s - setUp() - %s%n%s%s",
                    LOG_PREFIX, e.getClass().getSimpleName(), e.getMessage(),
                            "\n" +
                            "NOTE:You have configured the Pretested plugin to set the status to UNSTABLE. \n" +
                            "The Jenkins logic is to process the build steps in UNSTABLE mode.\n" +
                            "Consider to configure/guard your build steps with: \n" +
                            "   https://wiki.jenkins-ci.org/display/JENKINS/Conditional+BuildStep+Plugin \n" +
                            "and test for build status.\n" +
                            "This will free up time/resources as there where content issues.\n" +
                            "The publisher part is only executed if the build is successful - hence no consequences\n" +
                            "of handling it either way..\n" +
                            "\n" );
            if (getIntegrationFailedStatusUnstable()) {

                run.setResult(Result.UNSTABLE);
            } else {
                run.setResult(Result.FAILURE);
                throw new AbortException(e.getMessage());
            }
            listener.getLogger().println(logMessage);
            LOGGER.log(Level.SEVERE, logMessage, e);
            return;
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
     * @throws PushFailedException
     */
    public void pushToIntegrationBranch(AbstractBuild<?, ?> build, BuildListener listener) throws PushFailedException {
    }

    /**
     * Deletes the integrated integrationBranch.
     *
     * @param run    The Build
     * @param listener The BuildListener
     * @throws BranchDeletionFailedException
     * @throws NothingToDoException
     * @throws UnsupportedConfigurationException
     */
    public void deleteIntegratedBranch(AbstractBuild<?, ?> build, TaskListener listener) throws BranchDeletionFailedException, NothingToDoException, UnsupportedConfigurationException, IOException, InterruptedException {
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
    protected void mergeChanges(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws NothingToDoException, IntegrationFailedException, IntegrationUnknownFailureException, UnsupportedConfigurationException, IntegrationAllowedNoCommitException {
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
    public void prepareWorkspace(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws EstablishingWorkspaceFailedException, NothingToDoException, IntegrationFailedException, IntegrationUnknownFailureException,UnsupportedConfigurationException, IntegrationAllowedNoCommitException {
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
     * @return The information of the result of the Phlow
     */
/*    public String getResultInfo() {
        return resultInfo;
    }
*/
    /**
     * @param resultInfo
     */
/*    public void setResultInfo(String resultInfo) {
        this.resultInfo = resultInfo;
    }
*/
    /**
     * @param environment environment
     * @return The Integration Branch name as variable expanded if possible - otherwise return integrationBranch
     */
    public String getExpandedIntegrationBranch(EnvVars environment) {
        return environment.expand(getIntegrationBranch());
    }


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
