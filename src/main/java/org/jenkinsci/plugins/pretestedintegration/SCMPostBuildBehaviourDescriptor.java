/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.jenkinsci.plugins.pretestedintegration;

import hudson.model.Descriptor;
import java.util.ArrayList;
import java.util.List;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

/**
 *
 * @author Mads
 * @param <T>
 */
public abstract class SCMPostBuildBehaviourDescriptor<T extends SCMPostBuildBehaviour> extends Descriptor<SCMPostBuildBehaviour> {

    @Override
    public SCMPostBuildBehaviour newInstance(StaplerRequest req, JSONObject formData) throws FormException {
        return req.bindJSON(SCMPostBuildBehaviour.class, formData);
    }
    
}
