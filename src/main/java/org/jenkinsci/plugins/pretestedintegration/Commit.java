package org.jenkinsci.plugins.pretestedintegration;

import java.util.logging.Logger;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Hudson;

public class Commit<T> implements ExtensionPoint {
	
	private final T commitId;
	
	public Commit(T commitId){
		logger.finest("Creating commit with id: " + commitId.toString());
		this.commitId = commitId;
	}
	
	public T getId(){
		logger.entering("Commit", "getId");// Generated code DONT TOUCH! Bookmark: 852679f6e176ac77f59b18a702f68d2a
		logger.exiting("Commit", "getId");// Generated code DONT TOUCH! Bookmark: c09e520e8d2c040802722b0a25b6ec0a
		return this.commitId;
	}
	
	public static ExtensionList<Commit> all() {
		logger.entering("Commit", "all");// Generated code DONT TOUCH! Bookmark: deefc50556b39588e2e9a1ff76461db7
		logger.exiting("Commit", "all");// Generated code DONT TOUCH! Bookmark: ee477ee51fae325cfdc03060546322b6
		return Hudson.getInstance().getExtensionList(Commit.class);
	}
	
	private static final Logger logger = Logger.getLogger(Commit.class.getName());
}
