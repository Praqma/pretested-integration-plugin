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
import java.util.logging.Level;
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
        logger.entering("PretestedIntegrationBuildWrapper",
				"findLatestBuildWithPreTestedIntegrationAction",
				new Object[] { build });// Generated code DONT TOUCH! Bookmark: e5a6737aaf1716293a86f1dc6a63f4e2
		AbstractBuild<?,?> start = build.getPreviousBuild();
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
        logger.exiting("PretestedIntegrationBuildWrapper",
				"findLatestBuildWithPreTestedIntegrationAction");// Generated code DONT TOUCH! Bookmark: e6eabc19c589cecb05c17fa1995117b1
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
        logger.entering("PretestedIntegrationBuildWrapper", "setUp",
				new Object[] { build, listener, launcher });// Generated code DONT TOUCH! Bookmark: 369385d08e04baa778ddf826119fd65e
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
                listener.getLogger().println(e.getMessage());    
                BuildQueue.getInstance().release();                
                logger.log(Level.SEVERE, LOG_PREFIX + "- setUp()", e);
            }  
        } catch (NothingToDoException ex) {
            build.setResult(Result.NOT_BUILT);
            listener.getLogger().println(ex.getMessage());
            logger.log(Level.SEVERE, LOG_PREFIX + "- setUp()", ex);
            BuildQueue.getInstance().release();
            everythingOk = false;
        } catch (EstablishWorkspaceException e) {
            build.setResult(Result.FAILURE);
            listener.getLogger().println(e.getMessage());
            logger.log(Level.SEVERE, LOG_PREFIX + "- setUp()", e);            
            BuildQueue.getInstance().release();
            everythingOk = false;
        } catch (NextCommitFailureException e) {
            build.setResult(Result.FAILURE);
            listener.getLogger().println(e.getMessage());
            logger.log(Level.SEVERE, LOG_PREFIX + "- setUp()", e);            
            BuildQueue.getInstance().release();
            everythingOk = false;
        } catch (RollbackFailureException e) {
            build.setResult(Result.FAILURE);
            listener.getLogger().println(e.getMessage());
            logger.log(Level.SEVERE, LOG_PREFIX + "- setUp()", e);            
            BuildQueue.getInstance().release();
            everythingOk = false;
        } catch (IntegationFailedExeception e) {
            build.setResult(Result.FAILURE);
            listener.getLogger().println(e.getMessage());
            logger.log(Level.SEVERE, LOG_PREFIX + "- setUp()", e);            
            BuildQueue.getInstance().release();
            everythingOk = false;
        } 

        BuildWrapper.Environment environment = new PretestEnvironment();
        logger.exiting("PretestedIntegrationBuildWrapper", "setUp");// Generated code DONT TOUCH! Bookmark: 26d03a511f894da2473586c56955ccca
		return everythingOk ? environment : null;
    }

    public void ensurePublisher(AbstractBuild<?, ?> build) throws IOException {
        logger.entering("PretestedIntegrationBuildWrapper", "ensurePublisher",
				new Object[] { build });// Generated code DONT TOUCH! Bookmark: acb06422ca5820e5d1346435817da866
		Describable<?> describable = build.getProject().getPublishersList().get(PretestedIntegrationPostCheckout.class);
        if (describable == null) {
            logger.info("Adding publisher to project");
            build.getProject().getPublishersList().add(new PretestedIntegrationPostCheckout());
        }
		logger.exiting("PretestedIntegrationBuildWrapper", "ensurePublisher");// Generated code DONT TOUCH! Bookmark: 3397f1ecf99b5c50d66a7e3b08c793dc
    }

    /**
     * Prints out version information.
     * @throws java.io.IOException
     * @throws java.lang.InterruptedException
     */
    
    public void preCheckout() throws IOException, InterruptedException {
		logger.exiting("PretestedIntegrationBuildWrapper", "preCheckout");// Generated code DONT TOUCH! Bookmark: 558c4b785b68571bd2de45d1261e2a52
		logger.entering("PretestedIntegrationBuildWrapper", "preCheckout");// Generated code DONT TOUCH! Bookmark: cd48bfcdc37ee500c8a1449eb752b966
        //nop
    }

    @Override
    public DescriptorImpl getDescriptor() {
        logger.exiting("PretestedIntegrationBuildWrapper", "getDescriptor");// Generated code DONT TOUCH! Bookmark: c05050f6ec75bbdeaa711eded25307bd
		logger.entering("PretestedIntegrationBuildWrapper", "getDescriptor");// Generated code DONT TOUCH! Bookmark: a04a866281166644880e76e2f6650a77
		return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static class DescriptorImpl extends BuildWrapperDescriptor {

        private final static Logger logger = Logger
				.getLogger(DescriptorImpl.class.getName());// Generated code DONT TOUCH! Bookmark: 3ca61d8e671737b5ead8aaccd31875c4

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

		private final static Logger logger = Logger
				.getLogger(PretestEnvironment.class.getName());// Generated code DONT TOUCH! Bookmark: 3ca61d8e671737b5ead8aaccd31875c4
    }

    private static final Logger logger = Logger.getLogger(PretestedIntegrationBuildWrapper.class.getName());
}
