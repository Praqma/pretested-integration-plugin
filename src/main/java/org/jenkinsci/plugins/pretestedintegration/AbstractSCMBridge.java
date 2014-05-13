package org.jenkinsci.plugins.pretestedintegration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.kohsuke.stapler.DataBoundConstructor;

import jenkins.model.Jenkins;

import hudson.AbortException;
import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Describable;
import hudson.model.AbstractBuild;
import hudson.model.Descriptor;
import hudson.model.Result;
import hudson.model.TaskListener;
import java.util.logging.Level;
import org.apache.commons.lang.StringUtils;

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
     * @throws AbortException It is not possible to leave the workspace in a
     * state as described above.
     * @throws java.lang.InterruptedException
     * @throws IOException
     */
    public void prepareWorkspace(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, Commit<?> commit) throws IOException, InterruptedException {
        logger.finest(LOG_PREFIX + "Entering prepareWorkspace");
        
        listener.getLogger().println( LOG_PREFIX + "Invoking ensureBranch with branch: " + branch);            
        ensureBranch(build, launcher, listener, branch);            
        listener.getLogger().println( LOG_PREFIX + "Invoking mergeChanges with commit: " + commit.getId().toString());
        mergeChanges(build, launcher, listener, commit);

        logger.finest(LOG_PREFIX + "Exiting prepareWorkspace");
    }

    /**
     * Default is to use the selected integration strategy
     * @param build
     * @param launcher
     * @param listener
     * @param commit
     * @throws IOException
     * @throws InterruptedException 
     */
    protected void mergeChanges(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, Commit<?> commit) throws IOException, InterruptedException {
        integrationStrategy.integrate(build, launcher, listener, this, commit);
    }

    public abstract void ensureBranch(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, String branch) throws IOException, InterruptedException;
    
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
     * @throws IOException A repository could not be reached.
     */
    public Commit<?> nextCommit(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, Commit<?> commit) throws IOException, IllegalArgumentException {
        return null;
    }

    public void commit(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        //nop
    }

    public void rollback(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        //nop
    }
    
    public void deleteIntegratedBranch(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        //nop
    }
    
    public void updateBuildDescription(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        
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
    public void handlePostBuild( AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IOException {
        logger.finest(LOG_PREFIX + "Entering handlePostBuild");
        Result result = build.getResult();
        //TODO: make the success criteria configurable in post-build step
        if (result != null && result.isBetterOrEqualTo(getRequiredResult())) { //Commit the changes
            try {
                listener.getLogger().println(LOG_PREFIX + "Commiting changes");
                commit(build, launcher, listener);
                deleteIntegratedBranch(build, launcher, listener);
            } catch (InterruptedException e) {
                throw new AbortException(LOG_PREFIX + "Commiting changes on integration branch exited unexpectedly");
            } finally {
                try {
                    updateBuildDescription(build, launcher, listener);
                } catch (Exception ex) {
                    //Don't care
                }
            }
        } else {
            try {
                rollback(build, launcher, listener);
            } catch (InterruptedException ex) {
                listener.getLogger().println("Fatal error occured when trying to undo changes");
                ex.printStackTrace(listener.getLogger());
            } finally {
                try {
                    updateBuildDescription(build, launcher, listener);
                } catch (Exception ex) {
                    //Don't care
                } 
            }
        }  
    }
    
    private static final Logger logger = Logger.getLogger(AbstractSCMBridge.class.getName());

}
