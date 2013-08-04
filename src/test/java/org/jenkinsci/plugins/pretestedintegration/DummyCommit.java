package org.jenkinsci.plugins.pretestedintegration;

import hudson.Extension;

@Extension
public class DummyCommit extends Commit<String> {

	/* for unit testing purposes */
	public DummyCommit(){
		super("");
	}
	
	public DummyCommit(String commitId) {
		super(commitId);
		// TODO Auto-generated constructor stub
	}

}
