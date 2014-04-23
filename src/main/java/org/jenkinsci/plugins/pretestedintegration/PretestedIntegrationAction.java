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
     * @throws IllegalArgumentException
     * @throws IOException
     */
    public boolean finalise(Launcher launcher, BuildListener listener) throws IllegalArgumentException, IOException {
        listener.getLogger().println("Finalising");
        scmBridge.handlePostBuild(build, launcher, listener);

        scmBridge.getDescriptor().save();

        //Trigger a new build if there are more commits
        //Commit<?> next = scmBridge.nextCommit(build, launcher, listener, getCommit());
        //TODO: Add a condition such that builds are only triggered if no more builds are scheduled        
        if (getCommit() != null) {            
            listener.getLogger().println(String.format( "Triggering new build for commit: %s%nLast commit was: %s", getCommit().getId(), last != null ? last.getId() : "no previous commit") );
            build.getProject().scheduleBuild2(0);
        }
        return true;
    }

    private static final Logger logger = Logger.getLogger(PretestedIntegrationAction.class.getName());
}
