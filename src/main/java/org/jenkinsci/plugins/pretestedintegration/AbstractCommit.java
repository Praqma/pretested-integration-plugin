package org.jenkinsci.plugins.pretestedintegration;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Hudson;

public abstract class AbstractCommit implements ExtensionPoint {
	public static ExtensionList<AbstractCommit> all() {
		return Hudson.getInstance().getExtensionList(AbstractCommit.class);
	}
}
