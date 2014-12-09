/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.jenkinsci.plugins.pretestedintegration.scm.git;

/**
 * Hold common git error or user messages, used several times
 * in different classes and reusable for testing later.
 * @author bue
 */
public class GitMessages {
    
    public static final String text1 = "";
    
    /**
     * Message for merge strategies to show when they don't find a match
     * between remote branches and relevant SCM change.
     * @param branchName
     * @return
     */
    public static String NoRelevantSCMchange(String branchName) {
        return String.format("There is no relevant SCM change to integrate where branch matches the 'Integration repository'. Either branch (%s) is deleted or already integrated, or the SCM change is not related to the integration repository.", branchName);
    }
    
}
