package org.jenkinsci.plugins.pretestedintegration;

import java.io.IOException;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Descriptor.FormException;
import net.sf.json.JSONObject;

import org.jenkinsci.plugins.pretestedintegration.scm.mercurial.Mercurial;
import org.kohsuke.stapler.StaplerRequest;

public class DummySCM extends AbstractSCMInterface {
	
	private boolean commited = false;
	private boolean rolledBack = false;
	private Commit<?> commit = null;
	
	public void setCommit(Commit<?> commit){
		this.commit = commit;
	}
	
	@Override
	public Commit<?> nextCommit(
			AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, Commit<?> commit)
			throws IOException, IllegalArgumentException {
		return this.commit;
	}
	
	@Override
	public void commit(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) 
			throws IOException, InterruptedException {
		commited = true;
	}
	
	@Override
	public void rollback(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) 
			throws IOException, InterruptedException {
		rolledBack = true;
	}
	
	public boolean isCommited() {
		return commited;
	}
	
	public boolean isRolledBack() {
		return rolledBack;
	}
	
	@Extension
	public static final class DescriptorImpl extends SCMInterfaceDescriptor<Mercurial> {
		
		public String getDisplayName(){
			return "DummySCM";
		}
		
		@Override
		public Mercurial newInstance(StaplerRequest req, JSONObject formData) throws FormException {
			Mercurial i = (Mercurial) super.newInstance(req, formData);
			
			save();
			return i;
		}
		
	}
}
