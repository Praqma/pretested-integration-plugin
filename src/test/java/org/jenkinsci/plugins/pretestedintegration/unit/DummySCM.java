package org.jenkinsci.plugins.pretestedintegration.unit;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Result;
import java.io.IOException;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.pretestedintegration.AbstractSCMBridge;
import org.jenkinsci.plugins.pretestedintegration.exceptions.CommitFailedException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.EstablishingWorkspaceFailedException;
import org.jenkinsci.plugins.pretestedintegration.IntegrationStrategy;
import org.jenkinsci.plugins.pretestedintegration.SCMBridgeDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

import org.kohsuke.stapler.StaplerRequest;

public class DummySCM extends AbstractSCMBridge {

    private boolean commited = false;
    private boolean rolledBack = false;

    @DataBoundConstructor
    public DummySCM(IntegrationStrategy behaves) {
        super(behaves);
    }

    @Override
    public void commit(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws CommitFailedException {
        commited = true;
    }

    public boolean isCommited() {
        return commited;
    }

    public boolean isRolledBack() {
        return rolledBack;
    }

    @Override
    public void ensureBranch(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, String branch) throws EstablishingWorkspaceFailedException {

    }

    @Override
    public void handlePostBuild(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IOException {
        if (build.getResult().isBetterOrEqualTo(Result.SUCCESS)) {
            commit(build, launcher, listener);
        }
    }

    @Extension
    public static final class DescriptorImpl extends SCMBridgeDescriptor<DummyBridge> {

        @Override
        public String getDisplayName() {
            return "DummySCM";
        }

        @Override
        public DummyBridge newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            DummyBridge i = (DummyBridge) super.newInstance(req, formData);
            save();
            return i;
        }
    }
}
