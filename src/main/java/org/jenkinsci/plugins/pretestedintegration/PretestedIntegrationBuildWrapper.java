package org.jenkinsci.plugins.pretestedintegration;

import org.jenkinsci.plugins.pretestedintegration.exceptions.EstablishWorkspaceException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.IntegationFailedExeception;
import org.jenkinsci.plugins.pretestedintegration.exceptions.NextCommitFailureException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.NothingToDoException;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Describable;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.FreeStyleProject;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.pretestedintegration.exceptions.RollbackFailureException;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * The build wrapper determines what will happen before the build will run.
 * Depending on the chosen SCM, a more specific function will be called.
 */
public class PretestedIntegrationBuildWrapper extends BuildWrapper {

    private static final String LOG_PREFIX = "[PREINT] ";
    public final AbstractSCMBridge scmBridge;
    private final boolean rollbackEnabled = false;

    @DataBoundConstructor
    public PretestedIntegrationBuildWrapper(final AbstractSCMBridge scmBridge) {
        this.scmBridge = scmBridge;
    }
    
    /**
     * Goes through the list of builds..finds the latest build which was a pre-test integration.
     * @param build
     * @return 
     */
    private AbstractBuild<?,?> findLatestBuildWithPreTestedIntegrationAction(AbstractBuild<?,?> build) {        
        AbstractBuild<?,?> start = build.getPreviousBuild();;
        for(AbstractBuild<?,?> i = start; i != null; i = i.getNextBuild()) {
            //If the previous build was not pre-test enabled, take next
            if(i.getAction(PretestedIntegrationAction.class) == null) {
                continue;
            }
            
            //if the build is pre-test. Then we only return non-null in case the build was failed.
            if(i.getResult().isWorseThan(scmBridge.getRequiredResult())) {
                return i;
            } else {
                return null;
            }
        }
        return null;
    }

    /**
     * Jenkins hook that fires after the workspace is initialized. Calls the
     * SCM-specific function according to the chosen SCM.
     *
     * @param build
     * @param launcher
     * @param listener
     * @return
     */
    @Override
    public BuildWrapper.Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) {
        listener.getLogger().println(Jenkins.getInstance().getPlugin("pretested-integration").getWrapper().getVersion());
        boolean everythingOk = true;
        //There can be only one... at a time
        BuildQueue.getInstance().enqueueAndWait();        
        PretestedIntegrationAction action;
        try {            
            scmBridge.ensureBranch(build, launcher, listener, scmBridge.getBranch());
            
            //Create the action. Record the state of integration branch
            action = new PretestedIntegrationAction(build, launcher, listener, scmBridge);            
            build.addAction(action);
            
            if(rollbackEnabled) {
                /**
                 * If the previous build failed...then we revert to the state of master prior to that particular commit being integrated.
                 */
                AbstractBuild<?,?> latestBuildWithPreTest = findLatestBuildWithPreTestedIntegrationAction(build);            
                if(latestBuildWithPreTest != null ) {                
                    scmBridge.rollback(latestBuildWithPreTest.getPreviousBuild(), launcher, listener);                
                }
            }            
            
            action.initialise(launcher, listener);
            try {
                ensurePublisher(build);
            } catch (IOException e) {
                BuildQueue.getInstance().release();                
            }  
        } catch (NothingToDoException ex) {
            build.setResult(Result.NOT_BUILT);
            BuildQueue.getInstance().release();
            everythingOk = false;
        } catch (IntegationFailedExeception e) {
            build.setResult(Result.FAILURE);
            BuildQueue.getInstance().release();
            everythingOk = false;
        } catch (EstablishWorkspaceException established) {
            build.setResult(Result.FAILURE);
            BuildQueue.getInstance().release();
            everythingOk = false;
        } catch (NextCommitFailureException ex) {
            build.setResult(Result.FAILURE);
            BuildQueue.getInstance().release();
            everythingOk = false;
        } catch (RollbackFailureException ex) {
            build.setResult(Result.FAILURE); 
            BuildQueue.getInstance().release();
            everythingOk = false;        
        }

        BuildWrapper.Environment environment = new PretestEnvironment();
        return everythingOk ? environment : null;
    }

    public void ensurePublisher(AbstractBuild<?, ?> build) throws IOException {
        Describable<?> describable = build.getProject().getPublishersList().get(PretestedIntegrationPostCheckout.class);
        if (describable == null) {
            logger.info("Adding publisher to project");
            build.getProject().getPublishersList().add(new PretestedIntegrationPostCheckout());
        }
    }

    /**
     * Prints out version information.
     * @throws java.io.IOException
     * @throws java.lang.InterruptedException
     */
    
    public void preCheckout() throws IOException, InterruptedException {
        //nop
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static class DescriptorImpl extends BuildWrapperDescriptor {

        public DescriptorImpl() {
            load();
        }

        public String getDisplayName() {
            return "Use pretested integration";
        }

        public List<SCMBridgeDescriptor<?>> getSCMBridges() {
            return AbstractSCMBridge.getDescriptors();
        }

        @Override
        public boolean isApplicable(AbstractProject<?, ?> arg0) {
            return arg0 instanceof FreeStyleProject;
        }
    }

    class PretestEnvironment extends BuildWrapper.Environment {
    }

    private static final Logger logger = Logger.getLogger(PretestedIntegrationBuildWrapper.class.getName());
}
