package org.jenkinsci.plugins.pretestedintegration;

import hudson.model.TaskListener;

 /**
  *	Collection of static utility function for use in the pretest plugin.
  */ 
public class PretestUtils {
	
	private static final String LOG_PREFIX = "[PREINT] ";
	
	/**
	 * Writes the given log message to the Jenkins log and pre fixes it with a
	 * plugin identifier message.
	 *
	 * @param listener
	 * @param message
	 */
	public static void logMessage(TaskListener listener, String message) {
		listener.getLogger().println(LOG_PREFIX + message);
	}
	
	/**
	 * Writes the given error log message to the Jenkins log and pre fixes it
	 * with a plugin identifier message.
	 *
	 * @param listener
	 * @param message
	 */
	public static void logError(TaskListener listener, String message) {
		listener.error(LOG_PREFIX + message);
	}
}
