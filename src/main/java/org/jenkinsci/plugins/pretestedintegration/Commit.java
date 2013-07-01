package org.jenkinsci.plugins.pretestedintegration;

public class Commit<T> extends AbstractCommit<T> {

	public Commit(T commitId) {
		super(commitId);
	}
}
