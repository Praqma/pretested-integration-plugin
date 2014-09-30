package org.jenkinsci.plugins.pretestedintegration;

import hudson.plugins.git.Branch;
import hudson.plugins.git.util.BuildData;
import org.jenkinsci.plugins.pretestedintegration.exceptions.EstablishWorkspaceException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.IntegationFailedExeception;
import org.jenkinsci.plugins.pretestedintegration.exceptions.NextCommitFailureException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.NothingToDoException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.kohsuke.stapler.DataBoundConstructor;
import jenkins.model.Jenkins;
import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Describable;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.Result;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.pretestedintegration.exceptions.CommitChangesFailureException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.DeleteIntegratedBranchException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.UnsupportedConfigurationException;

public abstract class AbstractSCMBridge implements Describable<AbstractSCMBridge>, ExtensionPoint {

    protected String branch;
    public final IntegrationStrategy integrationStrategy;
    
    final static String LOG_PREFIX = "[PREINT-SCM] ";

    @DataBoundConstructor
    public AbstractSCMBridge(IntegrationStrategy integrationStrategy) {
        this.integrationStrategy = integrationStrategy;
    }
    
    public String getBranch() {
        return branch;
    }

    public Descriptor<AbstractSCMBridge> getDescriptor() {
        return (SCMBridgeDescriptor<?>) Jenkins.getInstance().getDescriptorOrDie(getClass());
    }

    public static DescriptorExtensionList<AbstractSCMBridge, SCMBridgeDescriptor<AbstractSCMBridge>> all() {
        return Jenkins.getInstance().<AbstractSCMBridge, SCMBridgeDescriptor<AbstractSCMBridge>>getDescriptorList(AbstractSCMBridge.class);
    }

    public static List<SCMBridgeDescriptor<?>> getDescriptors() {
        List<SCMBridgeDescriptor<?>> list = new ArrayList<SCMBridgeDescriptor<?>>();
        for (SCMBridgeDescriptor<?> d : all()) {
            list.add(d);
        }
        return list;
    }
    
    public static List<IntegrationStrategyDescriptor<?>> getBehaviours() {
        List<IntegrationStrategyDescriptor<?>> list = new ArrayList<IntegrationStrategyDescriptor<?>>();
        for(IntegrationStrategyDescriptor<?> descr : IntegrationStrategy.all()) {
           list.add(descr);
        }        
        return list;
    }
    

    /**
     * This function is called after the SCM plugin has updated the workspace
     * with remote changes. When this function has been run, the workspace must
     * be ready to perform a build and tests. The integration branch must be
     * checked out, and the given commit must be merged into it.
     *
     * @param build
     * @param launcher
     * @param listener
     * @param commit This commit represents the code that must be checked out.
     *
     * @throws IntegationFailedExeception 
     * @throws EstablishWorkspaceException
     * @throws NothingToDoException
     */
    public void prepareWorkspace(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, Commit<?> commit) throws EstablishWorkspaceException, NothingToDoException, IntegationFailedExeception {    
        mergeChanges(build, launcher, listener, commit);
    }

    /**
     * Default is to use the selected integration strategy
     * @param build
     * @param launcher
     * @param listener
     * @param commit
     * @throws NothingToDoException
     * @throws IntegationFailedExeception 
     */
    protected void mergeChanges(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, Commit<?> commit) throws NothingToDoException, IntegationFailedExeception {
        integrationStrategy.integrate(build, launcher, listener, this, commit);
    }
    
    /**
     * Method that determines if we should prepare workspace for integration, it
     * If this returns false, the applySkipBehaviour method is called to determine if we should proceed to the build step.
     * @param build
     * @param listener
     * @return 
     */
    public boolean isApplicable(AbstractBuild<?,?> build, BuildListener listener) {
        return true;
    }
    
    /**
     * Method that applies all the necessary behaviour when we have determined to skip.
     * By default we return true, indicating that the pre build step was OK.
     * @param build
     * @param listener
     * @return 
     */
    public boolean applySkipBehaviour(AbstractBuild<?,?> build, BuildListener listener) {
        return true;
    }

    public abstract void ensureBranch(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, String branch) throws EstablishWorkspaceException;
    
    /**
     * Override this to associate an integrated commit with a pointer with the starting point before merge. This is used to roll back in case of integraion failure
     * @param build
     * @param launcher
     * @param listener
     * @return 
     */
    protected Commit<?> determineIntegrationHead(AbstractBuild<?, ?> build, Launcher launcher, TaskListener listener) {
        return null;
    }

    /**
     * Calculate and return the next commit from the argument
     *
     * @param build
     * @param launcher
     * @param listener
     * @param commit
     * @return The next pending commit. If no commit is pending null is
     * returned.
     *
     * @throws NextCommitFailureException
     */
    public Commit<?> nextCommit(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, Commit<?> commit) throws NextCommitFailureException {
        return null;
    }

    public void commit(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws CommitChangesFailureException {
        //nop
    }
    
    public void deleteIntegratedBranch(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws DeleteIntegratedBranchException {
        //nop
    }
    
    public void updateBuildDescription(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
        //nop
    }

    public Result getRequiredResult() {
        return Result.SUCCESS;
    }

    /**
     * This is called after the build has run. If the build was successful, the
     * changes should be committed, otherwise the workspace is cleared as before
     * the changes
     *
     * @param build The status of the build.
     * @param launcher
     * @param listener
     *
     * @throws IOException A repository could not be reached.
     */
    public abstract void handlePostBuild( AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IOException;
    
    public void validateConfiguration(AbstractProject<?,?> project) throws UnsupportedConfigurationException {
        
    }
    
    private static final Logger logger = Logger.getLogger(AbstractSCMBridge.class.getName());

}
