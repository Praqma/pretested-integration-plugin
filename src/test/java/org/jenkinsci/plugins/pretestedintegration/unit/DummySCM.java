package org.jenkinsci.plugins.pretestedintegration.unit;

import org.jenkinsci.plugins.pretestedintegration.AbstractSCMBridge;
import org.jenkinsci.plugins.pretestedintegration.Commit;
import org.jenkinsci.plugins.pretestedintegration.IntegrationStrategy;
import org.jenkinsci.plugins.pretestedintegration.SCMBridgeDescriptor;
import org.jenkinsci.plugins.pretestedintegration.exceptions.EstablishWorkspaceException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.NextCommitFailureException;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.pretestedintegration.exceptions.CommitChangesFailureException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.RollbackFailureException;

import org.kohsuke.stapler.StaplerRequest;

public class DummySCM extends AbstractSCMBridge {
	
	private boolean commited = false;
	private boolean rolledBack = false;
	private Commit<?> commit = null;

        public DummySCM(IntegrationStrategy behaves) {
            super(behaves);
        }
	
	public void setCommit(Commit<?> commit){
		this.commit = commit;
	}
	
	@Override
	public Commit<?> nextCommit(
			AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, Commit<?> commit)
			throws NextCommitFailureException {
		return this.commit;
	}
	
	@Override
	public void commit(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws CommitChangesFailureException {
		commited = true;
	}
	
	@Override
	public void rollback(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws RollbackFailureException {
		rolledBack = true;
	}
	
	public boolean isCommited() {
		return commited;
	}
	
	public boolean isRolledBack() {
		return rolledBack;
	}

    @Override
    public void ensureBranch(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, String branch) throws EstablishWorkspaceException {
        
    }
	
	@Extension
	public static final class DescriptorImpl extends SCMBridgeDescriptor<DummyBridge> {
		
		public String getDisplayName(){
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
