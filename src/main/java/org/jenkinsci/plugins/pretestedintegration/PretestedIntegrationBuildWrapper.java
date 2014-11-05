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
import org.jenkinsci.plugins.pretestedintegration.exceptions.UnsupportedConfigurationException;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * The build wrapper determines what will happen before the build will run.
 * Depending on the chosen SCM, a more specific function will be called.
 */
public class PretestedIntegrationBuildWrapper extends BuildWrapper {

    public static final String LOG_PREFIX = "[PREINT] ";
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
        logger.entering("PretestedIntegrationBuildWrapper", "findLatestBuildWithPreTestedIntegrationAction", new Object[] { build });// Generated code DONT TOUCH! Bookmark: e5a6737aaf1716293a86f1dc6a63f4e2
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
        logger.exiting("PretestedIntegrationBuildWrapper", "findLatestBuildWithPreTestedIntegrationAction");// Generated code DONT TOUCH! Bookmark: e6eabc19c589cecb05c17fa1995117b1
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
        logger.entering("PretestedIntegrationBuildWrapper", "setUp", new Object[] { build, listener, launcher });// Generated code DONT TOUCH! Bookmark: 369385d08e04baa778ddf826119fd65e
        listener.getLogger().println(Jenkins.getInstance().getPlugin("pretested-integration").getWrapper().getVersion());
        
        boolean proceedToBuildStep = true;

        if(scmBridge.isApplicable(build, listener)) {
            BuildQueue.getInstance().enqueueAndWait();        
            PretestedIntegrationAction action;
            try {
                scmBridge.validateConfiguration(build.getProject());
                scmBridge.ensureBranch(build, launcher, listener, scmBridge.getBranch());
                //Create the action. Record the state of integration branch
                action = new PretestedIntegrationAction(build, launcher, listener, scmBridge);            
                build.addAction(action);                    
                action.initialise(launcher, listener);
                try {
                    ensurePublisher(build);
                } catch (IOException e) {
                    BuildQueue.getInstance().release();                
                }  
            } catch (NothingToDoException e) {
                build.setResult(Result.NOT_BUILT);
                listener.getLogger().println(e.getMessage());
                logger.log(Level.SEVERE, LOG_PREFIX + "- setUp()", e);            
                BuildQueue.getInstance().release();
                proceedToBuildStep = false;
            } catch (IntegationFailedExeception e) {
                build.setResult(Result.FAILURE);
                BuildQueue.getInstance().release();
                listener.getLogger().println(e.getMessage());
                logger.log(Level.SEVERE, LOG_PREFIX + "- setUp()", e);            
                proceedToBuildStep = false;
            } catch (EstablishWorkspaceException e) {
                build.setResult(Result.FAILURE);
                BuildQueue.getInstance().release();
                listener.getLogger().println(e.getMessage());
                logger.log(Level.SEVERE, LOG_PREFIX + "- setUp()", e);            
                proceedToBuildStep = false;
            } catch (NextCommitFailureException e) {
                build.setResult(Result.FAILURE);
                listener.getLogger().println(e.getMessage());
                logger.log(Level.SEVERE, LOG_PREFIX + "- setUp()", e);        
                BuildQueue.getInstance().release();    
            } catch (UnsupportedConfigurationException e) {
                build.setResult(Result.FAILURE);
                listener.getLogger().println(e.getMessage());
                logger.log(Level.SEVERE, LOG_PREFIX + "- setUp()", e);        
                listener.getLogger().println(e.getMessage());
                BuildQueue.getInstance().release();                            
                proceedToBuildStep = false;          
            }
        } else {
            listener.getLogger().println(String.format("%sSkipping the workspace preparation for pre tested integration", LOG_PREFIX));
            proceedToBuildStep = scmBridge.applySkipBehaviour(build, listener);
            listener.getLogger().println(String.format("%sProceed to build step = %s", LOG_PREFIX, proceedToBuildStep));
        }

        BuildWrapper.Environment environment = new PretestEnvironment();
        logger.exiting("PretestedIntegrationBuildWrapper", "setUp");
        return proceedToBuildStep ? environment : null;
    }

    public void ensurePublisher(AbstractBuild<?, ?> build) throws IOException {
        logger.entering("PretestedIntegrationBuildWrapper", "ensurePublisher", new Object[] { build });// Generated code DONT TOUCH! Bookmark: acb06422ca5820e5d1346435817da866
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
        logger.entering("PretestedIntegrationBuildWrapper", "preCheckout");// Generated code DONT TOUCH! Bookmark: cd48bfcdc37ee500c8a1449eb752b966
		logger.exiting("PretestedIntegrationBuildWrapper", "preCheckout");// Generated code DONT TOUCH! Bookmark: 558c4b785b68571bd2de45d1261e2a52
    }

    @Override
    public DescriptorImpl getDescriptor() {
        logger.entering("PretestedIntegrationBuildWrapper", "getDescriptor");// Generated code DONT TOUCH! Bookmark: a04a866281166644880e76e2f6650a77
        logger.exiting("PretestedIntegrationBuildWrapper", "getDescriptor");// Generated code DONT TOUCH! Bookmark: c05050f6ec75bbdeaa711eded25307bd		
		return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static class DescriptorImpl extends BuildWrapperDescriptor {

        private final static Logger logger = Logger.getLogger(DescriptorImpl.class.getName());// Generated code DONT TOUCH! Bookmark: 3ca61d8e671737b5ead8aaccd31875c4

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

    class PretestEnvironment extends BuildWrapper.Environment { }

    private static final Logger logger = Logger.getLogger(PretestedIntegrationBuildWrapper.class.getName());
}
