package org.jenkinsci.plugins.pretestedintegration;

import hudson.model.AbstractBuild;
import hudson.model.TaskListener;
import hudson.model.BuildListener;
import hudson.Launcher;
import hudson.AbortException;
import hudson.model.AbstractProject;

import org.jenkinsci.plugins.pretestedintegration.scminterface
		.PretestedIntegrationSCMInterface;
import org.jenkinsci.plugins.pretestedintegration.scminterface
		.AvailableInterfaces;

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
	
	
	public static String getScmType(AbstractBuild build, Launcher launcher,
			BuildListener listener) throws AbortException {
		AbstractProject<?,?> project = build.getProject();
		return project.getScm().getType();
	}
	
	/**
	 * Get the interface for the SCM according to the chosen SCM.
	 */
	public static PretestedIntegrationSCMInterface getScmInterface(
			AbstractBuild build, Launcher launcher, BuildListener listener) {
		String scmType;
		try{
			scmType = getScmType(build, launcher, listener);
		} catch(AbortException e) {
			PretestUtils.logMessage(listener, "No SCM chosen");
			return null;
		}
		if(scmType == null) {
			PretestUtils.logMessage(listener, "No SCM chosen");
			return null;
		}
		Class scmClass = AvailableInterfaces.getClassByName(scmType);
		if(scmClass == null) {
			PretestUtils.logMessage(
					listener, "No interface found for SCM type: " + scmType);
			return null;
		}
		PretestedIntegrationSCMInterface scmInterface;
		try {
			scmInterface = (PretestedIntegrationSCMInterface)
					scmClass.newInstance();
		} catch(InstantiationException e) {
			PretestUtils.logMessage(listener, "Could not instantiate class: "
					+ AvailableInterfaces.getClassByName(scmType));
			return null;
		} catch(IllegalAccessException e) {
			PretestUtils.logMessage(listener, "Could not instantiate class: "
					+ AvailableInterfaces.getClassByName(scmType));
			return null;
		} catch(ClassCastException e) {
			PretestUtils.logMessage(listener, "SCM interface class does not"
					+ " implement the PretestedIntegrationSCMInterface: "
					+ AvailableInterfaces.getClassByName(scmType));
			return null;
		}
		return scmInterface;
	}
}
