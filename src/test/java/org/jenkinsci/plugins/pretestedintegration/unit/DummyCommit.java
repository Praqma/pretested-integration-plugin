package org.jenkinsci.plugins.pretestedintegration.unit;

import hudson.Extension;
import org.jenkinsci.plugins.pretestedintegration.Commit;

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
