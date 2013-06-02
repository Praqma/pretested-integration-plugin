package org.jenkinsci.plugins.pretestedintegration.scminterface;

import java.util.Dictionary;
import java.util.Hashtable;

public class AvailableInterfaces {
	
	private Dictionary<String, Class> classesByName;
	
	private static AvailableInterfaces instance = null;
	
	/**
	 * Edit here!
	 */
	private void setAvailableInterfaces() {
		// classesByName.put("hudson.plugins.mercurial.MercurialSCM", );
	}
	
	private AvailableInterfaces() {
		classesByName = new Hashtable();
		setAvailableInterfaces();
	}
	
	private static AvailableInterfaces getInstance() {
		if(instance == null) {
			instance = new AvailableInterfaces();
		}
		return instance;
	}
	
	public static Class getClassByName(String name) {
		return getInstance().classesByName.get(name);
	}
	
}