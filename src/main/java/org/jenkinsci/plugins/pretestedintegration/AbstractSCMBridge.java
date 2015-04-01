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
    
    final static String LOG_PREFIX = "[PREINT] ";

    @DataBoundConstructor
    public AbstractSCMBridge(IntegrationStrategy integrationStrategy) {
        this.integrationStrategy = integrationStrategy;
    }
    
    public String getBranch() {
        logger.entering("AbstractSCMBridge", "getBranch");// Generated code DONT TOUCH! Bookmark: 7b6f3ea69850ae74b80bcda629a49db3
		logger.exiting("AbstractSCMBridge", "getBranch");// Generated code DONT TOUCH! Bookmark: 71ff2f36490cbfa5f2ad2fe00d1631a0
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
        logger.entering("AbstractSCMBridge", "getBehaviours");// Generated code DONT TOUCH! Bookmark: 683867f2e0a65e8854a550f17520af17
		List<IntegrationStrategyDescriptor<?>> list = new ArrayList<IntegrationStrategyDescriptor<?>>();
        for(IntegrationStrategyDescriptor<?> descr : IntegrationStrategy.all()) {
           list.add(descr);
        }        
        logger.exiting("AbstractSCMBridge", "getBehaviours");// Generated code DONT TOUCH! Bookmark: ab82e2ca5b94ef8089d6c1509b2b6c3d
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
    public void prepareWorkspace(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws EstablishWorkspaceException, NothingToDoException, IntegationFailedExeception, UnsupportedConfigurationException {    
        logger.entering("AbstractSCMBridge", "prepareWorkspace", new Object[] { build, listener, launcher });// Generated code DONT TOUCH! Bookmark: 153879db1346111ba452f21bdef681e3
		mergeChanges(build, launcher, listener);
		logger.exiting("AbstractSCMBridge", "prepareWorkspace");// Generated code DONT TOUCH! Bookmark: c1434f914c8ce73e41e5ea7b7ea1029a
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
    protected void mergeChanges(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws NothingToDoException, IntegationFailedExeception, UnsupportedConfigurationException {
        logger.entering("AbstractSCMBridge", "mergeChanges", new Object[] { build, listener, launcher });// Generated code DONT TOUCH! Bookmark: 2929a792ffc98c3546ea6e7755f81916
		integrationStrategy.integrate(build, launcher, listener, this);
		logger.exiting("AbstractSCMBridge", "mergeChanges");// Generated code DONT TOUCH! Bookmark: a55866afd1caa8a8ab61b77dbbc2f130
    }
    
    /**
     * Method that determines if we should prepare workspace for integration. If not we throw the nothing to do exception
     * @param build
     * @param listener
     * @return 
     */
    public void isApplicable(AbstractBuild<?,?> build, BuildListener listener) throws NothingToDoException, UnsupportedConfigurationException { }

    public abstract void ensureBranch(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, String branch) throws EstablishWorkspaceException;
    
    /**
     * Override this to associate an integrated commit with a pointer with the starting point before merge. This is used to roll back in case of integraion failure
     * @param build
     * @param launcher
     * @param listener
     * @return 
     */
    protected Commit<?> determineIntegrationHead(AbstractBuild<?, ?> build, Launcher launcher, TaskListener listener) {
        logger.entering("AbstractSCMBridge", "determineIntegrationHead", new Object[] { build, listener, launcher });// Generated code DONT TOUCH! Bookmark: 94e894e8dad5dfb0e85092e8e93ff296
        logger.exiting("AbstractSCMBridge", "determineIntegrationHead");// Generated code DONT TOUCH! Bookmark: 19478dfa81d1cac69955a0cb6ee4ecdd		
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
        logger.entering("AbstractSCMBridge", "nextCommit", new Object[] { build, listener, launcher, commit });// Generated code DONT TOUCH! Bookmark: b28ff589e6d8f755b1062bf3d667b16d
        logger.exiting("AbstractSCMBridge", "nextCommit");// Generated code DONT TOUCH! Bookmark: f0aa06678500b5cf31af6f72481f9561		
		return null;
    }

    public void commit(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws CommitChangesFailureException {
		logger.entering("AbstractSCMBridge", "commit", new Object[] { build, listener, launcher });// Generated code DONT TOUCH! Bookmark: 0858e763f8f80805e3caa7a6a8957226
		logger.exiting("AbstractSCMBridge", "commit");// Generated code DONT TOUCH! Bookmark: 88b215c6f16b1aa5e6458f4686fef503        
    }

    public void deleteIntegratedBranch(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws DeleteIntegratedBranchException, NothingToDoException, UnsupportedConfigurationException {
        logger.entering("AbstractSCMBridge", "deleteIntegratedBranch", new Object[] { build, listener, launcher });// Generated code DONT TOUCH! Bookmark: a499b16a8f9e177b3cb5bb52f9db446b
		logger.exiting("AbstractSCMBridge", "deleteIntegratedBranch");// Generated code DONT TOUCH! Bookmark: ec2017cf2a7526cfb7470217ea25413e
    }
    
    public void updateBuildDescription(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws NothingToDoException, UnsupportedConfigurationException {
        logger.entering("AbstractSCMBridge", "updateBuildDescription", new Object[] { build, listener, launcher });// Generated code DONT TOUCH! Bookmark: c0623c03e84a75c85d9db68bb8078a54
		logger.exiting("AbstractSCMBridge", "updateBuildDescription");// Generated code DONT TOUCH! Bookmark: 832f1d788154dbccfc795d2762c455ca		
    }

    public Result getRequiredResult() {
        logger.entering("AbstractSCMBridge", "getRequiredResult");// Generated code DONT TOUCH! Bookmark: f015d31908905293b47ac7cd98635eb8
		logger.exiting("AbstractSCMBridge", "getRequiredResult");// Generated code DONT TOUCH! Bookmark: e5202ad86a308a7620940ca0487810ef
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
    
    public void validateConfiguration(AbstractProject<?,?> project) throws UnsupportedConfigurationException { }
    
    private static final Logger logger = Logger.getLogger(AbstractSCMBridge.class.getName());

}
