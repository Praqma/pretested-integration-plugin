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
import hudson.plugins.git.GitSCM;
import hudson.scm.SCM;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.multiplescms.MultiSCM;
import org.jenkinsci.plugins.pretestedintegration.exceptions.RollbackFailureException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.UnsupportedConfigurationException;
import org.jenkinsci.plugins.pretestedintegration.scm.git.GitBridge;

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
    
    //For JENKINS-24754
    private void validateGitScm(GitSCM scm) throws UnsupportedConfigurationException {
        if(scm.getRepositories().size() > 1 && StringUtils.isBlank(((GitBridge)scmBridge).getRepoName())) {
            throw new UnsupportedConfigurationException(String.format("You have not defined a repository name in your Pre Tested Integration configuration, but you have multiple scm checkouts listed in your scm config%nCurrently this is not supported"));
        }        
    }
    
    //For JENKINS-24754
    private void validateConfiguration(AbstractProject<?,?> project) throws UnsupportedConfigurationException  {

        if( project.getScm() instanceof GitSCM ) {
            validateGitScm((GitSCM)project.getScm());
        } else if(Jenkins.getInstance().getPlugin("multiple-scms") != null && project.getScm() instanceof MultiSCM ) {
            MultiSCM multiscm = (MultiSCM)project.getScm();
            int gitCounter = 0;
            for(SCM scm : multiscm.getConfiguredSCMs()) {                
                if(scm instanceof GitSCM) {
                    GitSCM gitMultiScm = (GitSCM)scm;                    
                    validateGitScm(gitMultiScm);
                    gitCounter++;
                }
            }
            
            if(gitCounter > 1 && StringUtils.isBlank(((GitBridge)scmBridge).getRepoName())) {
                throw new UnsupportedConfigurationException("You haave included multiple git repositories in your multi scm configuration, but have not defined a repository name in the pre tested integration configuration");
            }            
        } else {
            throw new UnsupportedConfigurationException("We only support git and mutiple scm plugins");
        } 
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
            validateConfiguration(build.getProject());
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
        } catch (UnsupportedConfigurationException ex) {
            build.setResult(Result.FAILURE);
            BuildQueue.getInstance().release();            
            listener.getLogger().println(ex.getMessage());
            return null;            
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
