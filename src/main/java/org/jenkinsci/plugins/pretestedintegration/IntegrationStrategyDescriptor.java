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

    public abstract boolean isApplicable(Class<? extends AbstractSCMBridge> bridge);
    
    @Override
    public IntegrationStrategy newInstance(StaplerRequest req, JSONObject formData) throws FormException {
        return req.bindJSON(IntegrationStrategy.class, formData);
    }
    
}
