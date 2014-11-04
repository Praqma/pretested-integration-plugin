/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.jenkinsci.plugins.pretestedintegration;

import hudson.model.Descriptor;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

/**
 *
 * @author Mads
 * @param <T>
 */
public abstract class IntegrationStrategyDescriptor<T extends IntegrationStrategy> extends Descriptor<IntegrationStrategy> {

    private final static Logger logger = Logger
			.getLogger(IntegrationStrategyDescriptor.class.getName());// Generated code DONT TOUCH! Bookmark: 3ca61d8e671737b5ead8aaccd31875c4

	public abstract boolean isApplicable(Class<? extends AbstractSCMBridge> bridge);
    
    @Override
    public IntegrationStrategy newInstance(StaplerRequest req, JSONObject formData) throws FormException {
        logger.exiting("IntegrationStrategyDescriptor", "newInstance");// Generated code DONT TOUCH! Bookmark: 6b434b0bc4a7b4e502f04f88ec57767c
		logger.entering("IntegrationStrategyDescriptor", "newInstance",
				new Object[] { req, formData });// Generated code DONT TOUCH! Bookmark: 061afc28f8b9c2331a989a904c3fb6f6
		return req.bindJSON(IntegrationStrategy.class, formData);
    }
    
}
