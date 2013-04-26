package org.jenkinsci.plugins.pretestcommit;

import hudson.model.TaskListener;

/**
 * Collection of various static functions for use in the Pretest Commit plugin.
 */
public class PretestUtils {
	
	private static final String LOG_PREFIX = "[prteco] ";
	
	/**
	 * 
	 */
	public static void logMessage(TaskListener listener, String message) {
		listener.getLogger().println(LOG_PREFIX + message);
	}
	
	public static void logError(TaskListener listener, String message) {
		listener.error(LOG_PREFIX + message);
	}
}