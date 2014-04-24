package org.jenkinsci.plugins.pretestedintegration;

import java.io.IOException;
import java.util.logging.Logger;

import hudson.AbortException;
import hudson.Launcher;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;

public class PretestedIntegrationAction implements Action {

    AbstractBuild<?, ?> build;    
    AbstractSCMBridge scmBridge;
    Commit<?> last;
    Commit<?> commit;

    public PretestedIntegrationAction(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, AbstractSCMBridge scmBridge) throws IllegalArgumentException, IOException {
        this.build = build;
        this.scmBridge = scmBridge;
        try {

            this.last = build.getPreviousBuiltBuild().getAction(PretestedIntegrationAction.class).getCommit();
        } catch (NullPointerException e) {
            last = null;
        }
        this.commit = scmBridge.nextCommit(build, launcher, listener, last);
    }

    public String getDisplayName() {
        return null;
    }

    public String getIconFileName() {
        return null;
    }

    public String getUrlName() {
        return "pretested-integration";
    }

    public Commit<?> getCommit() {
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
     * @throws IOException
     * @throws AbortException
     * @throws IllegalArgumentException    
     */
    public boolean initialise(Launcher launcher, BuildListener listener) throws IllegalArgumentException, AbortException, IOException {
        boolean result = false;
        
        if (commit != null) {
            result = true;
            scmBridge.prepareWorkspace(build, launcher, listener, commit);
        }
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
    public boolean finalise(Launcher launcher, BuildListener listener) throws IllegalArgumentException, IOException {
        listener.getLogger().println("Finalising");
        scmBridge.handlePostBuild(build, launcher, listener);
        return true;
    }

    private static final Logger logger = Logger.getLogger(PretestedIntegrationAction.class.getName());
}
