package org.jenkinsci.plugins.pretestedintegration;

import org.jenkinsci.plugins.pretestedintegration.exceptions.EstablishWorkspaceException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.IntegationFailedExeception;
import org.jenkinsci.plugins.pretestedintegration.exceptions.NextCommitFailureException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.NothingToDoException;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Describable;
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
    
    @Deprecated
    private final boolean rollbackEnabled = false;

    @DataBoundConstructor
    public PretestedIntegrationBuildWrapper(final AbstractSCMBridge scmBridge) {
        this.scmBridge = scmBridge;
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
        listener.getLogger().println( String.format("%sPreparing environment using Pretested Integration Plugin %s ", LOG_PREFIX, Jenkins.getInstance().getPlugin("pretested-integration").getWrapper().getVersion()));        
        boolean proceedToBuildStep = true;        
        
        PretestedIntegrationAction action;
        try {        
            // Check job configuration - there are typically requirements
            // on how job is configured before we can allow integration.
            scmBridge.validateConfiguration(build.getProject());
            // isApplicable basically checks the changeset we git from SCM
            // can be used to configure a workspace.
            // This is where we check for contraints on what to integrate,
            // if there is anything to integrate, if there is ambiguiuty in
            // what to integrate.
            scmBridge.isApplicable(build, listener);
            
            //Updates workspace to the integration branch
            scmBridge.ensureBranch(build, launcher, listener, scmBridge.getBranch());
            
            //Create the action. Record the state of integration branch
            action = new PretestedIntegrationAction(build, launcher, listener, scmBridge);            
            build.addAction(action);                    
            action.initialise(launcher, listener);
            try {
                ensurePublisher(build);
            } catch (IOException ex) {
                logger.log(Level.WARNING, LOG_PREFIX+" "+"Failed to add publisher", ex);
            } 
        } catch (NothingToDoException e) {
            build.setResult(Result.NOT_BUILT);
            listener.getLogger().println(e.getMessage());
            logger.log(Level.SEVERE, LOG_PREFIX + "- setUp()-NothingToDoException", e);                        
            proceedToBuildStep = false;
        } catch (IntegationFailedExeception e) {
            build.setResult(Result.FAILURE);
            listener.getLogger().println(e.getMessage());
            logger.log(Level.SEVERE, LOG_PREFIX + "- setUp()-IntegationFailedExeception", e);            
            proceedToBuildStep = false;
        } catch (EstablishWorkspaceException e) {
            build.setResult(Result.FAILURE);
            listener.getLogger().println(e.getMessage());
            logger.log(Level.SEVERE, LOG_PREFIX + "- setUp()-EstablishWorkspaceException", e);            
            proceedToBuildStep = false;
        } catch (NextCommitFailureException e) {
            build.setResult(Result.FAILURE);
            listener.getLogger().println(e.getMessage());
            logger.log(Level.SEVERE, LOG_PREFIX + "- setUp()-NextCommitFailureException ", e);        
        } catch (UnsupportedConfigurationException e) {
            build.setResult(Result.FAILURE);
            listener.getLogger().println(e.getMessage());
            logger.log(Level.SEVERE, LOG_PREFIX + "- setUp()-UnsupportedConfigurationException", e);        
            listener.getLogger().println(e.getMessage());                          
            proceedToBuildStep = false;          
        } catch (Exception ex) {
            build.setResult(Result.FAILURE);
            listener.getLogger().println(String.format("%sUnexpected error. Check log for details", LOG_PREFIX));
            logger.log(Level.SEVERE, LOG_PREFIX + "- setUp() - Unexpected error", ex);
            proceedToBuildStep = false;
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
		return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static class DescriptorImpl extends BuildWrapperDescriptor {
        
		public DescriptorImpl() {
            load();
        }

        @Override
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
