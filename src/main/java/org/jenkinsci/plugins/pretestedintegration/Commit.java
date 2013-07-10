package org.jenkinsci.plugins.pretestedintegration;

import java.util.logging.Logger;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Hudson;

public class Commit<T> implements ExtensionPoint {
	
	private T commitId;
	
	public Commit(T commitId){
		this.commitId = commitId;
	}
	
	public T getId(){
		return this.commitId;
	}
	
	public static ExtensionList<Commit> all() {
		return Hudson.getInstance().getExtensionList(Commit.class);
	}
	
	private static Logger logger = Logger.getLogger(PretestedIntegrationBuildWrapper.class.getName());
}
