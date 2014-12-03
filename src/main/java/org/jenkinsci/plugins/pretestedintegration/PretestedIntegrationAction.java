package org.jenkinsci.plugins.pretestedintegration;

import org.jenkinsci.plugins.pretestedintegration.exceptions.EstablishWorkspaceException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.IntegationFailedExeception;
import org.jenkinsci.plugins.pretestedintegration.exceptions.NextCommitFailureException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.NothingToDoException;
import java.io.IOException;
import java.util.logging.Logger;

import hudson.Launcher;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import org.jenkinsci.plugins.pretestedintegration.exceptions.UnsupportedConfigurationException;

public class PretestedIntegrationAction implements Action {

    AbstractBuild<?, ?> build;    
    AbstractSCMBridge scmBridge;
    Commit<?> last;
    
    private Commit<?> currentIntegrationTip;
    @Deprecated
    Commit<?> commit;
    
    public PretestedIntegrationAction(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, AbstractSCMBridge scmBridge) throws NextCommitFailureException {
        this.build = build;
        this.scmBridge = scmBridge;
        this.currentIntegrationTip = scmBridge.determineIntegrationHead(build, launcher, listener);
        try {
            this.last = build.getPreviousBuiltBuild().getAction(PretestedIntegrationAction.class).getCommit();
        } catch (NullPointerException e) {
            last = null;
        }
        //this.commit = scmBridge.nextCommit(build, launcher, listener, last);
    }
    
    @Override
    public String getDisplayName() {
		return null;
    }

    @Override
    public String getIconFileName() {		
		return null;
    }

    @Override
    public String getUrlName() {
		return "pretested-integration";
    }

    public Commit<?> getCommit() {
        logger.entering("PretestedIntegrationAction", "getCommit");// Generated code DONT TOUCH! Bookmark: b43ca3a0527679c146edbad0873f0122
        logger.exiting("PretestedIntegrationAction", "getCommit");// Generated code DONT TOUCH! Bookmark: 674d4923ce7e049dffd9392fabc70101		
		return this.commit;
    }

    /**
     * Invoked before the build is started, responsible for preparing the
     * workspace
     *
     * @param launcher
     * @param listener
     * @return True if any changes are made and the workspace has been prepared,
     * false otherwise
     * @throws IntegationFailedExeception
     * @throws NothingToDoException
     * @throws EstablishWorkspaceException    
     */
    public boolean initialise(Launcher launcher, BuildListener listener) throws IntegationFailedExeception, NothingToDoException, EstablishWorkspaceException, UnsupportedConfigurationException {
        logger.entering("PretestedIntegrationAction", "initialise", new Object[] { listener, launcher });// Generated code DONT TOUCH! Bookmark: 243ef9e5f61005fcf1963a350f7abb77
		boolean result = false;

        scmBridge.prepareWorkspace(build, launcher, listener);
        
        logger.exiting("PretestedIntegrationAction", "initialise");// Generated code DONT TOUCH! Bookmark: 6f58d37470766bd11e40a451648336e5
		return result;
    }

    /**
     * Invoked by the notifier, responsible for commiting or rolling back the
     * workspace
     *
     * @param launcher
     * @param listener
     * @throws IllegalArgumentException
     * @throws IOException
     * @return {@link Boolean} indicating success or failure
     
     */
    public boolean finalise(Launcher launcher, BuildListener listener) throws IOException {
        logger.entering("PretestedIntegrationAction", "finalise", new Object[] { listener, launcher });// Generated code DONT TOUCH! Bookmark: 23e6352a21c64c077dc4297b12d4daa6
		scmBridge.handlePostBuild(build, launcher, listener);
        logger.exiting("PretestedIntegrationAction", "finalise");// Generated code DONT TOUCH! Bookmark: e8f9ed6662703ed637d9ea56f8214d09
		return true;
    }

    private static final Logger logger = Logger.getLogger(PretestedIntegrationAction.class.getName());

    /**
     * @return the currentIntegrationTip
     */
    public Commit<?> getCurrentIntegrationTip() {
        logger.entering("PretestedIntegrationAction", "getCurrentIntegrationTip");// Generated code DONT TOUCH! Bookmark: ffd3b899be05f13052473c38f95fa2d7
        logger.exiting("PretestedIntegrationAction", "getCurrentIntegrationTip");// Generated code DONT TOUCH! Bookmark: 6cdb1c69a0b483325f5f54412d160b1f
		return currentIntegrationTip;
    }

    /**
     * @param currentIntegrationTip the currentIntegrationTip to set
     */
    public void setCurrentIntegrationTip(Commit<?> currentIntegrationTip) {
        logger.entering("PretestedIntegrationAction", "setCurrentIntegrationTip", new Object[] { currentIntegrationTip });// Generated code DONT TOUCH! Bookmark: 7fe363971caf294085b34a533da2ceed
		this.currentIntegrationTip = currentIntegrationTip;
		logger.exiting("PretestedIntegrationAction", "setCurrentIntegrationTip");// Generated code DONT TOUCH! Bookmark: 6bd252060b7896da02bf44455dd4a422
    }
}
