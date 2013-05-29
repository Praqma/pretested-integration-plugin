package org.jenkinsci.plugins.pretestedintegration.scminterface;

/**
 * The class is used in the Pretested Commit Plugin to uniquely represent a
 * specific commit.
 */
public class PretestedIntegrationSCMCommit {
	
	private String id;
	
	/**
	 * New commit with a given id.
	 * 
	 * @param id The id of the commit.
	 */
	public PretestedIntegrationSCMCommit(String id) {
		this.id = id;
	}
	
	/**
	 * A unique identifier for the commit.
	 * 
	 * @return The identifier.
	 */
	public String getId() {
		return id;
	}
}
