package org.jenkinsci.plugins.pretestedintegration;

import hudson.model.TaskListener;

 /**
  *	Collection of static utility function for use in the pretest plugin.
  */ 
public class PretestUtils {
	
	private static final String LOG_PREFIX = "[PREINT] ";
	private static final String LOG_DEBUG_PREFIX = LOG_PREFIX + "[DEBUG] ";
	
	private static final String DEBUG_PROPERTY = "preintDebug";
	
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
	 * If debug is defined to true (e.g. compiled with -Ddebug), writes the
	 * given log message to the Jenkins log and pre fixes it with a plugin
	 * identifier message.
	 *
	 * @param listener
	 * @param message
	 */
	public static void logDebug(TaskListener listener, String message) {
		if(System.getProperty(DEBUG_PROPERTY, "false").equals("true")) {
			listener.getLogger().println(LOG_DEBUG_PREFIX + message);
		}
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
