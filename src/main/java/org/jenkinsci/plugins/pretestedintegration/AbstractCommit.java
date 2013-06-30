package org.jenkinsci.plugins.pretestedintegration;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Hudson;

public abstract class AbstractCommit<T> implements ExtensionPoint {
	
	private T commitId;
	
	public AbstractCommit(T commitId){
		this.commitId = commitId;
	}
	
	public T getId(){
		return this.commitId;
	}
	
	public static ExtensionList<AbstractCommit> all() {
		return Hudson.getInstance().getExtensionList(AbstractCommit.class);
	}
}
