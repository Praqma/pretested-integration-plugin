package org.jenkinsci.plugins.pretestcommit;

import hudson.model.TaskListener;

public class PretestUtils {
	
	private static final String LOG_PREFIX = "[prteco] ";
	
	public static void logMessage(TaskListener listener, String message) {
		listener.getLogger().println(LOG_PREFIX + message);
	}
	
	public static void logError(TaskListener listener, String message) {
		listener.error(LOG_PREFIX + message);
	}
}